package com.adevinta.oss.zoe.cli

import com.adevinta.oss.zoe.core.functions.GroupOffset
import com.adevinta.oss.zoe.core.functions.ListGroupsResponse
import com.adevinta.oss.zoe.core.utils.parseJson
import com.fasterxml.jackson.databind.node.BooleanNode
import io.kotest.core.spec.style.ExpectSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import java.util.*

class ConsumerGroupsTest : ExpectSpec({

    // spin up docker compose
    listener(DockerComposeEnv(testDockerCompose()))

    context("Testing Consumer groups offsets commands") {
        val topic = "topic-${UUID.randomUUID()}"
        val group = "group-${UUID.randomUUID()}"

        context("inserting data") {
            zoe(
                "schemas", "deploy",
                "--avdl",
                "--from-file", testConfDir.resolve("schema.avdl").absolutePath,
                "--name", "CatFact",
                "--strategy", "topic",
                "--topic", topic,
                "--suffix", "value"
            )

            zoe(
                "topics",
                "produce",
                "--topic", topic,
                "--subject", "$topic-value",
                "--from-file", testConfDir.resolve("data.json").absolutePath,
            )

            // consumer group should not exist yet
            zoe("groups", "list") { res ->
                res.stdout?.parseJson<ListGroupsResponse>()?.groups?.find { it.groupId == group } shouldBe null
            }

            context("setting consumer groups offsets to earliest") {
                zoe("offsets", "set", "--group", group, "--topic", topic, "--earliest") {
                    it.stdout?.get("success") shouldBe BooleanNode.TRUE
                }

                // group should now be visible
                zoe("groups", "list") { res -> res.stdout?.find { it["groupId"]?.asText() == group } != null }

                // offsets should be set to 0
                zoe("offsets", "read", "--group", group) { res ->
                    res.stdout
                        ?.map { it.parseJson<GroupOffset>() }
                        ?.filter { it.topic == topic }
                        ?.forAll { it.currentOffset shouldBe 0L }
                }
            }

            context("setting consumer groups offsets to latest") {
                zoe("offsets", "set", "--group", group, "--topic", topic, "--latest") {
                    it.stdout?.get("success") shouldBe BooleanNode.TRUE
                }

                // offsets should be set to the topic's end offset
                zoe("offsets", "read", "--group", group) { res ->
                    res.stdout
                        ?.map { it.parseJson<GroupOffset>() }
                        ?.filter { it.topic == topic }
                        ?.forAll { it.currentOffset shouldBe it.endOffset }
                }
            }

            context("setting consumer groups offsets to specific offsets") {
                val (partition, offset) = 0 to 10

                zoe(
                    "offsets",
                    "set",
                    "--group", group,
                    "--topic", topic,
                    "--offsets", "$partition=$offset"
                ) { it.stdout?.get("success") shouldBe BooleanNode.TRUE }

                // offset for partition 0 should have the correct value
                zoe("offsets", "read", "--group", group) { res ->
                    res.stdout
                        ?.map { it.parseJson<GroupOffset>() }
                        ?.find { it.topic == topic && it.partition == partition }
                        ?.should { it.currentOffset shouldBe offset }
                }
            }
        }
    }
})

