package com.adevinta.oss.zoe.cli

import com.adevinta.oss.zoe.core.utils.logger
import com.github.ajalt.clikt.output.CliktConsole
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.ExpectScope
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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

data class ZoeOutput(val stdout: List<String>, val stderr: List<String>, val error: Throwable?)

@FlowPreview
@ExperimentalCoroutinesApi
suspend fun ExpectScope.zoe(
    vararg command: String,
    configDir: String = testConfDir.resolve("config").absolutePath
): ZoeOutput {
    val mockConsole = MockConsole()
    val res = withZoe(customizeContext = { console = mockConsole }) {
        val fullCommand = listOf("--config-dir", configDir) + command.toList()
        logger.info("running command: $fullCommand")
        it.runCatching { parse(fullCommand) }.fold(
            onFailure = { err ->
                logger.error("zoe failed", err)
                ZoeOutput(
                    stdout = emptyList(),
                    stderr = emptyList(),
                    error = err
                )
            },
            onSuccess = {
                logger.info("command succeeded: ${mockConsole.stdout}")
                ZoeOutput(
                    stdout = mockConsole.stdout.toList(),
                    stderr = mockConsole.stderr.toList(),
                    error = null
                )
            }
        )
    }

    expect("command shouldn't fail") {
        res.error shouldBe null
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
