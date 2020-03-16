package com.adevinta.oss.zoe.service

import com.adevinta.oss.zoe.core.functions.TopicPartitionOffset
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.adevinta.oss.zoe.service.config.Cluster
import com.adevinta.oss.zoe.service.config.InMemoryConfigStore
import com.adevinta.oss.zoe.service.secrets.NoopSecretsProvider
import com.adevinta.oss.zoe.service.simulator.simulator
import com.adevinta.oss.zoe.service.storage.LocalFsKeyValueStore
import com.google.common.io.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@FlowPreview
@ExperimentalCoroutinesApi
object ZoeServiceTest : Spek({

    describe("Service can read data using several subscription strategies") {

        val runner by memoized {
            simulator(readSpeedPerPoll = 2) {
                cluster("local") {
                    topic(name = "input", partitions = 5) {
                        message("""{"id": 1}""".toJsonNode(), key = "0-0", partition = 0, offset = 0, timestamp = 0)
                        message("""{"id": 2}""".toJsonNode(), key = "0-1", partition = 0, offset = 1, timestamp = 1)
                        message("""{"id": 3}""".toJsonNode(), key = "0-2", partition = 0, offset = 2, timestamp = 3)
                        message("""{"id": 5}""".toJsonNode(), key = "1-0", partition = 1, offset = 0, timestamp = 1)
                        message("""{"id": 6}""".toJsonNode(), key = "1-1", partition = 1, offset = 1, timestamp = 2)
                        message("""{"id": 6}""".toJsonNode(), key = "2-0", partition = 2, offset = 0, timestamp = 1)
                    }
                }
            }
        }

        val service by memoized {
            ZoeService(
                configStore = InMemoryConfigStore(
                    filters = emptyMap(),
                    clusters = mapOf(
                        "cluster" to Cluster(registry = null, props = mapOf("bootstrap.servers" to "local"))
                    )
                ),
                runner = runner,
                storage = LocalFsKeyValueStore(Files.createTempDir()),
                secrets = NoopSecretsProvider
            )
        }

        describe("Full topic read from beginning") {

            lateinit var readResponse: List<RecordOrProgress>

            beforeEachTest {
                runBlocking {
                    readResponse =
                        service
                            .readWithDefaultValues(cluster = "cluster", topic = "input", from = ConsumeFrom.Earliest)
                            .toList()
                }
            }

            it("receives all the records") {
                Assert.assertEquals(
                    """didn't receive the right number of records:
                        | - received: $readResponse
                        | - expected: ${runner.state.clusters.first().topics.first().messages.values}
                    """.trimMargin(),
                    6,
                    readResponse.filterIsInstance<RecordOrProgress.Record>().size
                )
            }

            it("receives a partition progress") {
                Assert.assertTrue(readResponse.filterIsInstance<RecordOrProgress.Progress>().isNotEmpty())
            }
        }

        describe("Can read from a specific timestamp") {

            lateinit var readResponse: List<RecordOrProgress>

            beforeEachTest {
                runBlocking {
                    readResponse =
                        service
                            .readWithDefaultValues(
                                cluster = "cluster",
                                topic = "input",
                                from = ConsumeFrom.Timestamp(2)
                            )
                            .toList()
                }
            }

            it("receives only records new than the requested timestamp") {
                Assert.assertEquals(2, readResponse.filterIsInstance<RecordOrProgress.Record>().size)
            }
        }

        describe("Can request offsets from timestamps") {
            val ts = 2L
            val topic = "input"

            lateinit var response: List<TopicPartitionOffset>

            beforeEachTest {
                runBlocking {
                    response = service.offsetsFromTimestamp(
                        cluster = "cluster",
                        topic = TopicAliasOrRealName(topic),
                        timestamp = ts
                    )
                }
            }

            it("receives correct response") {
                Assert.assertEquals(
                    setOf(
                        TopicPartitionOffset(topic = topic, partition = 0, offset = 2),
                        TopicPartitionOffset(topic = topic, partition = 1, offset = 1),
                        TopicPartitionOffset(topic = topic, partition = 2, offset = null),
                        TopicPartitionOffset(topic = topic, partition = 3, offset = null),
                        TopicPartitionOffset(topic = topic, partition = 4, offset = null),
                        TopicPartitionOffset(topic = topic, partition = 5, offset = null)
                    ),
                    response.toSet()
                )
            }

        }

    }
})

@FlowPreview
@ExperimentalCoroutinesApi
private fun ZoeService.readWithDefaultValues(
    cluster: String,
    topic: String,
    from: ConsumeFrom,
    filters: List<String> = emptyList(),
    query: String? = null,
    parallelism: Int = 1,
    numberOfRecordsPerBatch: Int = 10,
    timeoutPerBatch: Long = 10000,
    formatter: String = "raw",
    stopCondition: StopCondition = StopCondition.TopicEnd
) = read(
    cluster = cluster,
    topic = TopicAliasOrRealName(topic),
    from = from,
    filters = filters,
    query = query,
    parallelism = parallelism,
    numberOfRecordsPerBatch = numberOfRecordsPerBatch,
    timeoutPerBatch = timeoutPerBatch,
    formatter = formatter,
    stopCondition = stopCondition
)
