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
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Supplier

interface KeyValueStore : Closeable {
    fun useNamespace(namespace: String)
    suspend fun get(key: String): ByteArray?
    suspend fun put(key: String, value: ByteArray)
    suspend fun listKeys(): Iterable<String>
    override fun close() {}
}

class LocalFsKeyValueStore(private var root: File) : KeyValueStore {

    private val executor = Executors.newSingleThreadExecutor()

    private suspend fun <T> submit(block: () -> T): T =
        CompletableFuture.supplyAsync(Supplier { block() }, executor).await()

    override suspend fun get(key: String): ByteArray? = submit {
        root.resolve(key).takeIf { it.exists() }?.readBytes()
    }

    override suspend fun put(key: String, value: ByteArray) = submit {
        root
            .resolve(key)
            .also {
                val parent = it.parentFile
                if (!parent.exists()) {
                    Files.createDirectories(parent.toPath())
                }
            }
            .outputStream()
            .use { it.write(value) }
    }

    override suspend fun listKeys(): Iterable<String> = submit {
        root
            .listFiles()
            ?.asSequence()
            ?.map { it.name.toString() }
            ?.toList()
            ?: emptyList()
    }

    override fun useNamespace(namespace: String) {
        this.root = root.resolve(namespace)
    }

    override fun close() {
        executor.shutdown()
    }
}

class AwsFsKeyValueStore(
    private val bucket: String,
    private var prefix: String,
    private val executor: ExecutorService
) : KeyValueStore {

    private val cache: MutableMap<String, ByteArray?> = HashMap()

    private suspend fun <T> submit(block: () -> T): T =
        CompletableFuture.supplyAsync(Supplier { block() }, executor).await()

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

    override fun useNamespace(namespace: String) {
        this.prefix = "${this.prefix}/$namespace"
    }

}
