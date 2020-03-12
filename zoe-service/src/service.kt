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
import com.adevinta.oss.zoe.service.config.*
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
        val (topicName, topicSubject) = clusterConfig.getTopicConfig(topic, subjectOverride = subject)
        val props = clusterConfig.getCompletedProps()

        val dejsonifierConfig = dejsonifier ?: when {
            topicSubject != null -> DejsonifierConfig.Avro(
                registry = clusterConfig.registry ?: userError("'registry' must be set when using a topic subject"),
                subject = topicSubject
            )
            else -> DejsonifierConfig.Raw
        }

        return runner.produce(
            ProduceConfig(
                topic = topicName,
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
        query: String?,
        parallelism: Int,
        numberOfRecordsPerBatch: Int,
        timeoutPerBatch: Long,
        formatter: String,
        stopCondition: StopCondition
    ): Flow<RecordOrProgress> = flow {

        val clusterConfig = getCluster(cluster)
        val topicName = clusterConfig.getTopicConfig(topic, subjectOverride = null).name
        val completedProps = clusterConfig.getCompletedProps()
        val resolvedFilters = filters.map { resolveExpression(it) }
        val resolvedQuery = query?.let { resolveExpression(it) }

        val partitionGroups = fetchPartitions(topicName, completedProps).splitIntoGroups(parallelism)

        val recordFlows = partitionGroups.map { group ->
            readUntilEnd(
                completedProps,
                topicName,
                resolvedFilters,
                resolvedQuery,
                from,
                group,
                formatter = formatter,
                recordsPerBatch = numberOfRecordsPerBatch,
                timeoutPerBatch = timeoutPerBatch,
                stopCondition = stopCondition
            )
        }

        emitAll(flowOf(*recordFlows.toTypedArray()).flattenMerge(concurrency = 100))
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


    private suspend fun resolveExpression(expression: String): String =
        if (CalledExpression.isCandidate(expression)) {
            val called = CalledExpression.parse(expression)
            val registered =
                configStore.expression(called.name).await()
                    ?: userError("expression not registered : ${called.name}")
            val result = registered.call(called.args)
            logger.info("resolved expression : $expression -> $result")
            result
        } else {
            expression
        }

    private suspend fun getCluster(name: String): Cluster =
        requireNotNull(configStore.cluster(name).await()) { "cluster config not found : $name" }

    private fun Cluster.getCompletedProps(): Map<String, String> =
        props
            .let(secrets::resolveSecrets)
            .toMutableMap()
            .apply { if (registry != null) putIfAbsent("schema.registry.url", registry) }

    private fun Cluster.getTopicConfig(aliasOrRealName: TopicAliasOrRealName, subjectOverride: String?) =
        when (val retrievedTopic = topics[aliasOrRealName.value]) {
            null -> Topic(aliasOrRealName.value, subjectOverride)
            else -> Topic(retrievedTopic.name, (subjectOverride ?: retrievedTopic.subject))
        }

    private fun Cluster.getConsumerGroup(aliasOrRealName: GroupAliasOrRealName): String =
        groups[aliasOrRealName.value]?.name ?: aliasOrRealName.value

    private fun readUntilEnd(
        props: Map<String, String>,
        topic: String,
        filter: List<String>,
        query: String?,
        from: ConsumeFrom,
        partitions: List<Int>?,
        recordsPerBatch: Int,
        timeoutPerBatch: Long,
        formatter: String,
        stopCondition: StopCondition
    ): Flow<RecordOrProgress> = flow {

        var globalProgress = emptyMap<Int, PartitionProgress>()
        var resumeFrom = emptyMap<Int, ResumeFrom>()

        do {

            val config = PollConfig(
                topic,
                props,
                when {
                    resumeFrom.isNotEmpty() ->
                        Subscription.AssignPartitions(resumeFrom.mapValues { it.value.from }.toMap())

                    else -> subscription(from, partitions)
                },
                filter,
                query,
                timeoutPerBatch,
                numberOfRecords = recordsPerBatch,
                jsonifier = formatter
            )

            val (records, progress) = runner.poll(config)

            resumeFrom = updatedResumePositions(progress, currentResumeFrom = resumeFrom, stopCondition = stopCondition)
            globalProgress = updatedGlobalProgress(progress, currentGlobalProgress = globalProgress)

            for (record in records) {
                emit(RecordOrProgress.Record(record))
            }

            emit(RecordOrProgress.Progress(globalProgress.values))

        } while (resumeFrom.isNotEmpty())
    }

    private fun updatedGlobalProgress(
        progress: Iterable<PartitionProgress>,
        currentGlobalProgress: Map<Int, PartitionProgress>
    ) = currentGlobalProgress.toMutableMap().apply {
        progress.forEach { newPartitionProgress ->
            val (_, partition, _, _, newProgress) = newPartitionProgress
            this[partition] =
                this[partition]
                    ?.let { previousPartitionProgress ->
                        val (_, _, _, _, previousProgress) = previousPartitionProgress
                        previousPartitionProgress.copy(
                            progress = Progress(
                                startOffset = previousProgress.startOffset,
                                currentOffset = newProgress.currentOffset,
                                currentTimestamp = newProgress.currentTimestamp,
                                recordsCount = newProgress.recordsCount + previousProgress.recordsCount
                            )
                        )
                    }
                    ?: newPartitionProgress
        }
    }

    private fun updatedResumePositions(
        progress: Iterable<PartitionProgress>,
        currentResumeFrom: Map<Int, ResumeFrom>,
        stopCondition: StopCondition
    ) = currentResumeFrom.toMutableMap().apply {
        progress.forEach { (_, partition, _, latestOffset, progress) ->

            val currentOffset = progress.currentOffset

            val resumePosition =
                currentResumeFrom[partition]
                    ?.copy(from = currentOffset)
                    ?: ResumeFrom(from = currentOffset, until = latestOffset)

            // save position for next poll
            this[partition] = resumePosition

            // remove completed partitions
            when (stopCondition) {
                StopCondition.TopicEnd -> {
                    if (currentOffset >= resumePosition.until - 1) {
                        remove(partition)
                    }
                }
                StopCondition.Continuously -> {
                }

            }
        }
    }

    private suspend fun fetchPartitions(topic: String, props: Map<String, String>): Iterable<Int> =
        runner
            .topics(AdminConfig(props))
            .let { resp -> resp.topics.find { it.topic == topic }?.partitions }
            ?: throw IllegalArgumentException("Topic not found : $topic")
}

private fun Iterable<Int>.splitIntoGroups(count: Int): Collection<List<Int>> =
    if (count <= 1) listOf(this.toList()) else groupBy { it % (count - 1) }.values

data class ResumeFrom(val from: Long, val until: Long)

sealed class RecordOrProgress {
    data class Record(val record: PolledRecord) : RecordOrProgress()
    data class Progress(val progress: Iterable<PartitionProgress>) : RecordOrProgress()
}

sealed class ConsumeFrom {
    object Earliest : ConsumeFrom()
    object Latest : ConsumeFrom()
    data class Timestamp(val ts: Long) : ConsumeFrom()
    data class OffsetStepBack(val count: Long) : ConsumeFrom()
}

class TopicAliasOrRealName(val value: String)
class GroupAliasOrRealName(val value: String)

sealed class StopCondition {
    object Continuously : StopCondition()
    object TopicEnd : StopCondition()
}

fun subscription(from: ConsumeFrom, partitions: List<Int>?): Subscription = when (from) {
    ConsumeFrom.Earliest -> Subscription.FromBeginning(partitions?.toSet())
    ConsumeFrom.Latest -> Subscription.OffsetStepBack(0, partitions?.toSet())
    is ConsumeFrom.Timestamp -> Subscription.FromTimestamp(from.ts, partitions?.toSet())
    is ConsumeFrom.OffsetStepBack -> Subscription.OffsetStepBack(from.count, partitions?.toSet())
}

data class RunnerVersionData(
    val clientVersion: String,
    val remoteVersion: String?,
    val mismatch: Boolean,
    val timestamp: Long
)
