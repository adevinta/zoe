// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli.commands

import com.adevinta.oss.zoe.cli.config.*
import com.adevinta.oss.zoe.cli.utils.globalTermColors
import com.adevinta.oss.zoe.cli.utils.yaml
import com.adevinta.oss.zoe.cli.utils.yamlPrettyWriter
import com.adevinta.oss.zoe.core.utils.buildJson
import com.adevinta.oss.zoe.core.utils.json
import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.adevinta.oss.zoe.service.utils.userError
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.*
import org.eclipse.jgit.util.FS
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.inject
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption


class ConfigCommand : NoOpCliktCommand(name = "config", help = "Inspect or initialize zoe config") {
    init {
        subcommands(
            ConfigInit(),
            ConfigEdit(),
            ConfigClusters(),
            ConfigEnvironments(),
            ConfigDefaults()
        )
    }
}

class ConfigInit : CliktCommand(
    name = "init",
    help = "Initialize zoe config",
    epilog = with(globalTermColors) {
        """```
        |Examples:
        |
        |  Init config with a default configuration file:
        |  > ${bold("zoe config init")}
        |
        |  Load config from a local directory:
        |  > ${bold("""zoe config init --from local --path /path/to/existing/config""")}
        |
        |  Load config from a git repository:
        |  > ${bold("""zoe config init --from git --url 'https://github.com/adevinta/zoe.git' --dir docs/guides/simple/config""")}
        |
        |  Load config from a git repository with authentication:
        |  > ${bold("""zoe config init --from git --url 'https://github.company.com/example/config.git' --dir zoe-config --username user --password pass""")}
        |
        |  You can also use a github token as a username:
        |  > ${bold("""zoe config init --from git --url 'https://github.company.com/example/config.git' --dir zoe-config --username gh-token""")}
        |
        |```""".trimMargin()
    }
), KoinComponent {

    private val ctx by inject<CliContext>()

    private val recreate: Boolean by option(
        "--recreate",
        help = "Recreate the configuration folder from scratch"
    ).flag(
        default = false
    )

    private val overwrite: Boolean by option("--overwrite", help = "Overwrite existing configuration folder").flag(
        default = false
    )

    private val from by option("--from", help = "Import from an existing configuration folder").groupChoice(
        "local" to LoadFrom.Local(),
        "git" to LoadFrom.Git()
    )

    override fun run() {
        val configDir = ctx.configDir
        val fromDir = from?.getSourceDir()

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
                        logger.info("copying '$source' to '$target'")
                        Files.copy(file.toPath(), target, *options)
                    } catch (exists: FileAlreadyExistsException) {
                        userError("file already exists : ${exists.message} (use --overwrite)")
                    }
                }
            }

            else -> {
                val target = ctx.configDir.resolve("${ctx.env}.yml")

                logger.info("creating a new config file in: $target")

                if (target.exists() && !overwrite) {
                    logger.info("config file '${target.absolutePath}' already exists ! (--overwrite to recreate)")
                    return
                }

                val config = EnvConfig(
                    clusters = mapOf(
                        "local" to ClusterConfig(
                            props = mapOf(
                                "bootstrap.servers" to "localhost:29092",
                                "key.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer",
                                "value.deserializer" to "org.apache.kafka.common.serialization.StringDeserializer",
                                "key.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
                                "value.serializer" to "org.apache.kafka.common.serialization.ByteArraySerializer"
                            ),
                            registry = null
                        )
                    ),
                    runners = RunnersSection(default = RunnerName.Local),
                    storage = null,
                    secrets = null
                )

                yaml.setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                    .setSerializationInclusion(JsonInclude.Include.NON_DEFAULT)
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(target, config)
            }
        }
    }
}

class ConfigEdit : CliktCommand(
    name = "edit",
    help = "Edit the config file of a specific environment (chosen using -e flag)"
), KoinComponent {

    private val ctx by inject<CliContext>()

    override fun run() {
        val target = ctx.configDir.resolve("${ctx.env}.yml")

        if (!target.exists()) {
            userError(
                message = "config file not found: $target",
                help = "You may want to initialize your config first for the specified environment: zoe -e ${ctx.env} config init"
            )
        }

        val content = target.readText(charset = Charsets.UTF_8)
        val header = "## NOTE: editing $target\n\n"

        val edited = TermUi.editText(
            text = "$header${content}",
            requireSave = true,
            extension = ".yml"
        )?.replaceFirst(header, "")

        if (edited.isNullOrBlank()) {
            logger.info("leaving file unchanged (empty or unsaved content)")
            return
        }

        // validate config
        yaml
            .runCatching { readValue<EnvConfig>(edited) }
            .getOrElse { userError("Invalid config after edition: $it") }

        logger.info("saving config into: $target")
        target.writeText(charset = Charsets.UTF_8, text = edited)
    }
}


class ConfigClusters : NoOpCliktCommand(name = "clusters") {

    init {
        subcommands(List(), SetDefault())
    }

    class List : CliktCommand(name = "list", help = "List configured clusters"), KoinComponent {
        private val ctx by inject<CliContext>()
        private val env by inject<EnvConfig>()

