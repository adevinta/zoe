package com.adevinta.oss.zoe.service.simulator

import com.adevinta.oss.zoe.core.functions.*
import com.adevinta.oss.zoe.core.utils.parseJson
import com.adevinta.oss.zoe.core.utils.toJsonString
import com.adevinta.oss.zoe.service.runners.ZoeRunner
import com.fasterxml.jackson.databind.JsonNode
import java.util.concurrent.CompletableFuture
import kotlin.math.min

class ZoeRunnerSimulator(val state: RunnerState) : ZoeRunner {

    override val name: String = "simulator"

    override fun launch(function: String, payload: String): CompletableFuture<String> =
        CompletableFuture.completedFuture(handle(function, payload).toJsonString())

    private fun handle(function: String, payload: String): Any = when (function) {
        "topics" -> handleTopicList(payload.parseJson())
        "poll" -> handlePoll(payload.parseJson())
        "queryOffsets" -> handleQueryOffsets(payload.parseJson())
        else -> "unhandled function: $function"
    }

    private fun handleTopicList(payload: AdminConfig): ListTopicsResponse {
        val cluster = state.clusterFromProps(payload.props)
            ?: throw IllegalArgumentException("cluster not found: $payload")

        return ListTopicsResponse(
            topics = cluster.topics.map {
                TopicDescription(
                    topic = it.name,
                    internal = false,
                    partitions = it.partitions.toList()
                )
            }
        )
    }

    private fun handlePoll(payload: PollConfig): PollResponse {
        val cluster = state.clusterFromProps(payload.props)
            ?: throw IllegalArgumentException("cluster not found: $payload")

        val topic = cluster.topics.find { it.name == payload.topic }
            ?: throw IllegalArgumentException("topic not found: $payload")

        val records: List<Message> = kotlin.run {

            val candidates = when (val subscription = payload.subscription) {

                is Subscription.AssignPartitions ->
                    topic
                        .messagesForPartitions(subscription.partitions.keys)
                        .filter { it.offset >= subscription.partitions.getValue(it.partition) }

                is Subscription.WithGroupId ->
                    throw IllegalArgumentException("subscription not supported: $subscription")
            }

            candidates.take(min(payload.numberOfRecords, state.readSpeedPerPoll))
        }

        return PollResponse(
            records = records
                .asSequence()
                .map {
                    PolledRecord(
                        key = it.key,
                        offset = it.offset,
                        timestamp = it.timestamp,
                        partition = it.partition,
                        topic = topic.name,
                        formatted = it.content
                    )
                }
                .toList(),

            progress = records
                .groupBy { it.partition }
                .map { (partition, partitionRecords) ->
                    PartitionProgress(
                        topic = topic.name,
                        partition = partition,
                        earliestOffset = topic.earliestOffset(partition)!!,
                        latestOffset = topic.latestOffset(partition)!!,
                        progress = Progress(
                            currentOffset = partitionRecords.maxBy { it.offset }?.offset!!,
                            currentTimestamp = partitionRecords.maxBy { it.offset }?.timestamp!!,
                            startOffset = partitionRecords.minBy { it.offset }?.offset!!,
                            recordsCount = partitionRecords.size.toLong()
                        )
                    )
                }
        )
    }

    private fun handleQueryOffsets(payload: OffsetQueriesRequest): OffsetQueriesResponse {
        val cluster = state.clusterFromProps(payload.props)
            ?: throw IllegalArgumentException("cluster not found: $payload")

        val topic = cluster.topics.find { it.name == payload.topic }
            ?: throw IllegalArgumentException("topic not found: $payload")

        val responses = payload.queries.map { query ->
            val response = when (query) {
                is OffsetQuery.Timestamp -> topic.partitions.map { partition ->
                    TopicPartitionOffset(
                        topic = topic.name,
                        partition = partition,
                        offset = topic.messagesForPartitions(setOf(partition)).find { it.timestamp >= query.ts }?.offset
                    )
                }
                is OffsetQuery.End -> topic.partitions.map { partition ->
                    TopicPartitionOffset(
                        topic = topic.name,
                        partition = partition,
                        offset = topic.latestOffset(partition)
                    )
                }
                is OffsetQuery.Beginning -> topic.partitions.map { partition ->
                    TopicPartitionOffset(
                        topic = topic.name,
                        partition = partition,
                        offset = topic.earliestOffset(partition)
                    )
                }
            }

            query.id to response
        }

        return OffsetQueriesResponse(
            responses = responses.toMap()
        )
    }

    override fun close() {}

}

data class RunnerState(
    val readSpeedPerPoll: Int,
    val clusters: List<Cluster>
)

data class Cluster(
    val name: String,
    val topics: List<Topic>
)

data class Topic(
    val name: String,
    val partitions: Set<Int>,
    val messages: Map<Int, List<Message>>
)

data class Message(
    val offset: Long,
    val timestamp: Long,
    val partition: Int,
    val key: String,
    val content: JsonNode
)

fun RunnerState.clusterFromProps(props: Map<String, String>) =
    clusters.find { it.name == props["bootstrap.servers"] }

fun Topic.messagesForPartitions(requestedPartitions: Set<Int>): List<Message> =
    requestedPartitions
        .map { partition ->
            if (partition !in partitions) {
                throw IllegalArgumentException("requested partition '$partition' not found in: $this")
            }
            messages[partition] ?: emptyList()
        }
        .flatten()


fun Topic.earliestOffset(partition: Int): Long? = messagesForPartitions(setOf(partition)).map { it.offset }.min()
fun Topic.latestOffset(partition: Int): Long? = messagesForPartitions(setOf(partition)).map { it.offset }.max()
