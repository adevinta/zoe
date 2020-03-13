// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.core.functions

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import com.adevinta.oss.zoe.core.functions.DeploySchemaResponse.ActualRunResponse
import com.adevinta.oss.zoe.core.functions.DeploySchemaResponse.DryRunResponse
import com.adevinta.oss.zoe.core.utils.admin
import com.adevinta.oss.zoe.core.utils.consumer
import com.adevinta.oss.zoe.core.utils.json
import org.apache.avro.Schema
import org.apache.avro.compiler.idl.Idl

/**
 * Lambda function to list the kafka topics
 */
val listTopics = zoeFunction<AdminConfig, ListTopicsResponse>(name = "topics") { config ->
    admin(config.props).use { cli ->
        cli.listTopics().listings().get()
            .map { it.name() }
            .let(cli::describeTopics).all().get()
            .map { (_, topic) ->
                TopicDescription(
                    topic.name(),
                    topic.isInternal,
                    topic.partitions().map { it.partition() })
            }
            .let { ListTopicsResponse(it) }
    }
}

/**
 * Lambda function to list schemas
 */
val listSchemas = zoeFunction<ListSchemasConfig, ListSchemasResponse>(name = "schemas") { config ->
    val registry = CachedSchemaRegistryClient(config.registry, 10)

    val subjects = registry.allSubjects.map {
        SchemaDescription(
            subject = it,
            versions = registry.getAllVersions(it),
            latest = registry.getLatestSchemaMetadata(it).schema
        )
    }

    ListSchemasResponse(subjects)
}

/**
 * Deploys an avro schema
 */
val deploySchema = zoeFunction<DeploySchemaConfig, DeploySchemaResponse>(name = "deploy-schema") { config ->
    val registry = CachedSchemaRegistryClient(config.registry, 10)
    val strategy = config.strategy
    val schema = config.schema.parsed()
    val subject = strategy.subjectName(schema)

    when {
        config.dryRun ->
            DryRunResponse(subject = subject, schema = schema.toString(false), registry = config.registry)
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
    val props: Map<String, String> = mapOf(),
    val groups: List<String>
)

data class GroupConfig(
    val props: Map<String, String> = mapOf(),
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
    val props: Map<String, String> = mapOf()
)

data class ListSchemasConfig(
    val registry: String
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

data class SchemaDescription(
    val subject: String,
    val versions: List<Int>,
    val latest: String
)

data class ListSchemasResponse(
    val subjects: List<SchemaDescription>
)

data class TopicDescription(
    val topic: String,
    val internal: Boolean,
    val partitions: List<Int>
)

data class ListTopicsResponse(
    val topics: List<TopicDescription>
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

fun SchemaContent.parsed(): Schema = when (this) {
    is SchemaContent.AvscSchema -> Schema.Parser().parse(content)
    is SchemaContent.AvdlSchema -> {
        val protocol = Idl(content.byteInputStream(Charsets.UTF_8)).CompilationUnit()
        val fqdn = "${protocol.namespace}.$name"
        protocol.getType(fqdn) ?: throw IllegalArgumentException("schema '$name' not found in avdl")
    }
}
