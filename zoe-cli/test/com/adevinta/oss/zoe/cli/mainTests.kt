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

            val result = zoe("topics", "create", topic)

            expect("topic is successfully created") {
                result.stdout.size shouldBe 1
                result.stdout.first().toJsonNode() shouldBe """{"done": true}""".toJsonNode()
            }

            context("listing topics") {
                val topics = zoe("topics", "list")

                expect("topics are listed successfully") {
                    topics.stdout.size shouldBe 1
                    topics.stdout.first().parseJson<List<String>>() shouldContain topic
                }
            }

            context("creating an avro schema") {
                val res = zoe(
                    "schemas", "deploy",
                    "--avdl",
                    "--from-file", testConfDir.resolve("schema.avdl").absolutePath,
                    "--name", "CatFact",
                    "--strategy", "topic",
                    "--topic", topic,
                    "--suffix", "value"
                )

                expect("schema is correctly created") {
                    res.stdout.first().parseJson<JsonNode>().has("id")
                }

                context("inserting some data") {
                    val inserted = zoe(
                        "topics",
                        "produce",
                        "--topic", topic,
                        "--from-file", testConfDir.resolve("data.json").absolutePath,
                        "--subject", "$topic-value"
                    )

                    expect("data is successfully inserted") {
                        inserted.error shouldBe null
                    }

                    context("reading data") {
                        val read = zoe(
                            "-o", "json",
                            "topics",
                            "consume",
                            topic,
                            "--from", "PT5m",
                            "--timeout-per-batch", "3000",
                            "-n", "1000"
                        )

                        expect("data can be read correctly") {
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