// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli.commands

import com.adevinta.oss.zoe.cli.config.*
import com.adevinta.oss.zoe.cli.utils.help
import com.adevinta.oss.zoe.cli.utils.loadFileFromResources
import com.adevinta.oss.zoe.cli.utils.singleCloseable
import com.adevinta.oss.zoe.cli.zoeHome
import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.adevinta.oss.zoe.service.ZoeService
import com.adevinta.oss.zoe.service.config.InMemoryConfigStore
import com.adevinta.oss.zoe.service.expressions.RegisteredExpression
import com.adevinta.oss.zoe.service.runners.KubernetesRunner
import com.adevinta.oss.zoe.service.runners.LambdaZoeRunner
import com.adevinta.oss.zoe.service.runners.LocalZoeRunner
import com.adevinta.oss.zoe.service.runners.ZoeRunner
import com.adevinta.oss.zoe.service.secrets.*
import com.adevinta.oss.zoe.service.storage.KeyValueStore
import com.adevinta.oss.zoe.service.storage.LocalFsKeyValueStore
import com.adevinta.oss.zoe.service.storage.withInMemoryBuffer
import com.adevinta.oss.zoe.service.storage.withNamespace
import com.adevinta.oss.zoe.service.utils.HelpWrappedError
import com.adevinta.oss.zoe.service.utils.userError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.TermColors
import com.github.ajalt.mordant.TerminalCapabilities
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.koin.core.KoinComponent
import org.koin.core.context.loadKoinModules
import org.koin.core.get
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.dsl.onClose
import software.amazon.awssdk.regions.Region
import java.io.File
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ZoeCommandLine : CliktCommand(name = "zoe"), KoinComponent {
    private val defaults: ZoeDefaults = get()

    private val env: String
        by option("--env", "-e", help = "Environment to use", envvar = "ZOE_ENV")
            .default(defaults.environment)

    private val cluster: String by option(
        "--cluster",
        "-c",
        help = "Target cluster",
        envvar = "ZOE_CLUSTER"
    ).default(defaults.cluster)

    override fun aliases(): Map<String, List<String>> = AliasesCommand.aliases(zoeHome)

    private val runner: RunnerName?
        by option("--runner", "-r", help = "Runner to use").choice(RunnerName.values().associateBy { it.code })

    private val outputFormat: Format
        by option("-o", "--output", help = "Output format")
            .choice(Format.values().associateBy { it.code })
            .default(defaults.outputFormat)

    private val colorize by option("-C", "--colorize", help = "Force terminal colors").flag(default = false)

    private val configDir
        by option("--config-dir", help = "Directory where config files are", envvar = "ZOE_CONFIG_DIR")
            .file()
            .defaultLazy { File("$zoeHome/config") }

    private val verbosity: Int
        by option("-v", help = "verbose mode (can be set multiple times)").counted()

    private val silent by option("--silent", help = "hide non error logs")
        .flag("--no-silent", default = defaults.silent)

    private val term by lazy {
        TermConfig(
            output = outputFormat,
            colors = TermColors(if (colorize) TermColors.Level.ANSI16 else TerminalCapabilities.detectANSISupport())
        )
    }

    override fun run() {
        LogManager.getRootLogger().level = when {
            silent -> Level.ERROR
            verbosity <= 1 -> Level.INFO
            verbosity == 2 -> Level.DEBUG
            else -> Level.ALL
        }

        loadKoinModules(
            mainModule(
                CliContext(
                    home = zoeHome,
                    runner = runner,
                    cluster = cluster,
                    configDir = configDir,
                    env = env,
                    term = term,
                    version = loadFileFromResources("version.json")?.toJsonNode()?.get("projectVersion")?.asText()
                        ?: kotlin.run {
                            logger.warn("Couldn't find version from resources. Using 'latest'")
                            "latest"
                        }
                )
            )
        )
    }

    fun printErr(err: Throwable) {
        val res = buildString {
            append(formatError(err))
            formatHelp(extractHelp(err))?.let { help ->
                appendLine()
                append(help)
            }
        }

        System.err.println(res)
        if (System.getenv("ZOE_STACKTRACE") == "1") {
            err.printStackTrace(System.err)
        }
    }

    private fun extractHelp(err: Throwable?): List<String> = when (err) {
        null -> emptyList()
        is HelpWrappedError -> listOf(err.help) + extractHelp(err.original)
        else -> err.help() + extractHelp(err.cause)
    }

    private fun formatError(err: Throwable, level: Int = 0): String {
        val error = when (err) {
            is HelpWrappedError -> formatError(err = err.original, level = level)
            is IllegalArgumentException -> "${term.colors.red("error:")} ${err.message}"
            else -> buildString {
                append("""${term.colors.red("failure:")} ${err.message}""")
                val cause = err.cause
                if (cause != null) {
                    appendLine()
                    appendLine(term.colors.red("cause:"))
                    append(formatError(cause, level = level + 1))
                }
            }
        }

        return error.prependIndent(indent = " ".repeat(2 * level))
    }

    private fun formatHelp(helpStack: List<String>) =
        helpStack.takeIf { it.isNotEmpty() }?.let {
            val message = if (it.size == 1) it.first() else it.joinToString(separator = "\n- ", prefix = "\n- ")
            with(term.colors) {
                bold(yellow("Help: ")) + message
            }
        }

}

