// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.core.functions

import com.adevinta.oss.zoe.core.functions.DeploySchemaResponse.ActualRunResponse
import com.adevinta.oss.zoe.core.functions.DeploySchemaResponse.DryRunResponse
import com.adevinta.oss.zoe.core.utils.*
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import org.apache.avro.Schema
import org.apache.avro.compiler.idl.Idl
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.Config
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.errors.TopicAuthorizationException
import org.apache.kafka.clients.admin.OffsetSpec as KafkaOffsetSpec
import org.apache.kafka.common.TopicPartition as KafkaTopicPartition

/**
 * Lambda function to list the kafka topics
 */
val listTopics = zoeFunction<AdminConfig, ListTopicsResponse>(name = "topics") { config ->
    admin(config.props).use { cli ->
        cli.listTopics().listings().get()
            .map { it.name() }
            .let { topics ->
                val describeTopicsFuture =
                    cli
                        .describeTopics(topics)
                        .all()

                val configResources: Map<ConfigResource, Config> =
                    cli
                        .describeConfigs(topics.map { ConfigResource(ConfigResource.Type.TOPIC, it) })
                        .all()
                        .runCatching { get() }
                        .onFailure {
                            when (it) {
                                is TopicAuthorizationException -> logger.warn("not authorized to describe the config!")
                                else -> logger.warn("unexpected error on config describe request", it)
                            }
                        }
                        .getOrElse { emptyMap() }

                describeTopicsFuture.get().entries.map { (_, topic) ->
                    val configMap = configResources[ConfigResource(ConfigResource.Type.TOPIC, topic.name())]
                        ?.entries()
                        ?.filter { it.value().isNotEmpty() }
                        ?.map { it.name() to it.value() }
                        ?.toMap()?.toSortedMap() ?: sortedMapOf()

                    TopicDescription(
                        topic = topic.name(),
                        internal = topic.isInternal,
                        partitions = topic.partitions().map { it.partition() },
                        config = configMap
                    )
                }
            }
            .let { ListTopicsResponse(it) }
    }
}

/**
 * Create a kafka topic
 */
val createTopic = zoeFunction<CreateTopicRequest, CreateTopicResponse>(name = "createTopic") { config ->
    admin(config.props).use { cli ->
        cli
            .createTopics(
                listOf(
                    NewTopic(config.name, config.partitions, config.replicationFactor.toShort())
                        .apply { config.topicConfig?.takeIf { it.isNotEmpty() }?.let(this::configs) }
                )
            )
            .all()
            .get()

        CreateTopicResponse(done = true)
    }
}


/**
 * Get offsets from a specific timestamp
 */
val queryOffsets = zoeFunction<OffsetQueriesRequest, OffsetQueriesResponse>(name = "queryOffsets") { config ->
    consumer(config.props).use { cons ->
        val partitions = cons.partitionsFor(config.topic).map { KafkaTopicPartition(it.topic(), it.partition()) }
        val responses = config.queries.map { query ->
            val response = when (query) {
                is OffsetQuery.Timestamp ->
                    cons
                        .offsetsForTimes(
                            partitions
                                .asSequence()
                                .map { it to query.ts }
                                .toMap()
                        )
                        .map {
                            TopicPartitionOffset(
                                it.key.topic(),
                                it.key.partition(),
                                it.value?.offset()
                            )
                        }

                is OffsetQuery.End ->
                    cons
                        .endOffsets(partitions)
                        .map {
                            TopicPartitionOffset(
                                it.key.topic(),
                                it.key.partition(),
                                it.value
                            )
                        }

                is OffsetQuery.Beginning ->
                    cons
                        .beginningOffsets(partitions)
                        .map {
                            TopicPartitionOffset(
                                it.key.topic(),
                                it.key.partition(),
                                it.value
                            )
                        }
            }

            query.id to response
        }

        OffsetQueriesResponse(responses = responses.toMap())
    }
}

/**
 * Get offsets from a specific timestamp
 */
val setOffsets = zoeFunction<SetOffsetRequest, SetOffsetResponse>(name = "setOffsets") { request ->
    admin(request.props).use { adm ->
        val offsets = request.offsets.entries.associate { (partition, offset) ->
            KafkaTopicPartition(request.topic, partition) to OffsetAndMetadata(offset)
        }

        adm.alterConsumerGroupOffsets(request.groupId, offsets).all().get()
        SetOffsetResponse(succeeded = true)
    }
}

/**
 * Lambda function to list schemas
 */
val listSchemas = zoeFunction<ListSchemasConfig, ListSchemasResponse>(name = "listSchemas") { config ->
    val registry = CachedSchemaRegistryClient(config.registry, 10)
    val regex = config.regexFilter?.toRegex()
    val limit = config.limit
    val subjects =
        registry
            .allSubjects
            .let { if (regex != null) it.filter { subject -> subject matches regex } else it }
            .let { if (limit != null) it.take(limit) else it }
            .toList()
    ListSchemasResponse(subjects)
}

