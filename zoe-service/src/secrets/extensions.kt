// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.secrets

import com.adevinta.oss.zoe.core.utils.json
import com.adevinta.oss.zoe.core.utils.parseJson
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.adevinta.oss.zoe.service.storage.KeyValueStore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import java.time.Duration

fun SecretsProvider.resolveSecrets(dict: Map<String, String>): Map<String, String> = when (this) {
    is NoopSecretsProvider -> dict
    else -> dict.mapValues { secretOrRaw(it.value) }
}

inline fun <reified T : Any> SecretsProvider.resolveSecretsInJsonSerializable(obj: T): T = when (this) {
    is NoopSecretsProvider -> obj
    else -> resolveSecrets(obj.toJsonNode()).parseJson()
}

fun SecretsProvider.resolveSecrets(node: JsonNode): JsonNode = when (node) {
    is ObjectNode -> json.createObjectNode().apply {
        node.fields().forEach { (field, value) -> set<JsonNode>(field, resolveSecrets(value)) }
    }
    is TextNode -> TextNode(secretOrRaw(node.textValue()))
    else -> node
}

fun SecretsProvider.secretOrRaw(value: String): String = if (isSecret(value)) decipher(value) else value

fun SecretsProvider.withLogging() =
    SecretsProviderWithLogging(this)

fun SecretsProvider.withCaching(store: KeyValueStore, ttl: Duration) =
    SecretsProviderWithCache(this, store = store, ttl = ttl)