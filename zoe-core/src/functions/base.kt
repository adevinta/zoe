// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.core.functions

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.adevinta.oss.zoe.core.utils.json
import java.io.InputStream
import java.io.OutputStream

/**
 * Main interface to implement a callable function in the zoe core.
 */
interface ZoeFunction {
    fun name(): String
    fun execute(payload: InputStream, output: OutputStream)
}

interface JsonZoeFunction<Payload, Response> : ZoeFunction {
    fun execute(payload: Payload): Response
    fun payloadType(): TypeReference<Payload>
    fun responseType(): TypeReference<Response>

    override fun execute(payload: InputStream, output: OutputStream) {
        val parsed = json.readValue<Payload>(payload.readBytes(), payloadType())
        val response = execute(parsed)
        output.write(json.writeValueAsBytes(response))
    }
}

inline fun <reified Payload, reified Response> zoeFunction(
    name: String,
    crossinline block: (Payload) -> Response
): JsonZoeFunction<Payload, Response> = object : JsonZoeFunction<Payload, Response> {
    override fun name(): String = name
    override fun execute(payload: Payload): Response = block(payload)
    override fun payloadType(): TypeReference<Payload> = jacksonTypeRef()
    override fun responseType(): TypeReference<Response> = jacksonTypeRef()
}
