// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service

import com.adevinta.oss.zoe.core.functions.*
import com.adevinta.oss.zoe.core.functions.SubjectNameStrategy.TopicNameStrategy
import com.adevinta.oss.zoe.core.functions.SubjectNameStrategy.TopicRecordNameStrategy
import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.core.utils.now
import com.adevinta.oss.zoe.service.DejsonifierNotInferrable.Reason
import com.adevinta.oss.zoe.service.config.Cluster
import com.adevinta.oss.zoe.service.config.ConfigStore
import com.adevinta.oss.zoe.service.config.Topic
import com.adevinta.oss.zoe.service.expressions.CalledExpression
import com.adevinta.oss.zoe.service.expressions.call
import com.adevinta.oss.zoe.service.runners.*
import com.adevinta.oss.zoe.service.secrets.SecretsProvider
import com.adevinta.oss.zoe.service.secrets.resolveSecrets
import com.adevinta.oss.zoe.service.storage.KeyValueStore
import com.adevinta.oss.zoe.service.storage.getAsJson
import com.adevinta.oss.zoe.service.storage.putAsJson
import com.adevinta.oss.zoe.service.storage.withNamespace
import com.adevinta.oss.zoe.service.utils.userError
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import java.time.Duration

@ExperimentalCoroutinesApi
@FlowPreview
class ZoeService(
    private val configStore: ConfigStore,
    private val runner: ZoeRunner,
    private val storage: KeyValueStore,
    private val secrets: SecretsProvider
) {

    private val internalTopics = setOf("__confluent.support.metrics")

    /**
     * Produce a list of messages to the topic
     */
    suspend fun produce(
        cluster: String,
        topic: TopicAliasOrRealName,
        subject: String?,
        messages: List<JsonNode>,
        keyPath: String?,
        valuePath: String?,
        timestampPath: String?,
        dejsonifier: DejsonifierConfig?,
        dryRun: Boolean
    ): ProduceResponse {
        val clusterConfig = getCluster(cluster)
        val topicConfig = clusterConfig.getTopicConfig(topic, subjectOverride = subject)
        val props = clusterConfig.getCompletedProps()

        val dejsonifierConfig = dejsonifier ?: clusterConfig.inferDejsonifierConfig(topicConfig)

        return runner.produce(
            ProduceConfig(
                topic = topicConfig.name,
                dejsonifier = dejsonifierConfig,
                keyPath = keyPath,
                valuePath = valuePath,
                timestampPath = timestampPath,
                data = messages,
                dryRun = dryRun,
                props = props
            )
        )
    }

    /**
     * Reads a topic from a specific point in time with optional filtering on messages
     */
    fun read(
        cluster: String,
        topic: TopicAliasOrRealName,
        from: ConsumeFrom,
        filters: List<String>,
        metadataFilters: List<String>,
        query: String?,
        parallelism: Int,
        numberOfRecordsPerBatch: Int,
        timeoutPerBatch: Long,
        formatter: String,
        stopCondition: StopCondition,
        dialect: JsonQueryDialect
    ): Flow<RecordOrProgress> = flow {

        val clusterConfig = getCluster(cluster)
        val topicName = clusterConfig.getTopicConfig(topic, subjectOverride = null).name
        val completedProps = clusterConfig.getCompletedProps()

        val resolvedFilters = filters.map { resolveExpression(it) }
        val resolvedMetaFilters = metadataFilters.map { resolveExpression(it) }
        val resolvedQuery = query?.let { resolveExpression(it) }


        val partitionGroups: Collection<List<ConsumptionRange>> =
            determineConsumptionRange(
                topic = topicName,
                props = completedProps,
                from = from,
                stopCondition = stopCondition
            ).splitIntoGroups(count = parallelism, by = { it.partition })

        val recordFlows = partitionGroups.map { rangeGroup ->
            readRange(
                props = completedProps,
                topic = topicName,
                filter = resolvedFilters,
                filterMeta = resolvedMetaFilters,
                query = resolvedQuery,
                range = rangeGroup,
                recordsPerBatch = numberOfRecordsPerBatch,
                timeoutPerBatch = timeoutPerBatch,
                formatter = formatter,
                dialect = dialect
            )
        }

        emitAll(flowOf(*recordFlows.toTypedArray()).flattenMerge(concurrency = 100))
    }

    private suspend fun determineConsumptionRange(
        topic: String,
        props: Map<String, String>,
        from: ConsumeFrom,
        stopCondition: StopCondition
    ): List<ConsumptionRange> {

        val topicEndQuery = OffsetQuery.End()

        val endQuery = when (stopCondition) {
            StopCondition.Continuously -> null
            StopCondition.TopicEnd -> OffsetQuery.End()
        }

        val startQuery = when (from) {
            is ConsumeFrom.Earliest -> OffsetQuery.Beginning()
            is ConsumeFrom.Latest -> OffsetQuery.End()
            is ConsumeFrom.Timestamp -> OffsetQuery.Timestamp(from.ts)
            is ConsumeFrom.OffsetStepBack -> userError("not yet supported: $from")
        }

        val responses = runner.queryOffsets(
            OffsetQueriesRequest(
                props = props,
                topic = topic,
                queries = listOfNotNull(endQuery, startQuery, topicEndQuery)
            )
        )

        val startOffsets = responses.responses[startQuery.id]
            ?: throw IllegalStateException("query result (query: '$startQuery') not found in response: $responses")

        val endOffsets = endQuery?.let { responses.responses[it.id] }?.map { it.partition to it }?.toMap()

        val topicEndOffsets = responses.responses[topicEndQuery.id]?.map { it.partition to it }?.toMap()
            ?: throw IllegalStateException("query result (query: '$endQuery') not found in response: $responses")

        return startOffsets.mapNotNull { (_: String, partition: Int, startOffset: Long?) ->
            startOffset?.let { nonNullStartOffset ->
                val endOffset = endOffsets?.let {
                    it[partition]
                        ?: topicEndOffsets[partition]
                        ?: throw IllegalStateException("topic end offset (part: $partition) not found in: $responses")
                }

                ConsumptionRange(
                    partition,
                    from = nonNullStartOffset,
                    until = endOffset?.offset,
                    progress = ConsumptionProgress(
                        currentOffset = null,
                        nextOffset = nonNullStartOffset,
                        currentTimestamp = null,
                        numberOfRecords = 0
                    )
                )
            }
        }

    }

    /**
     * List schemas from the registry
     */
    suspend fun listSchemas(cluster: String): ListSchemasResponse {
        val clusterConfig = getCluster(cluster)
        requireNotNull(clusterConfig.registry) { "registry must not be null in config to use the listSchemas function" }
        return runner.schemas(ListSchemasConfig(clusterConfig.registry))
    }

    /**
     * Deploy schema to the registry
     */
    suspend fun deploySchema(
        cluster: String,
        schema: SchemaContent,
        strategy: SubjectNameStrategy,
        dryRun: Boolean
    ): DeploySchemaResponse = with(getCluster(cluster)) {
        runner.deploySchema(
            DeploySchemaConfig(
                registry = registry ?: userError("'registry' must not be null in config to deploy schemas"),
                strategy = when (strategy) {
                    is TopicNameStrategy -> strategy.copy(topic = topics[strategy.topic]?.name ?: strategy.topic)
                    is TopicRecordNameStrategy -> strategy.copy(topic = topics[strategy.topic]?.name ?: strategy.topic)
                    else -> strategy
                },
                schema = schema,
                dryRun = dryRun
            )
        )
    }

    /**
     * List topics in the cluster
     */
    suspend fun listTopics(cluster: String, userTopicsOnly: Boolean): ListTopicsResponse {
        val clusterConfig = getCluster(cluster)
        val response = runner.topics(AdminConfig(clusterConfig.getCompletedProps()))
        return when {
            !userTopicsOnly -> response
            else -> response.copy(topics = response.topics.filter { !it.internal && it.topic !in internalTopics })
        }
    }

    /**
     * Create topic using its alias or real name
     */
    suspend fun createTopic(
        cluster: String,
        topic: TopicAliasOrRealName,
        partitions: Int,
        replicationFactor: Int,
        config: Map<String, String>
    ): CreateTopicResponse {
        val clusterConfig = getCluster(cluster)
        val topicName = clusterConfig.getTopicConfig(topic, subjectOverride = null).name
        return runner.createTopic(
            CreateTopicRequest(
                name = topicName,
                partitions = partitions,
                replicationFactor = replicationFactor,
                props = clusterConfig.getCompletedProps(),
                topicConfig = config
            )
        )
    }


    /**
     * Describe a topic
     */
    suspend fun describeTopic(cluster: String, topic: TopicAliasOrRealName): TopicDescription? {
        val clusterConfig = getCluster(cluster)

        return listTopics(cluster, userTopicsOnly = false).let { response ->
            val topicToSearch: String = clusterConfig.getTopicConfig(topic, subjectOverride = null).name
            response.topics.find { it.topic == topicToSearch }
        }
    }

    /**
     * Offsets for timestamp
     */
    suspend fun offsetsFromTimestamp(
        cluster: String,
        topic: TopicAliasOrRealName,
        timestamp: Long
    ): List<TopicPartitionOffset> {
        val clusterConfig = getCluster(cluster)
        val props = clusterConfig.getCompletedProps()
        val topicName = clusterConfig.getTopicConfig(topic, subjectOverride = null).name

        val query = OffsetQuery.Timestamp(timestamp, id = "query")

        return runner
            .queryOffsets(OffsetQueriesRequest(props, topicName, queries = listOf(query)))
            .responses[query.id]
            ?: throw IllegalStateException("response not found for query '$query'")
    }

    /**
     * List consumer groups
     */
    suspend fun listGroups(cluster: String, groups: List<GroupAliasOrRealName>): ListGroupsResponse {
        val clusterConfig = getCluster(cluster)
        val groupsToSearch = groups.map { clusterConfig.getConsumerGroup(it) }
        return runner.groups(GroupsConfig(clusterConfig.getCompletedProps(), groupsToSearch))
    }

    /**
     * List consumer groups offsets
     */
    suspend fun groupOffsets(cluster: String, group: GroupAliasOrRealName): GroupOffsetsResponse {
        val clusterConfig = getCluster(cluster)
        val groupToSearch = clusterConfig.getConsumerGroup(group)
        return runner.offsets(GroupConfig(clusterConfig.getCompletedProps(), groupToSearch))
    }

    /**
     * Check zoe version with remote. Useful when used against a lambda runner.
     */
    suspend fun checkVersion(useCache: Boolean): RunnerVersionData {
        val versionsCheckStorage = storage.withNamespace("versions")

        if (useCache) {
            val lastCheck = versionsCheckStorage.getAsJson<RunnerVersionData>(runner.name)
            if (lastCheck?.remoteVersion != null && now() - lastCheck.timestamp <= Duration.ofHours(3).toMillis()) {
                return lastCheck
            }
        }

        val versionCheckRequest = VersionCheckRequest(clientVersion = VersionNumber)
        val versionCheckResponse =
            kotlin
                .runCatching { runner.version(versionCheckRequest) }
                .onFailure { error ->
                    logger.error("unable to fetch version from remote...")
                    logger.debug("error when trying to fetch version from remote", error)
                }
                .getOrNull()

        val result = RunnerVersionData(
            clientVersion = versionCheckRequest.clientVersion,
            remoteVersion = versionCheckResponse?.remoteVersion,
            mismatch = versionCheckResponse?.compatible != true,
            timestamp = System.currentTimeMillis()
        )

        versionsCheckStorage.putAsJson(runner.name, result)

        return result
    }

    private suspend fun resolveExpression(expression: String): String {
        if (!CalledExpression.isCandidate(expression)) {
            return expression
        }

        val parsed = CalledExpression.parse(expression)
        val registered = configStore.expression(parsed.name).await()
            ?: userError("expression not registered : ${parsed.name}")

        val result = registered.call(parsed.args)
        logger.info("resolved expression : $expression -> $result")
        return result
    }

    private suspend fun getCluster(name: String): Cluster =
        requireNotNull(configStore.cluster(name).await()) { "cluster config not found : $name" }

    private fun Cluster.getCompletedProps(): Map<String, String> =
        props
            .let(secrets::resolveSecrets)
            .toMutableMap()
            .apply { if (registry != null) putIfAbsent("schema.registry.url", registry) }

    private fun Cluster.getConsumerGroup(aliasOrRealName: GroupAliasOrRealName): String =
        groups[aliasOrRealName.value]?.name ?: aliasOrRealName.value

    private fun readRange(
        props: Map<String, String>,
        topic: String,
        filter: List<String>,
        filterMeta: List<String>,
        query: String?,
        range: List<ConsumptionRange>,
        recordsPerBatch: Int,
        timeoutPerBatch: Long,
        formatter: String,
        dialect: JsonQueryDialect
    ): Flow<RecordOrProgress> = flow {

        var currentRange = range

        while (currentRange.isNotEmpty()) {

            val config = PollConfig(
                topic = topic,
                subscription = Subscription.AssignPartitions(
                    currentRange.map { it.partition to it.progress.nextOffset }.toMap()
                ),
                props = props,
                filter = filter,
                filterMeta = filterMeta,
                query = query,
                timeoutMs = timeoutPerBatch,
                numberOfRecords = recordsPerBatch,
                jsonifier = formatter,
                jsonQueryDialect = dialect
            )

            val (records, progress) = runner.poll(config)

            for (record in records) {
                emit(RecordOrProgress.Record(record))
            }

            currentRange = updateConsumptionRange(currentRange = currentRange, progress = progress)

            emit(RecordOrProgress.Progress(currentRange))

        }

    }

    private fun updateConsumptionRange(
        progress: Iterable<PartitionProgress>,
        currentRange: List<ConsumptionRange>
    ): List<ConsumptionRange> {
        val currentOffsets = progress.map { it.partition to it.progress }.toMap()
        return currentRange
            .map {
                it.copy(
                    progress = currentOffsets[it.partition]
                        ?.let { lastProgress ->
                            ConsumptionProgress(
                                currentOffset = lastProgress.currentOffset,
                                nextOffset = lastProgress.currentOffset + 1,
                                currentTimestamp = lastProgress.currentTimestamp,
                                numberOfRecords = lastProgress.recordsCount + it.progress.numberOfRecords
                            )
                        }
                        ?: it.progress
                )
            }
            .filter { it.until == null || it.progress.nextOffset <= it.until }
    }
}

