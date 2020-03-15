package com.adevinta.oss.zoe.service.simulator

import com.adevinta.oss.zoe.core.utils.now
import com.adevinta.oss.zoe.core.utils.uuid
import com.fasterxml.jackson.databind.JsonNode
import kotlin.random.Random


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

private fun <T, R : Comparable<R>> Iterable<T>.isSortedAscendingBy(by: (T) -> R) =
    map(by).zipWithNext().all { (previous, next) -> previous <= next }


fun simulator(readSpeedPerPoll: Int, action: RunnerBuilder.() -> Unit): ZoeRunnerSimulator {
    val state = RunnerBuilder(readSpeedPerPoll, mutableListOf()).apply(action).build()
    return ZoeRunnerSimulator(state)
}
