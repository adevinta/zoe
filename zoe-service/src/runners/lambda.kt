// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.runners

import com.adevinta.oss.zoe.core.utils.parseJson
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.adevinta.oss.zoe.core.utils.toJsonString
import com.adevinta.oss.zoe.service.utils.lambdaClient
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.metrics.AwsSdkMetrics
import com.amazonaws.services.lambda.AWSLambda
import com.amazonaws.services.lambda.model.InvokeRequest
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.function.Supplier

class LambdaZoeRunner(
    override val name: String,
    private val executor: ExecutorService,
    private val client: AWSLambda
) : ZoeRunner {

    companion object {
        const val LambdaFunctionName = "zoe-final-launch"
    }

    constructor(
        name: String,
        executor: ExecutorService,
        awsCredentials: AWSCredentialsProvider,
        awsRegion: String?
    ) : this(
        name = name,
        executor = executor,
        client = lambdaClient(awsCredentials, awsRegion)
    )

    override suspend fun launch(function: String, payload: String): String = CompletableFuture.supplyAsync(
        Supplier {
            AwsSdkMetrics.disableMetrics()
            val response = client.invoke(
                InvokeRequest().apply {
                    functionName =
                        LambdaFunctionName
                    setPayload(
                        mapOf(
                            "function" to function,
                            "payload" to payload.toJsonNode()
                        ).toJsonString()
                    )
                }
            )

            val parsed = String(response.payload.array(), Charsets.UTF_8)

            if (response.functionError != null) {
                throw parsed.parseJson<LambdaExecutionError>().toZoeRunnerException()
            }

            parsed
        },
        executor
    ).await()

    override fun close() {}

    private data class LambdaExecutionError(
        val errorMessage: String,
        val errorType: String,
        val stackTrace: List<String>,
        val cause: LambdaExecutionError?
    )

    private fun LambdaExecutionError.toZoeRunnerException(): ZoeRunnerException =
        ZoeRunnerException(
            message = "$errorMessage (type: $errorType)",
            cause = cause?.toZoeRunnerException(),
            runnerName = name,
            remoteStacktrace = stackTrace
        )
}
