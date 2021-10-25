package com.adevinta.oss.zoe.cli

import com.adevinta.oss.zoe.cli.config.EnvConfig
import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import com.github.ajalt.clikt.output.CliktConsole
import io.kotest.core.spec.style.scopes.ExpectScope
import io.kotest.core.test.TestContext
import io.kotest.matchers.shouldBe
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.io.File

class MockConsole : CliktConsole {
    val stdout = ArrayList<String>()
    val stderr = ArrayList<String>()

    override val lineSeparator: String = "\n"

    override fun print(text: String, error: Boolean) {
        if (error) stderr.add(text) else stdout.add(text)
    }

    override fun promptForLine(prompt: String, hideInput: Boolean): String? {
        TODO("Not yet implemented")
    }
}

data class ZoeOutput(val stdout: JsonNode?, val stderr: List<String>, val error: Throwable?)

suspend fun ExpectScope.zoe(
    vararg command: String,
    configDir: String = testConfDir.resolve("config").absolutePath,
    shouldFail: Boolean = false,
    expect: suspend TestContext.(ZoeOutput) -> Unit = {}
): ZoeOutput {
    val mockConsole = MockConsole()
    val fullCommand = listOf("--config-dir", configDir, "-o", "json") + command.toList()
    val res = withZoe(customizeContext = { console = mockConsole }) {
        logger.info("running command: $fullCommand")

        loadKoinModules(testModule)

        it
            .runCatching { parse(fullCommand) }
            .mapCatching {
                logger.info("command succeeded: ${mockConsole.stdout}")
                ZoeOutput(
                    stdout = mockConsole.stdout.firstOrNull()?.toJsonNode(),
                    stderr = mockConsole.stderr.toList(),
                    error = null
                )
            }
            .getOrElse { err ->
                logger.error("zoe failed", err)
                ZoeOutput(
                    stdout = NullNode.instance,
                    stderr = emptyList(),
                    error = err
                )
            }
    }

    expect("command '$fullCommand' should return correct result") {
        if (!shouldFail) {
            res.error shouldBe null
        }
        expect(res)
    }

    return res
}

private val testModule = module {
    single<((EnvConfig) -> EnvConfig)> { ::customizeEnvConfigWithTestContainerAddresses }
}

private fun customizeEnvConfigWithTestContainerAddresses(envConfig: EnvConfig): EnvConfig {
    val clusters = envConfig.clusters.mapValues { cluster ->
        cluster.value.copy(
            registry = TestcontainersContext.schemaRegistry.url,
            props = cluster.value.props.let { props ->
                props.toMutableMap().apply {
                    put("bootstrap.servers", TestcontainersContext.kafka.bootstrapServers)
                }
            }
        )
    }

    return envConfig.copy(clusters = clusters)
}

val testConfDir = File("testResources/env")
