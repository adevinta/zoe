// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli.commands

import com.adevinta.oss.zoe.cli.config.*
import com.adevinta.oss.zoe.cli.utils.singleCloseable
import com.adevinta.oss.zoe.service.ZoeService
import com.adevinta.oss.zoe.service.config.InMemoryConfigStore
import com.adevinta.oss.zoe.service.config.RegisteredExpression
import com.adevinta.oss.zoe.service.executors.KubernetesExecutor
import com.adevinta.oss.zoe.service.executors.LambdaZoeExecutor
import com.adevinta.oss.zoe.service.executors.LocalZoeExecutor
import com.adevinta.oss.zoe.service.executors.ZoeExecutor
import com.adevinta.oss.zoe.service.secrets.*
import com.adevinta.oss.zoe.service.storage.KeyValueStore
import com.adevinta.oss.zoe.service.storage.LocalFsKeyValueStore
import com.adevinta.oss.zoe.service.storage.withNamespace
import com.adevinta.oss.zoe.service.utils.userError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.TermColors
import com.github.ajalt.mordant.TerminalCapabilities
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.koin.core.context.loadKoinModules
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@FlowPreview
@ExperimentalCoroutinesApi
class ZoeCommandLine : CliktCommand(name = "zoe") {
    private val home by lazy { "${System.getenv("HOME") ?: userError("HOME not found")}/.zoe" }
    private val env: String by option("--env", "-e", help = "Environment to use", envvar = "ZOE_ENV").default("default")
    private val cluster: String? by option("--cluster", "-c", help = "Target cluster", envvar = "ZOE_CLUSTER")
    private val executor: ExecutorName?
            by option("--executor", "-x", help = "Executor to use").choice(
                ExecutorName.Lambda.code to ExecutorName.Lambda,
                ExecutorName.Local.code to ExecutorName.Local,
                ExecutorName.Kubernetes.code to ExecutorName.Kubernetes
            )

    private val outputFormat: Format
            by option("-o", "--output", help = "Output format")
                .choice(
                    "raw" to Format.Raw,
                    "json" to Format.Json,
                    "table" to Format.Table
                )
                .default(Format.Raw)

    private val colorize by option("-C", "--colorize", help = "Force terminal colors").flag(default = false)

    private val configDir
            by option("--config-dir", help = "Directory where config files are", envvar = "ZOE_CONFIG_DIR")
                .file()
                .defaultLazy { File("$home/config") }

    private val verbosity: Int
            by option("-v", help = "verbose mode (can be set multiple times)").counted()

    val term by lazy {
        TermConfig(
            output = outputFormat,
            colors = TermColors(if (colorize) TermColors.Level.ANSI16 else TerminalCapabilities.detectANSISupport())
        )
    }

    override fun run() {
        LogManager.getRootLogger().level = when (verbosity) {
            0 -> Level.ERROR
            1 -> Level.INFO
            2 -> Level.DEBUG
            else -> Level.ALL
        }

        loadKoinModules(
            mainModule(
                CliContext(
                    home = home,
                    executor = executor,
                    cluster = cluster,
                    configDir = configDir,
                    env = env,
                    term = term
                )
            )
        )
    }

    fun printErr(err: Throwable) {
        System.err.println(formatError(err))
        if (System.getenv("ZOE_STACKTRACE") == "1") {
            err.printStackTrace(System.err)
        }
    }

    private fun formatError(err: Throwable, level: Int = 0): String {
        val res = when (err) {

            is IllegalArgumentException ->
                "${term.colors.red("error:")} ${err.message}"

            else -> buildString {
                append("""${term.colors.red("failure:")} ${err.message}""")
                val cause = err.cause
                if (cause != null) {
                    appendln()
                    appendln(term.colors.red("cause:"))
                    append(formatError(cause, level = level + 1))
                }
            }
        }
        return res.prependIndent(indent = " ".repeat(2 * level))
    }
}

@FlowPreview
@ExperimentalCoroutinesApi
fun mainModule(context: CliContext) = module {
    single<CliContext> { context }

    single {
        ConfigUrlProviderChain(
            listOf(
                EnvVarsConfigUrlProvider,
                LocalConfigDirUrlProvider(context.configDir)
            )
        ).createConfig(context.env)
    }

    single<ExecutorService>(named("io")) { Executors.newCachedThreadPool() } onClose { it?.shutdown() }

    singleCloseable<KeyValueStore> {
        LocalFsKeyValueStore(
            "${context.home}/storage/${context.env}",
            get(named("io"))
        )
    }

    singleCloseable<SecretsProvider?> {
        val config = get<EnvConfig>()
        val storage = get<KeyValueStore>()

        val provider = when (val secrets = config.secrets) {
            null -> null

            is SecretsProviderConfig.Strongbox -> StrongboxProvider(
                credentials = secrets.credentials.resolve(),
                region = secrets.region
            )

            is SecretsProviderConfig.EnvVars -> EnvVarsSecretProvider(
                append = secrets.append ?: "",
                prepend = secrets.prepend ?: ""
            )

            else -> userError("no secrets provider matched : $secrets")
        }

        provider
            ?.withCaching(
                store = storage.withNamespace("secrets"),
                ttl = Duration.ofDays(1)
            )
            ?.withLogging()
    }

    singleCloseable<NamespacedKubernetesClient> {
        val config = get<EnvConfig>().executors.config.kubernetes

        val client =
            if (config.context != null) DefaultKubernetesClient(Config.autoConfigure(config.context))
            else DefaultKubernetesClient()

        client.inNamespace(config.namespace)
    }

    singleCloseable<ZoeExecutor> {
        val pool = get<ExecutorService>(named("io"))

        val executorsSectionWithSecrets =
            get<SecretsProvider?>().resolveSecretsInJsonSerializable(get<EnvConfig>().executors)

        when (context.executor ?: executorsSectionWithSecrets.default) {
            ExecutorName.Lambda -> with(executorsSectionWithSecrets.config.lambda) {
                LambdaZoeExecutor(
                    name = ExecutorName.Lambda.code,
                    executor = pool,
                    awsCredentials = credentials.resolve(),
                    awsRegion = awsRegion
                )
            }

            ExecutorName.Local -> LocalZoeExecutor(
                name = ExecutorName.Local.code,
                executor = pool
            )

            ExecutorName.Kubernetes -> {
                val kubeConfig = executorsSectionWithSecrets.config.kubernetes
                KubernetesExecutor(
                    name = ExecutorName.Kubernetes.code,
                    client = get(),
                    configuration = KubernetesExecutor.Config(
                        zoeImage = "wlezzar/zoe-core:1.1", // TODO : make this not hard coded
                        cpu = kubeConfig.cpu,
                        memory = kubeConfig.memory,
                        deletePodsAfterCompletion = kubeConfig.deletePodAfterCompletion,
                        timeoutMs = kubeConfig.timeoutMs
                    )
                )
            }
        }
    }

    single<ZoeService> {
        val config = get<EnvConfig>()
        ZoeService(
            configStore = InMemoryConfigStore(
                clusters = config.clusters,
                filters = config.expressions.mapValues {
                    RegisteredExpression(
                        it.key,
                        it.value
                    )
                }
            ),
            executor = get(),
            storage = get(),
            secrets = get()
        )
    }
}

data class TermConfig(val output: Format, val colors: TermColors)

data class CliContext(
    val home: String,
    val term: TermConfig,
    val configDir: File,
    val env: String,
    val cluster: String?,
    val executor: ExecutorName?
)

fun CliContext.requireCluster(): String = cluster ?: userError("you must select a cluster !")
