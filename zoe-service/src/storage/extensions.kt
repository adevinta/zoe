// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.storage

import com.adevinta.oss.zoe.core.utils.parseJson
import com.adevinta.oss.zoe.core.utils.toJsonBytes

fun <T : KeyValueStore> T.withNamespace(namespace: String): T {
    useNamespace(namespace)
    return this
}

suspend inline fun <reified T : Any> KeyValueStore.putAsJson(key: String, value: T) {
    put(key, value.toJsonBytes())
}

suspend inline fun <reified T : Any> KeyValueStore.getAsJson(key: String): T? =
    get(key)?.parseJson()

suspend inline fun <reified T : Any> KeyValueStore.getOrPutAsJson(key: String, loader: () -> T): T {
    val found = get(key)
    return if (found != null) {
        found.parseJson()
    } else {
        val obj = loader.invoke()
        put(key, obj.toJsonBytes())
        obj
    }
}

suspend inline fun <reified T : Any> KeyValueStore.putIfAbsentAsJson(key: String, loader: () -> T) {
    if (get(key) == null) {
        put(key, loader.invoke().toJsonBytes())
    }
}