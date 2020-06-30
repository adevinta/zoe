@file:Suppress("NAME_SHADOWING")

package com.adevinta.oss.zoe.cli

import com.adevinta.oss.zoe.core.utils.parseJson
import com.adevinta.oss.zoe.core.utils.toJsonNode
import io.kotest.core.spec.style.ExpectSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import java.util.*

@FlowPreview
@ExperimentalCoroutinesApi
class MainTest : ExpectSpec({

    // spin up docker compose
    listener(DockerComposeEnv(testDockerCompose()))

    context("Testing CLI with Avro and a local runner") {

        context("a new topic is created") {
            val topic = "topic-${UUID.randomUUID()}"

            zoe("topics", "create", topic) { it.stdout shouldBe """{"done": true}""".toJsonNode() }

            zoe("topics", "list") { it.stdout.parseJson<List<String>>() shouldContain topic }

            context("creating an avro schema") {
                zoe(
                    "schemas", "deploy",
                    "--avdl",
                    "--from-file", testConfDir.resolve("schema.avdl").absolutePath,
                    "--name", "CatFact",
                    "--strategy", "topic",
                    "--topic", topic,
                    "--suffix", "value"
                ) { it.stdout.has("id") }

                context("inserting some data") {
                    val insertResult = zoe(
                        "topics",
                        "produce",
                        "--topic", topic,
                        "--from-file", testConfDir.resolve("data.json").absolutePath,
                        "--subject", "$topic-value"
                    )

                    context("reading data") {
                        zoe(
                            "-o", "json",
                            "topics",
                            "consume",
                            topic,
                            "--from", "PT5m",
                            "--timeout-per-batch", "3000",
                            "-n", "1000"
                        ) { read ->
                            val numberOfAdsConsumed = read.stdout.size()
                            val numberOfAdsProduced = insertResult.stdout["produced"]?.size()

                            numberOfAdsConsumed shouldBe numberOfAdsProduced
                        }
                    }
                }
            }
        }
    }
})