private fun <T> Iterable<T>.splitIntoGroups(count: Int, by: (T) -> Int): Collection<List<T>> =
    if (count <= 1) listOf(this.toList()) else groupBy { by(it) % count }.values

sealed class RecordOrProgress {
    data class Record(val record: PolledRecord) : RecordOrProgress()
    data class Progress(val range: Iterable<ConsumptionRange>) : RecordOrProgress()
}

sealed class ConsumeFrom {
    object Earliest : ConsumeFrom()
    object Latest : ConsumeFrom()
    data class Timestamp(val ts: Long) : ConsumeFrom()
    data class OffsetStepBack(val count: Long) : ConsumeFrom()
}

data class ConsumptionRange(
    val partition: Int,
    val from: Long,
    val until: Long?,
    val progress: ConsumptionProgress
)

data class ConsumptionProgress(
    val currentOffset: Long?,
    val nextOffset: Long,
    val currentTimestamp: Long?,
    val numberOfRecords: Long
)

class TopicAliasOrRealName(val value: String)
class GroupAliasOrRealName(val value: String)

sealed class StopCondition {
    object Continuously : StopCondition()
    object TopicEnd : StopCondition()
}

data class RunnerVersionData(
    val clientVersion: String,
    val remoteVersion: String?,
    val mismatch: Boolean,
    val timestamp: Long
)

