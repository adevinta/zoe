// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.core.functions

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import com.adevinta.oss.zoe.core.utils.jmespath
import com.adevinta.oss.zoe.core.utils.json
import com.adevinta.oss.zoe.core.utils.producer
import com.adevinta.oss.zoe.core.utils.uuid
import com.fasterxml.jackson.annotation.JsonSubTypes
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

/**
 * Lambda function to producer a bunch of records into kafka
 */
val produce = zoeFunction<ProduceConfig, ProduceResponse>(name = "produce") { config ->
    val keyPath = config.keyPath?.let(jmespath::compile)
    val valuePath = config.valuePath?.let(jmespath::compile)
    val tsPath = config.timestampPath?.let(jmespath::compile)

    val dejsonifier = Dejsonifier.fromConfig(config.dejsonifier)

    val records = config.data.map {
        ProducerRecord<Any?, Any?>(
            config.topic,
            null,
            tsPath?.search(it)?.longValue(),
            keyPath?.search(it)?.textValue() ?: uuid(),
            dejsonifier.dejsonify(valuePath?.search(it) ?: it)
        )
    }

    when {
        config.dryRun -> ProduceResponse(produced = listOf(), skipped = records.map { it.formatResponse() })
        else -> {
            val produced = producer(config.props).use { producer ->
                records.map(producer::send).map { it.get().formatResponse() }
            }
            ProduceResponse(produced = produced, skipped = emptyList())
        }
    }
}

private sealed class Dejsonifier {
    companion object;
    abstract fun dejsonify(input: JsonNode): Any
}

private class JsonNodeToGenericRecord(val schema: Schema) : Dejsonifier() {
    override fun dejsonify(input: JsonNode): Any = try {
        GenericDatumReader<GenericRecord>(schema).read(
            null,
            DecoderFactory.get().jsonDecoder(schema, input.toString())
        )
    } catch (e: Throwable) {
        throw IllegalArgumentException("Couldn't convert json to avro schema '${schema.name}': $input", e)
    }
}

private object JsonNodeToBytes : Dejsonifier() {
    override fun dejsonify(input: JsonNode): Any = json.writeValueAsBytes(input)
}

private fun Dejsonifier.Companion.fromConfig(config: DejsonifierConfig): Dejsonifier = when (config) {
    is DejsonifierConfig.Avro -> JsonNodeToGenericRecord(
        schema = CachedSchemaRegistryClient(config.registry, 10)
            .getLatestSchemaMetadata(config.subject)
            .schema
            .let { Schema.Parser().parse(it) }

    )
    is DejsonifierConfig.Raw -> JsonNodeToBytes
}

data class ProduceConfig(
    val topic: String,
    val dejsonifier: DejsonifierConfig,
    val keyPath: String?,
    val valuePath: String?,
    val timestampPath: String?,
    val data: List<JsonNode>,
    val dryRun: Boolean,
    val props: Map<String, String>
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DejsonifierConfig.Avro::class, name = "avro"),
    JsonSubTypes.Type(value = DejsonifierConfig.Raw::class, name = "raw")
)
sealed class DejsonifierConfig {
    data class Avro(val registry: String, val subject: String) : DejsonifierConfig()
    object Raw : DejsonifierConfig()
}

data class SkippedRecord(
    val key: String,
    val value: String
)

data class ProducedRecord(
    val offset: Long,
    val partition: Int,
    val topic: String,
    val timestamp: Long
)

data class ProduceResponse(
    val produced: List<ProducedRecord>,
    val skipped: List<SkippedRecord>
)

private fun RecordMetadata.formatResponse() = ProducedRecord(
    offset = offset(),
    partition = partition(),
    timestamp = timestamp(),
    topic = topic()
)

private fun ProducerRecord<*, *>.formatResponse() = SkippedRecord(
    key = "${key()}",
    value = "${value()}"
)
