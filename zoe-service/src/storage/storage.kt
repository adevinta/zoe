// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.storage

import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.service.config.s3
import kotlinx.coroutines.future.await
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import kotlin.streams.asSequence

interface KeyValueStore : Closeable {
    fun inNamespace(namespace: String)
    suspend fun get(key: String): ByteArray?
    suspend fun put(key: String, value: ByteArray)
    suspend fun listKeys(): Iterable<String>
    override fun close() {}
}

class LocalFsKeyValueStore(private var namespace: String, private val es: ExecutorService) :
    KeyValueStore {

    private suspend fun <T> submit(block: () -> T): T = CompletableFuture.supplyAsync(block).await()

    override suspend fun get(key: String): ByteArray? = submit {
        val path = Path.of("$namespace/$key")
        if (path.toFile().exists()) Files.readAllBytes(path) else null
    }

    override suspend fun put(key: String, value: ByteArray) = submit {
        val path = Path.of("$namespace/$key")
        if (!path.parent.toFile().exists()) {
            Files.createDirectories(path.parent)
        }
        Files.newOutputStream(path).use { it.write(value) }
    }

    override suspend fun listKeys(): Iterable<String> = submit {
        Files
            .list(Path.of(namespace))
            .asSequence()
            .map { it.fileName.toString() }
            .toList()
    }

    override fun inNamespace(namespace: String) {
        this.namespace = "${this.namespace}/$namespace"
    }
}

class AwsFsKeyValueStore(private val bucket: String, private var prefix: String) :
    KeyValueStore {

    private val cache: MutableMap<String, ByteArray?> = HashMap()

    private suspend fun <T> submit(block: () -> T): T = CompletableFuture.supplyAsync(block).await()

    override suspend fun put(key: String, value: ByteArray): Unit = submit {
        cache.remove("$prefix/$key")
        s3.putObject(bucket, "$prefix/$key", String(value))
    }

    override suspend fun get(key: String): ByteArray? = submit {
        cache.getOrPut("$prefix/$key") {
            s3
                .runCatching { getObject(bucket, "$prefix/$key").objectContent.use { it.readBytes() } }
                .getOrElse {
                    logger.warn("couldn't fetch key '$key' in namespace '$prefix' : '${it.message}'")
                    null
                }
        }
    }


    override suspend fun listKeys(): Iterable<String> = submit {
        val root = "$prefix/"
        s3.listObjectsV2(bucket, root).objectSummaries.map { it.key.replaceFirst(root, "") }
    }

    override fun inNamespace(namespace: String) {
        this.prefix = "${this.prefix}/$namespace"
    }


}
