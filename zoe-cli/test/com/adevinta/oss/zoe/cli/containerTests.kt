package com.adevinta.oss.zoe.cli

import io.kotest.core.spec.style.ExpectSpec
import io.kotest.matchers.shouldBe
import org.testcontainers.containers.GenericContainer
import java.nio.file.Paths

class ContainerTests : ExpectSpec({
    context("Build and test container") {
        context("Build container") {
            val rootDirectory = Paths.get(System.getProperty("user.dir"), "..").toFile()

            val process = ProcessBuilder("./gradlew", "zoe-cli:jibDockerBuild")
                .directory(rootDirectory)
                .inheritIO()
                .start()

            expect("Build process to finish") {
                val exitCode = process.waitFor()
                exitCode shouldBe 0
            }
        }

        context("Test container config command") {
            val container = GenericContainer("adevinta/zoe-cli")
                .withCommand("config", "environments", "list")

            expect("Container to start and run") {
                container.use {
                    container.start()
                    while (container.isRunning) {
                        Thread.sleep(100)
                    }
                    container.logs shouldBe "[]\n"
                }
            }
        }
    }
})