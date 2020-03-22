// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.utils

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.AWSLambdaClientBuilder
import kotlinx.coroutines.future.await
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.function.Supplier

const val Timeout = 60 * 5 * 1000

fun userError(message: String): Nothing = throw IllegalArgumentException(message)

fun lambdaClient(credentials: AWSCredentialsProvider, awsRegion: String?): AWSLambda =
    AWSLambdaClientBuilder
        .standard()
        .withCredentials(credentials)
        .let { if (awsRegion != null) it.withRegion(awsRegion) else it }
        .withClientConfiguration(
            ClientConfiguration()
                .withMaxErrorRetry(0)
                .withConnectionTimeout(Timeout)
                .withRequestTimeout(Timeout)
                .withSocketTimeout(Timeout)
        )
        .build()

fun loadFileFromResources(path: String): String? =
    Thread.currentThread()
        .contextClassLoader
        .getResourceAsStream(path)
        ?.use { it.readBytes() }
        ?.toString(Charsets.UTF_8)

suspend fun <T> ExecutorService.doAsync(action: () -> T): T =
    CompletableFuture.supplyAsync(Supplier { action() }, this).await()