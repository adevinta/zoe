package com.adevinta.oss.zoe.service.simulator

import com.adevinta.oss.zoe.core.functions.*
import com.adevinta.oss.zoe.core.utils.*
import com.adevinta.oss.zoe.service.runners.ZoeRunner
import com.adevinta.oss.zoe.service.simulator.Simulator.clusterFromProps
import com.adevinta.oss.zoe.service.simulator.Simulator.earliestOffset
import com.adevinta.oss.zoe.service.simulator.Simulator.latestOffset
import com.adevinta.oss.zoe.service.simulator.Simulator.messagesForPartitions
import com.fasterxml.jackson.databind.JsonNode
import java.util.concurrent.CompletableFuture
import kotlin.math.min
import kotlin.random.Random

private fun <T, R : Comparable<R>> Iterable<T>.isSortedAscendingBy(by: (T) -> R) =
    map(by).zipWithNext().all { (previous, next) -> previous <= next }

object Simulator {

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

    @DslMarker
    annotation class RunnerBuilderDslMarker

    @RunnerBuilderDslMarker
    class RunnerBuilder(
        private var readSpeedPerPartition: Int,
        private val clusters: MutableList<ClusterBuilder> = mutableListOf()
    ) {

        fun cluster(name: String, action: ClusterBuilder.() -> Unit) {
            clusters.add(
                ClusterBuilder(name).apply(action)
            )
        }

        fun build(): RunnerState = RunnerState(
            readSpeedPerPoll = readSpeedPerPartition,
            clusters = clusters.map { it.build() }.toList()
        )
    }

    @RunnerBuilderDslMarker
    class ClusterBuilder(private val name: String, private var topics: MutableList<TopicBuilder> = mutableListOf()) {

        fun topic(name: String, partitions: Int, action: TopicBuilder.() -> Unit) {
            val topicBuilder = TopicBuilder(name, partitions, mutableListOf())
            topicBuilder.action()
            topics.add(topicBuilder)
        }

        fun build(): Cluster = Cluster(
            name = name,
            topics = topics.map { it.build() }.toList()
        )
    }

    @RunnerBuilderDslMarker
    class TopicBuilder(
        private val name: String,
        private val partitions: Int,
        private val messages: MutableList<Message>
    ) {

        fun message(
            content: JsonNode,
            key: String = uuid(),
            partition: Int? = null,
            offset: Long? = null,
            timestamp: Long? = null
        ) {
            val effectiveTimestamp = timestamp ?: now()
            val effectivePartition = partition ?: Random.nextInt(from = 0, until = partitions)
            val effectiveOffset =
                offset
                    ?: messages.filter { it.partition == effectivePartition }.map { it.offset }.max()?.let { it + 1 }
                    ?: 0

            messages.add(
                Message(
                    offset = effectiveOffset,
                    timestamp = effectiveTimestamp,
                    partition = effectivePartition,
                    content = content,
                    key = key
                )
            )
        }

        fun build(): Topic = Topic(
            name = name,
            messages = messages
                .asSequence()
                .groupBy { it.partition }
                .mapValues { (_, partitionRecords) ->
                    partitionRecords
                        .sortedBy { it.offset }
                        .toList()
                        .also {
                            check(it.isSortedAscendingBy { rec -> rec.timestamp }) {
                                "inconsistent state - offsets and timestamps are not consistent: $it"
                            }
                        }
                },
            partitions = (0..partitions).toSet()
        )
    }
}

fun simulator(readSpeedPerPoll: Int, action: Simulator.RunnerBuilder.() -> Unit): SimulatorRunner {
    val state = Simulator.RunnerBuilder(readSpeedPerPoll, mutableListOf()).apply(action).build()
    return SimulatorRunner(state)
}

class SimulatorRunner(private val state: Simulator.RunnerState) : ZoeRunner {

    override val name: String = "simulator"

    override fun launch(function: String, payload: String): CompletableFuture<String> =
        CompletableFuture.completedFuture(handle(function, payload).toJsonString())

    private fun handle(function: String, payload: String): Any = when (function) {
        "topics" -> handleTopicList(payload.parseJson())
        "poll" -> handlePoll(payload.parseJson())
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

        val records: List<Simulator.Message> = kotlin.run {
            val candidates = when (val subscription = payload.subscription) {

                is Subscription.FromBeginning ->
                    topic.messagesForPartitions(subscription.partitions ?: topic.partitions)

                is Subscription.AssignPartitions ->
                    topic
                        .messagesForPartitions(subscription.partitions.keys)
                        .filter { it.offset >= subscription.partitions.getValue(it.partition) }

                is Subscription.FromTimestamp ->
                    topic
                        .messagesForPartitions(subscription.partitions ?: topic.partitions)
                        .filter { it.timestamp >= subscription.ts }

                is Subscription.OffsetStepBack ->
                    topic
                        .messagesForPartitions(subscription.partitions ?: topic.partitions)
                        .filter { it.offset >= topic.latestOffset(it.partition)!! - subscription.offsetsBack }

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
                .groupBy { (topic.name to it.partition) }
                .map { (topicPartition, partitionRecords) ->
                    val (_, partition) = topicPartition
                    PartitionProgress(
                        topic = topic.name,
                        partition = partition,
                        earliestOffset = topic.earliestOffset(partition)
                            ?: throw IllegalStateException("no messages found for partition: $partition"),
                        latestOffset = topic.latestOffset(partition)
                            ?: throw IllegalStateException("no messages found for partition: $partition"),
                        progress = Progress(
                            currentOffset = partitionRecords.maxBy { it.offset }?.offset
                                ?: throw IllegalStateException("no partition records found: $partition"),
                            currentTimestamp = partitionRecords.maxBy { it.offset }?.timestamp
                                ?: throw IllegalStateException("no partition records found: $partition"),
                            startOffset = partitionRecords.minBy { it.offset }?.offset
                                ?: throw IllegalStateException("no partition records found: $partition"),
                            recordsCount = partitionRecords.size.toLong()
                        )
                    )
                }
        )
    }

    override fun close() {}

}

fun main() {
    simulator(2) {
        cluster("test") {
            topic("hello", 3) {
                message(1.toJsonNode(), partition = 0, offset = 0, timestamp = 2)
                message(1.toJsonNode(), partition = 0, offset = 3, timestamp = 3)
                message(1.toJsonNode(), partition = 1, offset = 1, timestamp = 5)
                message(1.toJsonNode(), partition = 1, offset = 2, timestamp = 6)
            }
        }
    }
}