/**
 * Lambda function to describe a schema
 */
val describeSchema = zoeFunction<DescribeSchemaConfig, DescribeSchemaResponse>(name = "describeSchema") { config ->
    val registry = CachedSchemaRegistryClient(config.registry, 10)

    DescribeSchemaResponse(
        subject = config.subject,
        versions = registry.getAllVersions(config.subject),
        latest = registry.getLatestSchemaMetadata(config.subject).schema
    )
}


/**
 * Deploys an avro schema
 */
val deploySchema = zoeFunction<DeploySchemaConfig, DeploySchemaResponse>(name = "deploy-schema") { config ->
    val registry = CachedSchemaRegistryClient(config.registry, 10)
    val strategy = config.strategy
    val schema = config.schema.parsed()
    val subject = strategy.subjectName(schema.rawSchema())

    when {
        config.dryRun ->
            DryRunResponse(
                subject = subject,
                schema = schema.rawSchema().toString(false),
                registry = config.registry
            )
        else -> {
            val id = registry.register(subject, schema)
            ActualRunResponse(id = id, subject = subject)
        }
    }
}

/**
 * Lambda function that gives information about consumer groups
 */
val listGroups = zoeFunction<GroupsConfig, ListGroupsResponse>(name = "groups") { config ->
    admin(config.props).use { cli ->
        val groupIds = config.groups.ifEmpty { cli.listConsumerGroups().all().get().map { it.groupId() } }
        val groups = cli.describeConsumerGroups(groupIds).all().get().values.map { group ->
            GroupDescription(
                partitionsAssignor = group.partitionAssignor(),
                coordinator = with(group.coordinator()) {
                    json.createObjectNode()
                        .put("host", host())
                        .put("id", id())
                        .put("idString", idString())
                        .put("hasRack", hasRack())
                        .put("rack", rack())
                        .put("isEmpty", isEmpty)
                },
                simpleConsumerGroup = group.isSimpleConsumerGroup,
                groupId = group.groupId(),
                state = json.createObjectNode().put("name", group.state().name).put(
                    "ordinal",
                    group.state().ordinal
                ),
                members = group.members().map {
                    GroupMember(
                        clientId = it.clientId(),
                        consumerId = it.consumerId(),
                        host = it.host(),
                        assignment = it.assignment().topicPartitions().asSequence().map { assignment ->
                            GroupAssignment(
                                assignment.topic(),
                                assignment.partition()
                            )
                        }.toList()
                    )
                }
            )
        }

        ListGroupsResponse(groups)
    }
}

/**
 * Lambda function that gives the offsets of a consumer group
 */
val offsets = zoeFunction<GroupConfig, GroupOffsetsResponse>(name = "offsets") { config ->
    admin(config.props).use { cli ->
        val currentOffsets =
            cli
                .listConsumerGroupOffsets(config.group)
                .partitionsToOffsetAndMetadata().get()

        val endOffsets = consumer(config.props).use { it.endOffsets(currentOffsets.keys) }

        GroupOffsetsResponse(
            currentOffsets.map { (tp, offset) ->
                GroupOffset(
                    topic = tp.topic(),
                    partition = tp.partition(),
                    currentOffset = offset.offset(),
                    endOffset = endOffsets[tp],
                    lag = endOffsets[tp]?.let { it - offset.offset() },
                    metadata = offset.metadata()
                )
            }
        )
    }
}

data class GroupsConfig(
    val props: Map<String, String?> = mapOf(),
    val groups: List<String>
)

data class GroupConfig(
    val props: Map<String, String?> = mapOf(),
    val group: String
)

data class GroupDescription(
    val partitionsAssignor: String,
    val coordinator: JsonNode,
    val simpleConsumerGroup: Boolean,
    val groupId: String,
    val state: JsonNode,
    val members: List<GroupMember>
)

data class ListGroupsResponse(
    val groups: List<GroupDescription>
)

data class GroupMember(
    val clientId: String,
    val consumerId: String,
    val host: String,
    val assignment: List<GroupAssignment>
)

data class GroupAssignment(
    val topic: String,
    val partition: Int
)

data class GroupOffsetsResponse(
    val offsets: List<GroupOffset>
)

data class GroupOffset(
    val topic: String,
    val partition: Int,
    val currentOffset: Long,
    val endOffset: Long?,
    val lag: Long?,
    val metadata: String
)

data class AdminConfig(
    val props: Map<String, String?> = mapOf()
)

