// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.core

import com.adevinta.oss.zoe.core.functions.*
import com.adevinta.oss.zoe.core.utils.json
import com.adevinta.oss.zoe.core.utils.toJsonString
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.fasterxml.jackson.databind.JsonNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.system.exitProcess

/**
 * Main entrypoint when executing as a standalone program or in a remote container
 *
 * It accepts 2 arguments:
 * 1. The payload (mandatory)
 * 2. A file path (optional) : if given, results are written to the file pointed out by the path.
 *    Otherwise, results are written to output stream.
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage : zoe <payload> [<output-path>]" }

    val input = args[0].byteInputStream(Charsets.UTF_8)
    val output = if (args.size > 1) File(args[1]).outputStream() else System.out

    val exitCode =
        output.use { out ->
            input.use { inp ->
                Handler()
                    .runCatching { handleRequest(inp, out, context = null) }
                    .onFailure { err ->
                        out.write(
                            FailureResponse
                                .fromThrowable(err)
                                .toJsonString()
                                .toByteArray(Charsets.UTF_8)
                        )
                    }
                    .fold(onSuccess = { 0 }, onFailure = { 1 })
            }
        }

    exitProcess(exitCode)
}

/**
 * Main handler for zoe requests. It implements a {@link RequestStreamHandler} to make this class directly usable as
 * lambda functions entrypoint.
 */
class Handler : RequestStreamHandler {
    override fun handleRequest(input: InputStream, output: OutputStream, context: Context?) {
        val parsed: JsonNode = json.readTree(input.readBytes())

        val function = parsed["function"]?.asText() ?: error("missing 'function' field")
        val payload = parsed["payload"]?.toString() ?: error("missing 'payload' field")

        FunctionsRegistry.call(function, payload.byteInputStream(Charsets.UTF_8), output)
    }
}

object FunctionsRegistry {

    private val functions = mutableMapOf<String, ZoeFunction>()

    init {
        add(version)
        add(listTopics)
        add(createTopic)
        add(listSchemas)
        add(queryOffsets)
        add(listGroups)
        add(deploySchema)
        add(offsets)
        add(poll)
        add(produce)
    }

    private fun add(function: ZoeFunction) {
        functions[function.name()] = function
    }

    fun call(name: String, payload: InputStream, output: OutputStream) {
        val function = functions[name] ?: throw IllegalArgumentException("function not registered : $name")
        function.execute(payload, output)
    }

    fun call(name: String, payload: String): String = ByteArrayOutputStream().use { out ->
        call(name, payload.byteInputStream(Charsets.UTF_8), out)
        String(out.toByteArray(), Charsets.UTF_8)
    }

    fun list(): List<ZoeFunction> = functions.values.toList()
}

/**
 * Generic failure response (inspired by the json exception sent by AWS lambdas)
 */
data class FailureResponse(
    val errorMessage: String,
    val errorType: String,
    val stackTrace: List<String>?,
    val cause: FailureResponse?
) {
    companion object
}

fun FailureResponse.Companion.fromThrowable(throwable: Throwable): FailureResponse = FailureResponse(
    errorMessage = throwable.message ?: "",
    errorType = throwable::class.java.canonicalName,
    cause = throwable.cause?.let(FailureResponse.Companion::fromThrowable),
    stackTrace = throwable.stackTrace?.map { it.toString() } ?: emptyList()
)

fun error(msg: String): Nothing = throw IllegalArgumentException(msg)