fun mainModule(context: CliContext) = module {
    single<CliContext> { context }

    single<EnvConfig> {
        val envConfig = ConfigUrlProviderChain(
            listOf(
                EnvVarsConfigUrlProvider,
                LocalConfigDirUrlProvider(context.configDir)
            )
        ).createConfig(context.env)

        customize(envConfig)
    }

    single<ZoeService> {
        val config = get<EnvConfig>()
        ZoeService(
            configStore = InMemoryConfigStore(
                clusters = config.clusters.mapValues { it.value.toDomain() },
                filters = config.expressions.mapValues {
                    RegisteredExpression(
                        it.key,
                        it.value
                    )
                }
            ),
            runner = get(),
            storage = get(),
            secrets = get()
        )
    }

    singleCloseable<ZoeRunner> {
        val ctx = get<CliContext>()
        val ioPool = get<ExecutorService>(named("io"))

        val runnersSectionWithSecrets =
            get<SecretsProvider>().resolveSecretsInJsonSerializable(get<EnvConfig>().runners)

        when (context.runner ?: runnersSectionWithSecrets.default) {
            RunnerName.Lambda -> with(runnersSectionWithSecrets.configOrDefault().lambda) {
                LambdaZoeRunner(
                    name = RunnerName.Lambda.code,
                    version = ctx.version,
                    suffix = nameSuffix,
                    awsCredentials = credentials.resolve(),
                    awsRegion = awsRegion?.let { Region.of(it) }
                )
            }

            RunnerName.Local -> LocalZoeRunner(
                name = RunnerName.Local.code,
                executor = ioPool
            )

            RunnerName.Kubernetes -> {
                val kubeConfig = runnersSectionWithSecrets.configOrDefault().kubernetes

                KubernetesRunner(
                    name = RunnerName.Kubernetes.code,
                    configuration = KubernetesRunner.Config(
                        zoeImage = with(kubeConfig.image) { "$registry/$image:${tag ?: ctx.version}" },
                        cpu = kubeConfig.cpu,
                        memory = kubeConfig.memory,
                        deletePodsAfterCompletion = kubeConfig.deletePodAfterCompletion,
                        timeoutMs = kubeConfig.timeoutMs
                    ),
                    executor = ioPool,
                    namespace = kubeConfig.namespace,
                    context = kubeConfig.context
                )
            }
        }
    }

    singleCloseable<SecretsProvider> {
        val config = get<EnvConfig>()
        val storage = get<KeyValueStore>()

        val provider = when (val secrets = config.secrets) {
            null -> NoopSecretsProvider

            is SecretsProviderConfig.Strongbox ->
                throw IllegalArgumentException("strongbox provider is not supported anymore")

            is SecretsProviderConfig.EnvVars -> EnvVarsSecretProvider(
                append = secrets.append ?: "",
                prepend = secrets.prepend ?: ""
            )

            is SecretsProviderConfig.Exec -> ExecSecretProvider(
                command = secrets.command,
                timeout = Duration.ofMillis(secrets.timeoutMs)
            )

            is SecretsProviderConfig.AwsSecretsManager -> AwsSecretsManagerProvider(
                credentials = secrets.credentials.resolve(),
                region = secrets.region?.let { Region.of(it) }
            )

            else -> userError("no secrets provider matched : $secrets")
        }

        provider
            .withCaching(
                store = storage.withNamespace("secrets"),
                ttl = Duration.ofDays(1)
            )
            .withLogging()
    }

    singleCloseable<KeyValueStore> {
        LocalFsKeyValueStore(root = File("${context.home}/storage/${context.env}")).withInMemoryBuffer()
    }

    single<ExecutorService>(named("io")) { Executors.newCachedThreadPool() } onClose { it?.shutdown() }
}

private inline fun <reified T> Scope.customize(data: T): T {
    val customizers = getAll<(T) -> T>()
    return customizers.fold(data) { it, customizer -> customizer(it) }
}

data class TermConfig(val output: Format, val colors: TermColors)

data class CliContext(
    val home: String,
    val term: TermConfig,
    val configDir: File,
    val env: String,
    val cluster: String,
    val runner: RunnerName?,
    val version: String
)
