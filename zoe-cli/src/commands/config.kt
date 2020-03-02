// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli.commands

import com.adevinta.oss.zoe.cli.config.*
import com.adevinta.oss.zoe.cli.utils.yaml
import com.adevinta.oss.zoe.core.utils.json
import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.service.utils.userError
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ConfigCommand : CliktCommand(name = "config", help = "Initialize zoe") {
    override fun run() {}
}

@ExperimentalCoroutinesApi
@FlowPreview
class ConfigInit : CliktCommand(name = "init", help = "Initialize zoe config"), KoinComponent {

    private val ctx by inject<CliContext>()

    private val recreate: Boolean by option("--recreate", help = "Recreate the configuration folder from scratch").flag(
        default = false
    )

    private val overwrite: Boolean by option("--overwrite", help = "Overwrite existing configuration folder").flag(
        default = false
    )

    private val from: File? by option("--from", help = "Import from an existing configuration folder").file(
        exists = true,
        folderOkay = true,
        readable = true
    )

    override fun run() {
        val configDir = ctx.configDir
        val fromDir = from

        if (recreate && configDir.exists()) {
            logger.info("deleting existing config directory : ${configDir.absolutePath}")
            configDir.deleteRecursively()
        }

        Files.createDirectories(configDir.toPath())

        when {

            fromDir != null -> {
                val sourceConfigFiles = fromDir.listFiles { file -> file.isFile && file.extension == "yml" }
                    ?: userError("provided source is not listable : $fromDir")

                for (file in sourceConfigFiles) {
                    val name = file.name
                    val source = file.toPath()
                    val target = configDir.toPath().resolve(name)
                    val options = if (overwrite) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()

                    try {
                        logger.info("copying '${source}' to '${target}'")
                        Files.copy(file.toPath(), target, *options)
                    } catch (exists: FileAlreadyExistsException) {
                        userError("file already exists : ${exists.message} (use --overwrite)")
                    }
                }
            }

            else -> {
                logger.info("creating a new config file...")

                val target = ctx.configDir.toPath().resolve("default.yml").toFile()

                if (target.exists() && !overwrite) {
                    logger.info("config file '${target.absolutePath}' already exists ! (--overwrite to recreate)")
                    return
                }

                val config = EnvConfig(
                    runners = RunnersSection(default = RunnerName.Local),
                    clusters = mapOf(
                        "local" to ClusterConfig(
                            registry = "http://localhost:8081",
                            topics = mapOf(
                                "input" to TopicConfig(
                                    "input-topic",
                                    "input-topic-value"
                                )
                            ),
                            props = mapOf(
                                "bootstrap.servers" to "localhost:29092",
                                "key.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer",
                                "value.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer"
                            )
                        )
                    ),
                    storage = null,
                    secrets = null
                )

                val jsonValue: JsonNode = json.valueToTree(config)

                yaml.setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(target, jsonValue)
            }
        }
    }
}

@FlowPreview
@ExperimentalCoroutinesApi
fun configCommands() = ConfigCommand().subcommands(
    ConfigInit()
)