data class DeploySchemaConfig(
    val registry: String,
    val schema: SchemaContent,
    val strategy: SubjectNameStrategy,
    val dryRun: Boolean
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ActualRunResponse::class, name = "actual"),
    JsonSubTypes.Type(value = DryRunResponse::class, name = "dry")
)
sealed class DeploySchemaResponse {
    data class ActualRunResponse(val id: Int, val subject: String) : DeploySchemaResponse()
    data class DryRunResponse(val subject: String, val schema: String, val registry: String) :
        DeploySchemaResponse()
}

data class DescribeSchemaConfig(
    val registry: String,
    val subject: String
)

data class DescribeSchemaResponse(
    val subject: String,
    val versions: List<Int>,
    val latest: String
)

data class ListSchemasConfig(
    val registry: String,
    val regexFilter: String?,
    val limit: Int?
)

data class ListSchemasResponse(
    val subjects: List<String>
)

data class TopicDescription(
    val topic: String,
    val internal: Boolean,
    val partitions: List<Int>,
    val config: Map<String, String>
)

data class ListTopicsResponse(
    val topics: List<TopicDescription>
)

data class OffsetQueriesRequest(
    val props: Map<String, String?>,
    val topic: String,
    val queries: List<OffsetQuery>
)

data class SetOffsetRequest(
    val props: Map<String, String?>,
    val topic: String,
    val groupId: String,
    val offsets: Map<Int, Long>
)

data class SetOffsetResponse(val succeeded: Boolean)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = OffsetQuery.Timestamp::class, name = "timestamp"),
    JsonSubTypes.Type(value = OffsetQuery.End::class, name = "end"),
    JsonSubTypes.Type(value = OffsetQuery.Beginning::class, name = "beginning")
)
sealed class OffsetQuery {
    abstract val id: String

    data class Timestamp(val ts: Long, override val id: String = uuid()) : OffsetQuery()
    data class End(override val id: String = uuid()) : OffsetQuery()
    data class Beginning(override val id: String = uuid()) : OffsetQuery()
}

data class OffsetQueriesResponse(
    val responses: Map<String, List<TopicPartitionOffset>>
)

data class TopicPartitionOffset(
    val topic: String,
    val partition: Int,
    val offset: Long?
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = SchemaContent.AvscSchema::class, name = "avsc"),
    JsonSubTypes.Type(value = SchemaContent.AvdlSchema::class, name = "avdl")
)
sealed class SchemaContent {
    data class AvscSchema(val content: String) : SchemaContent()
    data class AvdlSchema(val content: String, val name: String) : SchemaContent()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = SubjectNameStrategy.RecordNameStrategy::class, name = "RecordNameStrategy"),
    JsonSubTypes.Type(value = SubjectNameStrategy.TopicNameStrategy::class, name = "TopicNameStrategy"),
    JsonSubTypes.Type(value = SubjectNameStrategy.TopicRecordNameStrategy::class, name = "TopicRecordNameStrategy")
)
sealed class SubjectNameStrategy {
    object RecordNameStrategy : SubjectNameStrategy()
    data class TopicNameStrategy(val topic: String, val suffix: TopicNameStrategySuffix) : SubjectNameStrategy()
    data class TopicRecordNameStrategy(val topic: String) : SubjectNameStrategy()
}

enum class TopicNameStrategySuffix(@JsonValue val code: String) {
    Key("key"), Value("value")
}

fun SubjectNameStrategy.subjectName(schema: Schema): String = when (this) {
    is SubjectNameStrategy.TopicNameStrategy -> {
        "$topic-${suffix.code}"
    }
    is SubjectNameStrategy.RecordNameStrategy -> "${schema.namespace}.${schema.name}"
    is SubjectNameStrategy.TopicRecordNameStrategy -> {
        val fqdn = "${schema.namespace}.${schema.name}"
        "$topic-$fqdn"
    }
}

private fun SchemaContent.parsed(): AvroSchema = when (this) {
    is SchemaContent.AvscSchema -> AvroSchema(Schema.Parser().parse(content))
    is SchemaContent.AvdlSchema -> {
        val protocol = Idl(content.byteInputStream(Charsets.UTF_8)).CompilationUnit()
        val fqdn = "${protocol.namespace}.$name"
        AvroSchema(protocol.getType(fqdn) ?: throw IllegalArgumentException("schema '$name' not found in avdl"))
    }
}

private fun AdminClient.listOffsetsAndMetadata(topic: String, partitions: List<Int>, spec: KafkaOffsetSpec) =
    listOffsets(partitions.associate { KafkaTopicPartition(topic, it) to spec })
        .all()
        .get()
        .mapValues { OffsetAndMetadata(it.value.offset()) }

data class CreateTopicRequest(
    val name: String,
    val partitions: Int,
    val replicationFactor: Int,
    val topicConfig: Map<String, String>?,
    val props: Map<String, String?>
)

data class CreateTopicResponse(val done: Boolean)
