package com.adevinta.oss.zoe.cli

import com.adevinta.oss.zoe.core.functions.DescribeTopicResponse
import com.adevinta.oss.zoe.core.utils.parseJson
import com.adevinta.oss.zoe.core.utils.toJsonNode
import io.kotest.core.spec.style.ExpectSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldNotBeEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.util.*

class MainTest : ExpectSpec({

    context("Testing CLI with Avro and a local runner") {

        val topic = "topic-${UUID.randomUUID()}"

        context("a new topic is created") {

            zoe("topics", "create", topic) { it.stdout shouldBe """{"done": true}""".toJsonNode() }

            zoe("topics", "list") { it.stdout?.parseJson<List<String>>()?.shouldContain(topic) }

            zoe("topics", "describe", topic) {
                it.stdout?.parseJson<DescribeTopicResponse>()?.should { topicDescription ->
                    topicDescription.topic.shouldBe(topic)
                    topicDescription.partitions.shouldBe(listOf(0))
                    topicDescription.internal.shouldBeFalse()
                    topicDescription.config.shouldNotBeEmpty()
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
                ) { it.stdout?.has("id") shouldBe true }

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
                            "topics",
                            "consume",
                            topic,
                            "--from", "PT5m",
                            "--timeout-per-batch", "3000",
                            "-n", "1000"
                        ) { read ->
                            val numberOfAdsConsumed = read.stdout?.size()
                            val numberOfAdsProduced = insertResult.stdout?.get("produced")?.size()

                            numberOfAdsConsumed shouldNotBe null
                            numberOfAdsConsumed shouldBe numberOfAdsProduced
                        }
                    }
                }
            }
        }

        context("deleting some avro schemas/subject") {

            context("adding a second version of the schema") {
                zoe(
                    "schemas", "deploy",
                    "--avdl",
                    "--from-file", testConfDir.resolve("schema-v2.avdl").absolutePath,
                    "--name", "CatFact",
                    "--strategy", "topic",
                    "--topic", topic,
                    "--suffix", "value"
                ) { it.stdout?.has("id") shouldBe true }
            }

            context("deleting the first version of the avro schema") {
                zoe(
                    "schemas", "delete",
                    "$topic-value",
                    "--schema-version", "1"
                ) {
                    it.stdout?.has("type") shouldBe true
                    it.stdout?.has("deletedVersions") shouldBe true
                    it.stdout?.has("subject") shouldBe true
                    it.stdout?.has("hardDelete") shouldBe true
                }
            }

            context("describing avro schema(s)") {
                zoe(
                    "schemas", "describe",
                    "$topic-value"
                ) {
                    it.stdout?.has("versions") shouldBe true
                    it.stdout?.has("latest") shouldBe true
                }
            }

            context("deleting an avro subject permanently") {
                zoe(
                    "schemas", "delete",
                    "$topic-value",
                    "--hard"
                ) {
                    it.stdout?.has("type") shouldBe true
                    it.stdout?.has("deletedVersions") shouldBe true
                    it.stdout?.has("subject") shouldBe true
                    it.stdout?.has("hardDelete") shouldBe true
                }
            }

            context("describing deleted avro schema") {
                zoe(
                    "schemas", "describe",
                    "$topic-value",
                    shouldFail = true
                ) {
                    it.error?.cause?.message shouldContain "Subject '$topic-value' not found"
                    it.error?.cause?.message shouldContain "error code: 40401"
                }
            }
        }
    }
})
