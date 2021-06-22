// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.runners

import com.adevinta.oss.zoe.core.FailureResponse
import java.io.Closeable

/**
 * ZoeRunner is the client is responsible to call zoe core functions by their names and passing a payload to them.
 */
interface ZoeRunner : Closeable {
    val name: String
    suspend fun launch(function: String, payload: String): String
}

class ZoeRunnerException(
    message: String?,
    cause: Throwable?,
    val runnerName: String,
    val remoteStacktrace: List<String>?
) : Exception(message, cause) {
    companion object
}

fun ZoeRunnerException.Companion.fromRunFailureResponse(
    error: FailureResponse,
    runnerName: String,
    level: Int = 0,
    maxDepth: Int = 5,
): ZoeRunnerException = ZoeRunnerException(
    message = buildString {
        if (level <= 0) append("runner '$runnerName' failed: ")
        append("${error.errorMessage} (type: ${error.errorType})")
    },
    cause = error.cause
        ?.takeIf { level <= maxDepth }
        ?.let { ZoeRunnerException.fromRunFailureResponse(error, runnerName, level = level + 1, maxDepth = maxDepth) },
    runnerName = runnerName,
    remoteStacktrace = error.stackTrace
)
