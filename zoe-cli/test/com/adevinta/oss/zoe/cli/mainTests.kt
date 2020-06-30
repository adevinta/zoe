@file:Suppress("NAME_SHADOWING")

package com.adevinta.oss.zoe.cli

import com.adevinta.oss.zoe.core.utils.parseJson
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.fasterxml.jackson.databind.JsonNode
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

            zoe("topics", "create", topic) {
                it.stdout.size shouldBe 1
                it.stdout.first().toJsonNode() shouldBe """{"done": true}""".toJsonNode()
            }

            context("listing topics") {
                zoe("topics", "list") {
                    it.stdout.size shouldBe 1
                    it.stdout.first().parseJson<List<String>>() shouldContain topic
                }
            }

            context("creating an avro schema") {
                zoe(
                    "schemas", "deploy",
                    "--avdl",
                    "--from-file", testConfDir.resolve("schema.avdl").absolutePath,
                    "--name", "CatFact",
                    "--strategy", "topic",
                    "--topic", topic,
                    "--suffix", "value"
                ) {
                    it.stdout.first().parseJson<JsonNode>().has("id")
                }

                context("inserting some data") {
                    val inserted = zoe(
                        "topics",
                        "produce",
                        "--topic", topic,
                        "--from-file", testConfDir.resolve("data.json").absolutePath,
                        "--subject", "$topic-value"
                    ) {
                        it.error shouldBe null
                    }

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
                            read.stdout.size shouldBe 1
                            read.stdout.first().parseJson<List<JsonNode>>().size shouldBe inserted.stdout.first()
                                .parseJson<JsonNode>().get("produced").size()
                        }
                    }
                }
            }
        }
    }
})