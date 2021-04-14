// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.utils

import com.adevinta.oss.zoe.core.utils.logger
import kotlinx.coroutines.future.await
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.lambda.LambdaAsyncClient
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

const val Timeout = 60 * 5 * 1000

class HelpWrappedError(val help: String, val original: Throwable) : Exception(original)

fun userError(message: String, help: String? = null): Nothing {
    val exception = IllegalArgumentException(message)
    if (help != null) throw HelpWrappedError(help = help, original = exception) else throw exception
}

fun Throwable.withHelpMessage(help: String) = HelpWrappedError(help = help, original = this)

fun lambdaClient(credentials: AwsCredentialsProvider, awsRegion: Region?): LambdaAsyncClient =
    LambdaAsyncClient
        .builder()
        .credentialsProvider(credentials)
        .let { if (awsRegion != null) it.region(awsRegion) else it }
        .overrideConfiguration { config -> config.retryPolicy { it.numRetries(0) } }
        .build()

fun loadFileFromResources(path: String): String? =
    Thread.currentThread()
        .contextClassLoader
        .getResourceAsStream(path)
        ?.use { it.readBytes() }
        ?.toString(Charsets.UTF_8)

suspend fun <T> ExecutorService.doSuspending(action: () -> T): T =
    CompletableFuture.supplyAsync(Supplier { action() }, this).await()

data class CommandResult(val stdout: String, val stderr: String, val exitCode: Int)

fun exec(command: Array<String>, failOnError: Boolean = true, timeout: Duration? = null): CommandResult {
    logger.debug("executing command: ${command.contentToString()}")

    val res =
        Runtime
            .getRuntime()
            .exec(command)
            .apply { timeout?.let { waitFor(it.seconds, TimeUnit.SECONDS) } ?: waitFor() }

    val stdout = res.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
    val stderr = res.errorStream.use { it.readBytes() }.toString(Charsets.UTF_8)

    val commandResult =
        """command '${command.contentToString()}' finished with exit code: ${res.exitValue()}
            | - stdout: $stdout
            | - stderr: $stderr
            """.trimMargin()

    if (res.exitValue() != 0 && failOnError) {
        throw Exception(commandResult)
    }

    logger.debug(commandResult)

    return CommandResult(stdout, stderr, res.exitValue())
}
