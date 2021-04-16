package com.adevinta.oss.zoe.cli.config

import com.adevinta.oss.zoe.cli.utils.yaml
import com.adevinta.oss.zoe.cli.utils.yamlPrettyWriter
import com.adevinta.oss.zoe.cli.zoeHome
import com.adevinta.oss.zoe.core.functions.JsonQueryDialect
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

interface DefaultsProvider {
    suspend fun defaults(): ZoeDefaults
    suspend fun persist(defaults: ZoeDefaults)
}

data class ZoeDefaults(
    val cluster: String = "default",
    val environment: String = "default",
    val outputFormat: Format = Format.Raw,
    val silent: Boolean = false,
    val topic: Topic = Topic(),
) {
    data class Topic(val consume: Consume = Consume()) {
        data class Consume(val jsonQueryDialect: JsonQueryDialect = JsonQueryDialect.Jmespath)
    }
}

class HomeFileDefaultsProvider : DefaultsProvider {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val source: File = File(zoeHome).resolve("defaults.yml")

    override suspend fun defaults(): ZoeDefaults = withContext(Dispatchers.IO) {
        source
            .takeIf { it.exists() }
            ?.runCatching { yaml.readValue<ZoeDefaults>(this) }
            ?.onFailure { err ->
                logger.warn(
                    "Unable to load defaults from '$source'. " +
                        "If the problem persists, delete the file manually. (err: $err)"
                )
            }
            ?.getOrNull()
            ?: ZoeDefaults()
    }

    override suspend fun persist(defaults: ZoeDefaults) =
        withContext(Dispatchers.IO) {
            logger.info("saving defaults at: $source")
            yamlPrettyWriter.writeValue(source, defaults)
        }

}
