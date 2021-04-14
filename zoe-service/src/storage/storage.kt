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
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.function.Supplier

interface KeyValueStore : Closeable {
    fun useNamespace(namespace: String)
    suspend fun get(key: String): ByteArray?
    suspend fun put(key: String, value: ByteArray)
    suspend fun delete(key: String): ByteArray?
    suspend fun listKeys(): Iterable<String>
    override fun close() {}
}

class BufferedKeyValueStore(private val wrapped: KeyValueStore) : KeyValueStore {

    internal sealed class EntryState {
        class Present(val data: ByteArray) : EntryState()
        object Deleted : EntryState()
    }

    private val inMemoryBuffer = mutableMapOf<String, EntryState>()

    override fun useNamespace(namespace: String) {
        wrapped.useNamespace(namespace)
    }

    override suspend fun get(key: String): ByteArray? {
        val found = inMemoryBuffer[key] ?: wrapped.get(key)?.let { EntryState.Present(it) }
        return (found as? EntryState.Present)?.data
    }

    override suspend fun put(key: String, value: ByteArray) {
        inMemoryBuffer[key] = EntryState.Present(data = value)
    }

    override suspend fun delete(key: String): ByteArray? {
        val existing = inMemoryBuffer[key] as? EntryState.Present
        inMemoryBuffer[key] = EntryState.Deleted
        return existing?.data
    }

    override suspend fun listKeys(): Iterable<String> {
        val deletedKeys = inMemoryBuffer.filterValues { it == EntryState.Deleted }.keys
        val inMemoryKeys = inMemoryBuffer.filterValues { it is EntryState.Present }.keys
        val persistedKeys = wrapped.listKeys()

        return inMemoryKeys.union(persistedKeys).minus(deletedKeys)
    }

    override fun close() = runBlocking {
        inMemoryBuffer.forEach { (key, value) ->
            when (value) {
                is EntryState.Present -> wrapped.put(key, value.data)
                EntryState.Deleted -> wrapped.delete(key)
            }
        }
        wrapped.close()
    }
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

    override suspend fun delete(key: String): ByteArray? = submit {
        val file = root.resolve(key).takeIf { it.exists() }
        val existing = file?.readBytes()
        file?.deleteRecursively()
        existing
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
) : KeyValueStore {

    override suspend fun put(key: String, value: ByteArray): Unit {
        s3
            .putObject(
                PutObjectRequest.builder().bucket(bucket).key("$prefix/$key").build(),
                AsyncRequestBody.fromBytes(value)
            )
            .await()
    }

    override suspend fun delete(key: String): ByteArray? {
        val existing = get(key)
        s3.deleteObject { it.bucket(bucket).key("$prefix/$key") }.await()
        return existing
    }

    override suspend fun get(key: String): ByteArray? =
        try {
            s3
                .getObject(
                    GetObjectRequest.builder().bucket(bucket).key("$prefix/$key").build(),
                    AsyncResponseTransformer.toBytes()
                )
                .await()
                .asByteArray()
        } catch (err: Exception) {
            logger.warn("couldn't fetch key '$key' in namespace '$prefix' : '${err.message}'")
            null
        }

    override suspend fun listKeys(): Iterable<String> {
        val root = "$prefix/"
        return s3.listObjectsV2 { it.bucket(bucket).prefix(root) }
            .await()
            .contents()
            .map { it.key().replaceFirst(root, "") }
    }

    override fun useNamespace(namespace: String) {
        this.prefix = "${this.prefix}/$namespace"
    }
}
