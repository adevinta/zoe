package com.adevinta.oss.zoe.cli

import com.adevinta.oss.zoe.core.utils.logger
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

private const val kafkaVersion = "5.5.1"

/**
 * Context which holds the started containers for easy access in application setup customizers.
 */
object TestcontainersContext {
    lateinit var kafka: KafkaContainer
        private set

    lateinit var schemaRegistry: SchemaRegistryContainer
        private set

    /**
     * Automatically start containers during app start.
     */
    init {
        start()
    }

    private fun start() {
        val network = Network.newNetwork()
        val logConsumer = Slf4jLogConsumer(logger)

        kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka").withTag(kafkaVersion))
            .withNetwork(network)
            .withLogConsumer(logConsumer)
            .apply { start() }

        schemaRegistry = SchemaRegistryContainer(kafka)
            .withLogConsumer(logConsumer)
            .apply { start() }
    }
}

class SchemaRegistryContainer(kafka: KafkaContainer) :
    GenericContainer<SchemaRegistryContainer>("confluentinc/cp-schema-registry:$kafkaVersion") {

    init {
        withNetwork(kafka.network)
        withEnv(
            "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS",
            "PLAINTEXT://" + TestcontainersContext.kafka.networkAliases[0] + ":9092"
        )
        withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
        withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
            .withExposedPorts(8081)
    }

    val url
        get() = "http://localhost:" + getMappedPort(8081)
}
