package com.adevinta.oss.zoe.cli

import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import com.github.ajalt.clikt.output.CliktConsole
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.scopes.ExpectScope
import io.kotest.core.test.TestContext
import io.kotest.matchers.shouldBe
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
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
    expect: suspend TestContext.(ZoeOutput) -> Unit = {}
): ZoeOutput {
    val mockConsole = MockConsole()
    val fullCommand = listOf("--config-dir", configDir, "-o", "json") + command.toList()
    val res = withZoe(customizeContext = { console = mockConsole }) {
        logger.info("running command: $fullCommand")
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
        res.error shouldBe null
        expect(res)
    }

    return res
}

val testConfDir = File("testResources/env")

fun testDockerCompose() =
    DockerCompose(testConfDir.resolve("docker-compose.yml")).apply {
        waitingFor("broker", Wait.forListeningPort())
        waitingFor("schema-registry", Wait.forHttp("/subjects").forStatusCode(200))
    }

typealias DockerCompose = DockerComposeContainer<Nothing>

class DockerComposeEnv(private val env: DockerCompose) : TestListener {
    override suspend fun beforeSpec(spec: io.kotest.core.spec.Spec) = env.start()
    override suspend fun afterSpec(spec: io.kotest.core.spec.Spec) = env.stop()
}