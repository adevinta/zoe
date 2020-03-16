// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.core.functions

import com.adevinta.oss.zoe.core.utils.consumer
import com.adevinta.oss.zoe.core.utils.jmespath
import com.adevinta.oss.zoe.core.utils.json
import com.adevinta.oss.zoe.core.utils.match
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import java.io.ByteArrayOutputStream
import java.time.Duration

/**
 * Polls records from kafka
 */
val poll = zoeFunction<PollConfig, PollResponse>(name = "poll") { config ->

    // parse filters and select statement
    val filters = config.filter.map(jmespath::compile)
    val query = config.query?.let(jmespath::compile)
    val jsonifier = Jsonifiers.get(config.jsonifier)

    val props = HashMap(config.props).apply {
        putIfAbsent(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1000")
        putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
        putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    }

    val consumer = consumer(config = props)

    // subscribe to the topic
    consumer.subscribe(topic = config.topic, subscription = config.subscription)
    consumer.use { cons ->

        val (records, progressListener) =
            cons.pollAsSequence(config.timeoutMs)

        val filtered =
            records
                .map {
                    PolledRecord(
                        key = it.key()?.toString(),
                        offset = it.offset(),
                        timestamp = it.timestamp(),
                        partition = it.partition(),
                        topic = it.topic(),
                        formatted = it.value()?.let { rec -> jsonifier.format(rec) } ?: NullNode.getInstance()
                    )
                }
                .filter { it.formatted.match(filters) }
                .map { if (query == null) it else it.copy(formatted = query.search(it.formatted)) }
                .take(config.numberOfRecords)
                .toList()

        PollResponse(
            records = filtered,
            progress = progressListener.reportProgress()
        )
    }
}

data class PollResponse(
    val records: Iterable<PolledRecord>,
    val progress: Iterable<PartitionProgress>
)

data class PolledRecord(
    val key: String?,
    val offset: Long,
    val timestamp: Long,
    val partition: Int,
    val topic: String,
    val formatted: JsonNode
)

private fun Consumer<*, *>.subscribe(topic: String, subscription: Subscription) = when (subscription) {
    is Subscription.AssignPartitions -> {
        assign(subscription.partitions.map { TopicPartition(topic, it.key) })
        subscription.partitions.forEach { (partition, offset) ->
            seek(TopicPartition(topic, partition), offset)
        }
    }

    is Subscription.WithGroupId -> subscribe(listOf(topic))
}


private fun <K, V> Consumer<K, V>.pollAsSequence(timeoutMs: Long): Pair<Sequence<ConsumerRecord<K, V>>, ProgressListener> {
    val progressListener = ProgressListener(this)
    val records = sequence {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val batch = poll(Duration.ofSeconds(3))
            for (record in batch) {
                progressListener.ackProgress(record)
                yield(record)
            }
        }
    }

    return (records to progressListener)
}

private class ProgressListener(val consumer: Consumer<*, *>) {

    private data class ConsumedRecords(
        val first: ConsumerRecord<*, *>,
        val last: ConsumerRecord<*, *>,
        val count: Long
    )

    private val consumed: MutableMap<TopicPartition, ConsumedRecords?> = mutableMapOf()

    fun ackProgress(record: ConsumerRecord<*, *>) {
        val part = TopicPartition(record.topic(), record.partition())
        val previouslyConsumed = consumed[part]
        consumed[part] = ConsumedRecords(
            first = previouslyConsumed?.first?.takeIf { it.offset() <= record.offset() } ?: record,
            last = previouslyConsumed?.last?.takeIf { it.offset() >= record.offset() } ?: record,
            count = (previouslyConsumed?.count ?: 0) + 1
        )
    }

    fun reportProgress(): List<PartitionProgress> {
        val partitions = consumer.assignment()
        val earliestOffsets = consumer.beginningOffsets(partitions)
        val latestOffsets = consumer.endOffsets(partitions)

        return partitions.map { part ->
            val consumedInPartition = consumed[part]

            PartitionProgress(
                topic = part.topic(),
                partition = part.partition(),
                earliestOffset = earliestOffsets.getValue(part),
                latestOffset = latestOffsets.getValue(part),
                progress = if (consumedInPartition != null) {
                    Progress(
                        currentTimestamp = consumedInPartition.last.timestamp(),
                        currentOffset = consumedInPartition.last.offset(),
                        startOffset = consumedInPartition.first.offset(),
                        recordsCount = consumedInPartition.count
                    )
                } else {
                    val position = consumer.position(part)
                    Progress(
                        currentTimestamp = null,
                        currentOffset = position,
                        startOffset = position,
                        recordsCount = 0
                    )
                }
            )
        }
    }
}

object Jsonifiers {
    // TODO : enable injection of custom jsonifiers
    private val jsonifiers = sequenceOf(GenericRecordToAvroJson(), RawToJson()).map { it.name() to it }.toMap()

    fun get(name: String): Jsonifier =
        jsonifiers[name] ?: throw IllegalArgumentException("jsonifier not found : $name")
}

interface Jsonifier {
    fun name(): String
    fun format(input: Any): JsonNode
}

class GenericRecordToAvroJson : Jsonifier {
    override fun name() = "avro"

    override fun format(input: Any): JsonNode {
        require(input is GenericRecord) { "the 'avro' formatter requires GenericRecords as input" }

        return try {
            ByteArrayOutputStream().use {
                val writer = GenericDatumWriter<GenericRecord>(input.schema)
                val encoder = EncoderFactory.get().jsonEncoder(input.schema, it)
                writer.write(input, encoder)
                encoder.flush()
                json.readTree(it.toByteArray())
            }
        } catch (e: Throwable) {
            throw RuntimeException("cannot convert generic record to json : $this", e)
        }
    }
}

class RawToJson : Jsonifier {
    override fun name(): String = "raw"
    override fun format(input: Any): JsonNode = json.readTree(input.toString())
}

data class PartitionProgress(
    val topic: String,
    val partition: Int,
    val earliestOffset: Long,
    val latestOffset: Long,
    val progress: Progress
)

data class Progress(
    val currentTimestamp: Long?,
    val currentOffset: Long,
    val startOffset: Long,
    val recordsCount: Long
)

data class PollConfig(
    val topic: String,
    val subscription: Subscription,
    val props: Map<String, String> = mapOf(),
    val filter: List<String> = listOf(),
    val query: String? = null,
    val timeoutMs: Long = 10000,
    val numberOfRecords: Int = 3,
    val jsonifier: String
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = Subscription.WithGroupId::class, name = "withGroupId"),
    JsonSubTypes.Type(value = Subscription.AssignPartitions::class, name = "assignPartitions")
)
sealed class Subscription {
    object WithGroupId : Subscription()
    data class AssignPartitions(val partitions: Map<Int, Long>) : Subscription()
}