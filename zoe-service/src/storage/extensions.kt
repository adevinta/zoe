package com.adevinta.oss.zoe.service.storage

import com.adevinta.oss.zoe.core.utils.parseJson
import com.adevinta.oss.zoe.core.utils.toJsonBytes

fun <T : KeyValueStore> T.withNamespace(namespace: String): T {
    inNamespace(namespace)
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