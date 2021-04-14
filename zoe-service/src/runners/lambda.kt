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
import kotlinx.coroutines.future.await
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.lambda.LambdaAsyncClient

class LambdaZoeRunner(
    override val name: String,
    private val version: String,
    private val suffix: String?,
    private val client: LambdaAsyncClient
) : ZoeRunner {

    companion object {
        const val LambdaFunctionNamePrefix = "zoe-final-launch"
        fun functionName(version: String, suffix: String?): String =
            listOfNotNull(LambdaFunctionNamePrefix, version, suffix)
                .joinToString(separator = "-")
                .replace(".", "-")
    }

    constructor(
        name: String,
        version: String,
        suffix: String?,
        awsCredentials: AwsCredentialsProvider,
        awsRegion: Region?
    ) : this(
        name = name,
        version = version,
        suffix = suffix,
        client = lambdaClient(awsCredentials, awsRegion)
    )

    override suspend fun launch(function: String, payload: String): String {
        val response =
            client
                .invoke {
                    it.functionName(functionName(version, suffix))
                    it.payload(
                        SdkBytes.fromString(
                            mapOf(
                                "function" to function,
                                "payload" to payload.toJsonNode()
                            ).toJsonString(),
                            Charsets.UTF_8
                        )
                    )
                }
                .await()

        val parsed = response.payload().asUtf8String()

        if (response.functionError() != null) {
            throw parsed.parseJson<LambdaExecutionError>().toZoeRunnerException()
        }

        return parsed
    }

    override fun close() {}

    private data class LambdaExecutionError(
        val errorMessage: String,
        val errorType: String,
        val stackTrace: List<String>?,
        val cause: LambdaExecutionError?
    )

    private fun LambdaExecutionError.toZoeRunnerException(level: Int = 0): ZoeRunnerException =
        ZoeRunnerException(
            message = buildString {
                if (level <= 0) append("runner '$name' failed: ")
                append("$errorMessage (type: $errorType)")
            },
            cause = cause?.toZoeRunnerException(level = level + 1),
            runnerName = name,
            remoteStacktrace = stackTrace
        )
}