        override fun run() {
            val response = env.clusters.map { (name, config) ->
                mapOf(
                    "cluster" to name,
                    "brokers" to config.props["bootstrap.servers"],
                    "registry" to config.registry,
                    "topics" to config.topics.map { (alias, topic) ->
                        buildJson {
                            put("alias", alias)
                            put("name", topic.name)
                        }
                    },
                    "groups" to config.groups
                )
            }

            ctx.term.output.format(response.toJsonNode()) { echo(it) }
        }
    }

    class SetDefault : CliktCommand(name = "set-default", help = "Set the default cluster"), KoinComponent {
        private val provider: DefaultsProvider = get()
        private val defaults: ZoeDefaults = get()

        private val cluster: String by argument("cluster")

        override fun run() = runBlocking {
            provider.persist(defaults.copy(cluster = cluster))
        }
    }
}

class ConfigEnvironments : NoOpCliktCommand(name = "environments"), KoinComponent {

    init {
        subcommands(List(), SetDefault())
    }

    class List : CliktCommand(name = "list", help = "List environments"), KoinComponent {
        private val ctx by inject<CliContext>()

        override fun run() {
            val environments = ctx.configDir
                .takeIf { it.exists() && it.isDirectory }
                ?.listFiles()
                ?.map { it.nameWithoutExtension }
                ?.filter { it != "common" }
                ?: emptyList()

            ctx.term.output.format(environments.toJsonNode()) { echo(it) }
        }

    }

    class SetDefault : CliktCommand(name = "set-default", help = "Set the default environment"), KoinComponent {
        private val provider: DefaultsProvider = get()
        private val defaults: ZoeDefaults = get()

        private val environment: String by argument("environment")

        override fun run() = runBlocking {
            provider.persist(defaults.copy(environment = environment))
        }
    }
}


class ConfigDefaults : NoOpCliktCommand(name = "defaults", help = "Manage zoe defaults") {
    init {
        subcommands(Init(), Edit())
    }

    class Init : CliktCommand(name = "init", help = "Initialize the zoe defaults file"), KoinComponent {
        private val provider: DefaultsProvider = get()
        override fun run() = runBlocking { provider.persist(ZoeDefaults()) }
    }

    class Edit : CliktCommand(name = "edit", help = "Open an editor to edit the defaults file"), KoinComponent {
        private val provider: DefaultsProvider = get()
        private val defaults: ZoeDefaults = get()

        private val format: EditingFormat by option(
            "--format",
            help = "Preferred editing format (json or yml)"
        ).choice(choices = EditingFormat.values().associateBy { it.code }).default(EditingFormat.Yaml)

        override fun run() {
            val edited = TermUi.editText(
                when (format) {
                    EditingFormat.Json -> json.writerWithDefaultPrettyPrinter().writeValueAsString(defaults)
                    EditingFormat.Yaml -> yamlPrettyWriter.writeValueAsString(defaults)
                },
                requireSave = true,
                extension = when (format) {
                    EditingFormat.Json -> ".json"
                    EditingFormat.Yaml -> ".yml"
                }
            )

            when {
                edited.isNullOrBlank() -> logger.warn("Leaving defaults unchanged (empty or unsaved content)")
                else -> runBlocking { provider.persist(yaml.readValue(edited)) }
            }
        }

    }
}

enum class EditingFormat(val code: String) { Json("json"), Yaml("yaml") }

sealed class LoadFrom(name: String) : OptionGroup(name) {
    class Local : LoadFrom("Options to load from local") {
        val path: File
            by option("--path")
                .file(mustExist = true, canBeDir = true, mustBeReadable = true)
                .required()
    }

    class Git : LoadFrom("Options to load from git") {
        val url: String by option("--url", help = "remote url of the repository").required()
        val dir: String by option("--dir", help = "path to the config inside the repo").default(".")
        val username: String? by option("-u", "--username")
        val password: String? by option("--password")
        val privateKey: File?
            by option("--private-key").file(canBeDir = false, canBeFile = true, mustExist = true)
        val passphrase: String? by option("--passphrase")
    }
}

fun LoadFrom.getSourceDir(): File = when (this) {
    is LoadFrom.Local -> path

    is LoadFrom.Git -> {
        val temp = Files.createTempDirectory("tmp-zoe-config-init-").toFile().also { it.deleteOnExit() }

        Git
            .cloneRepository()
            .setURI(url)
            .let {
                when {
                    password != null || username != null -> it.setCredentialsProvider(
                        UsernamePasswordCredentialsProvider(
                            username ?: "",
                            password ?: ""
                        )
                    )
                    privateKey != null -> it.setTransportConfigCallback(GitSshTransport(privateKey, passphrase))
                    else -> it
                }
            }
            .setDirectory(temp)
            .call()

        temp.resolve(dir)
    }
}

private class GitSshTransport(val privateKey: File?, val passphrase: String?) : TransportConfigCallback {
    override fun configure(transport: Transport?) {
        (transport as SshTransport).sshSessionFactory = object : JschConfigSessionFactory() {
            override fun configure(host: OpenSshConfig.Host?, session: Session) {
                session.setConfig("StrictHostKeyChecking", "no")
            }

            override fun createDefaultJSch(fs: FS): JSch = super.createDefaultJSch(fs).apply {
                privateKey?.absolutePath?.let { privateKey ->
                    when {
                        passphrase != null -> addIdentity(privateKey, passphrase)
                        else -> addIdentity(privateKey)
                    }
                }
            }
        }

    }
}

fun configCommands() = ConfigCommand()
