// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli.utils

import com.adevinta.oss.zoe.core.utils.logger
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.ajalt.mordant.TermColors
import com.jakewharton.picnic.BorderStyle
import com.jakewharton.picnic.Table
import com.jakewharton.picnic.TextAlignment
import com.jakewharton.picnic.table
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.whileSelect
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudformation.CloudFormationAsyncClient
import software.amazon.awssdk.services.iam.IamAsyncClient
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

val yaml = ObjectMapper(YAMLFactory()).registerKotlinModule()

fun iamClient(credentials: AwsCredentialsProvider, awsRegion: Region?): IamAsyncClient =
    IamAsyncClient
        .builder()
        .credentialsProvider(credentials)
        .region(Region.AWS_GLOBAL)
//        .let { if (awsRegion != null) it.region(awsRegion) else it }
        .build()

fun cfClient(credentials: AwsCredentialsProvider, awsRegion: Region?): CloudFormationAsyncClient =
    CloudFormationAsyncClient
        .builder()
        .credentialsProvider(credentials)
        .let { if (awsRegion != null) it.region(awsRegion) else it }
        .build()

fun zipEntries(entries: Map<String, Path>): ByteArray = ByteArrayOutputStream().use { stream ->
    ZipOutputStream(stream).use { zs ->
        for ((entry, content) in entries) {
            zs.putNextEntry(ZipEntry(entry))
            Files.copy(content, zs)
            zs.closeEntry()
        }
    }
    stream.toByteArray()
}

fun <T> retryUntilNotNull(timeoutMs: Long, onTimeoutMsg: String?, block: () -> T?): T {
    val start = System.currentTimeMillis()

    do {
        val result = block.invoke()

        if (result != null) {
            return result
        }

        Thread.sleep(3000)
    } while (System.currentTimeMillis() - start < timeoutMs)

    throw TimeoutException(onTimeoutMsg)
}

suspend fun <T> retrySuspendingUntilNotNull(timeoutMs: Long, onTimeoutMsg: String?, block: suspend () -> T?): T {
    val start = System.currentTimeMillis()

    do {
        val result = block.invoke()

        if (result != null) {
            return result
        }

        delay(timeMillis = 3000)
    } while (System.currentTimeMillis() - start < timeoutMs)

    throw TimeoutException(onTimeoutMsg)
}


fun fetch(input: InputStream, streaming: Boolean): Flow<String> = flow {
    input.use {
        if (streaming) {
            val reader = input.bufferedReader(Charsets.UTF_8)
            while (true) {
                while (reader.ready()) {
                    logger.info("received record...")
                    emit(reader.readLine())
                }
                delay(200)
            }
        } else {
            if (it.available() != 0) {
                val data = CompletableFuture.supplyAsync { it.readBytes().toString(Charsets.UTF_8) }.await()
                emit(data)
            } else {
                logger.info("stream is empty")
            }
        }
    }
}


fun CoroutineScope.timeoutChannel(ms: Long) = produce {
    while (isActive) {
        delay(ms)
        send(System.currentTimeMillis())
    }
}

fun <T> Flow<T>.batches(timeoutMs: Long, scope: CoroutineScope): Flow<List<T>> = flow {
    val channel = produceIn(scope)
    val timeout = scope.timeoutChannel(timeoutMs)

    while (!channel.isClosedForReceive) {
        val batch = ArrayList<T>()

        whileSelect {
            timeout.onReceive { false }
            channel.onReceive {
                batch.add(it)
                !channel.isClosedForReceive
            }
        }

        emit(batch)
    }
}

fun <T, R> useResource(resource: T, onClose: (T) -> Unit, block: (T) -> R): R =
    Closeable { onClose(resource) }.use { block(resource) }

fun toPrettyPrintedTableRow(element: Any, level: Int): String = when {
    element is ObjectNode ->
        element
            .fields()
            .asSequence()
            .map { (key, value) -> "$key: $value" }
            .joinToString(separator = if (level <= 1) "\n" else "  ")

    element is ArrayNode -> when {
        level <= 1 -> element
            .asSequence()
            .map { toPrettyPrintedTableRow(it, level = level.inc()) }
            .joinToString(separator = "\n")
        else -> element.toString()
    }

    element is JsonNode && element.isTextual -> element.textValue()

    else -> element.toString()
}

fun JsonNode.toPrettyPrintedTable(level: Int = 0): String = toTable(
    this,
    level
).toString()

private fun toTable(input: JsonNode, level: Int = 0): Table = table {
    style {
        borderStyle = BorderStyle.Solid
    }

    cellStyle {
        border = true
        alignment = TextAlignment.MiddleLeft
        paddingRight = 1
        paddingLeft = 1
    }

    when (input) {
        is ObjectNode -> {
            val fields = input.fieldNames().asSequence().toList()

            header {
                row(*fields.toTypedArray())
            }
            row {
                fields.forEach {
                    cell(
                        toPrettyPrintedTableRow(
                            input[it],
                            level.inc()
                        )
                    )
                }
            }
        }

        is ArrayNode -> when {
            input.none() -> {
                /* do nothing */
            }

            input.first() is ObjectNode -> {
                val fields = input.first().fieldNames().asSequence().toList()
                header {
                    row(*fields.toTypedArray())
                }
                input.forEach { element ->
                    row {
                        fields.forEach { fieldName ->
                            cell(
                                toPrettyPrintedTableRow(
                                    element[fieldName],
                                    level.inc()
                                )
                            )
                        }
                    }
                }
            }

            else -> {
                // pretty print array of strings
                header { row("value") }
                input.forEach {
                    row(it.toString())
                }
            }
        }

        else -> {
            header { row("value") }
            row(input.toString())
        }
    }
}

val globalTermColors = TermColors()

fun loadFileFromResources(path: String): String? =
    Thread.currentThread()
        .contextClassLoader
        .getResourceAsStream(path)
        ?.use { it.readBytes() }
        ?.toString(Charsets.UTF_8)
