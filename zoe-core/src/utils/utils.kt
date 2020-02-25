// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.core.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.burt.jmespath.Expression
import io.burt.jmespath.jackson.JacksonRuntime
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.util.*


val json = ObjectMapper().registerKotlinModule()
val jmespath = JacksonRuntime()
val logger = LoggerFactory.getLogger("zoe")!!

fun consumer(config: Map<String, Any>): KafkaConsumer<Any?, Any?> = KafkaConsumer(config)
fun producer(config: Map<String, Any>): KafkaProducer<Any?, Any?> = KafkaProducer(config)
fun admin(config: Map<String, Any>): AdminClient = AdminClient.create(config)

inline fun <reified T> String.parseJson(): T = json.readValue(this, T::class.java)
inline fun <reified T> JsonNode.parseJson(): T = json.treeToValue(this, T::class.java)
inline fun <reified T> ByteArray.parseJson(): T = json.readValue(this, T::class.java)

fun Any.toJsonBytes(): ByteArray = json.writeValueAsBytes(this)
fun Any.toJsonString(): String = json.writeValueAsString(this)
fun Any.toJsonNode(): JsonNode = json.valueToTree(this)
fun String.toJsonNode(): JsonNode = json.readTree(this)

fun JsonNode.match(filters: List<Expression<JsonNode>>) =
    filters.all { it.search(this)?.asBoolean() == true }

fun uuid(): String = UUID.randomUUID().toString()
fun now(): Long = System.currentTimeMillis()