private fun Cluster.getTopicConfig(aliasOrRealName: TopicAliasOrRealName, subjectOverride: String?) =
    when (val retrievedTopic = topics[aliasOrRealName.value]) {
        null -> Topic(aliasOrRealName.value, subjectOverride)
        else -> Topic(retrievedTopic.name, (subjectOverride ?: retrievedTopic.subject))
    }

fun Cluster.inferDejsonifierConfig(topic: Topic): DejsonifierConfig {
    val deserializer = props["value.deserializer"]
        ?: throw DejsonifierNotInferrable(error = "couldn't infer data type", reason = Reason.MissingValueDeserializer)

    return when (deserializer) {
        "io.confluent.kafka.serializers.KafkaAvroDeserializer" -> {
            val msgInCaseOfError =
                "inferred avro data type (because KafkaAvroDeserializer is used) " +
                    "but couldn't build the data converter"

            DejsonifierConfig.Avro(
                registry = registry ?: throw DejsonifierNotInferrable(
                    error = msgInCaseOfError,
                    reason = Reason.MissingRegistry
                ),
                subject = topic.subject ?: throw DejsonifierNotInferrable(
                    error = msgInCaseOfError,
                    reason = Reason.MissingSubjectName
                )
            )
        }
        else -> DejsonifierConfig.Raw
    }
}

class DejsonifierNotInferrable(val error: String, val reason: Reason) : Exception("$error. Reason: ${reason.msg}") {
    enum class Reason(val msg: String) {
        MissingValueDeserializer(
            "missing 'value.deserializer' in the cluster config props (needed to infer data type to produce)"
        ),
        MissingSubjectName("unable to determine topic's subject name to use"),
        MissingRegistry("cannot determine schema registry address")
    }
}