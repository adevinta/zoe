// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.runners

import com.adevinta.oss.zoe.core.FailureResponse
import com.adevinta.oss.zoe.core.utils.*
import com.adevinta.oss.zoe.service.utils.loadFileFromResources
import com.adevinta.oss.zoe.service.utils.userError
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.client.*
import io.fabric8.kubernetes.client.dsl.Watchable
import io.fabric8.kubernetes.client.dsl.internal.ExecWebSocketListener
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.function.Supplier

/**
 * A [ZoeRunner] implementation that launches kafka clients inside kubernetes pods.
 */
@ExperimentalCoroutinesApi
class KubernetesRunner(
    override val name: String,
    private val client: NamespacedKubernetesClient,
    private val executor: ExecutorService,
    private val closeClientAtShutdown: Boolean,
    private val configuration: Config
) : ZoeRunner {

    constructor(
        name: String,
        configuration: Config,
        namespace: String,
        context: String?,
        executor: ExecutorService
    ) : this(
        name = name,
        client = kotlin.run {
            val client =
                if (context != null) DefaultKubernetesClient(io.fabric8.kubernetes.client.Config.autoConfigure(context))
                else DefaultKubernetesClient()

            client.inNamespace(namespace)
        },
        executor = executor,
        closeClientAtShutdown = true,
        configuration = configuration
    )

    data class Config(
        val deletePodsAfterCompletion: Boolean,
        val zoeImage: String,
        val cpu: String,
        val memory: String,
        val timeoutMs: Long?
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val pendingResponses = mutableMapOf<String, CompletableDeferred<String>>()
    private val responseFile = "/output/response.txt"
    private val labels = mapOf(
        "owner" to "zoe",
        "runnerId" to uuid()
    )

    private val watcher = with(scope) {
        launch(start = CoroutineStart.LAZY) {
            client.pods().withLabels(labels).watchContinuously().collect { (action, resource) ->
                logger.debug("received event '$action' with pod: ${resource?.toJsonString()}")

                val podResult =
                    resource?.metadata?.name?.let { pendingResponses.getOrPut(it) { CompletableDeferred() } }
                val podStatus = resource?.status?.phase
                val zoeContainerStatus = resource?.status?.containerStatuses?.find { it.name == "zoe" }?.state

                when {

                    podResult == null -> logger.warn("received event for a pod not waiting for a response: $resource")

                    podResult.isCompleted -> logger.debug("discarding event as pod's response has already been fullfilled")

                    zoeContainerStatus?.waiting?.reason == "ImagePullBackOff" ->
                        // Means the container image is not pullable. Fail early!
                        podResult.completeExceptionally(
                            ZoeRunnerException(
                                "Zoe image does not seem to be pullable. Pod:${resource.toJsonString()}",
                                cause = null,
                                runnerName = name,
                                remoteStacktrace = null
                            )
                        )

                    podStatus == "Pending" -> logger.debug("pod is spinning up...")

                    zoeContainerStatus == null -> podResult.completeExceptionally(
                        ZoeRunnerException(
                            "State for container 'zoe' not found. Pod:${resource.toJsonString()}",
                            cause = null,
                            runnerName = name,
                            remoteStacktrace = null
                        )
                    )

                    zoeContainerStatus.terminated != null ->
                        try {
                            // pulling response from the container
                            val response =
                                client
                                    .pods()
                                    .withName(resource.metadata.name)
                                    .inContainer("tailer")
                                    .file(responseFile)
                                    .runSuspending { read().use { it.readAllBytes().toString(Charsets.UTF_8) } }

                            when (zoeContainerStatus.terminated.exitCode) {
                                0 -> podResult.complete(response)
                                else -> podResult.completeExceptionally(
                                    response
                                        .runCatching { parseJson<FailureResponse>() }
                                        .map { ZoeRunnerException.fromRunFailureResponse(it, name) }
                                        .getOrElse {
                                            ZoeRunnerException(
                                                message = "container exit status: ${zoeContainerStatus.terminated}",
                                                cause = null,
                                                runnerName = name,
                                                remoteStacktrace = null
                                            )
                                        }
                                )
                            }
                        } catch (err: Throwable) {
                            podResult.completeExceptionally(err)
                        }

                    else -> logger.debug("zoe container is in: '${zoeContainerStatus.toJsonString()}'")
                }
            }
        }
    }

    override suspend fun launch(function: String, payload: String): String {
        if (!watcher.isActive) watcher.start()

        val pod = generatePodObject(
            image = configuration.zoeImage,
            args = listOf(
                mapOf("function" to function, "payload" to payload.toJsonNode()).toJsonString(),
                responseFile
            )
        )

        val response = pendingResponses.getOrPut(pod.metadata.name) { CompletableDeferred() }
        val timeout = configuration.timeoutMs ?: Duration.ofMinutes(10).toMillis()

        try {
            client.pods().runSuspending { create(pod) }
            return withTimeout(timeout) { response.await() }
        } finally {
            if (configuration.deletePodsAfterCompletion) {
                client
                    .pods()
                    .withName(pod.metadata.name)
                    .withGracePeriod(0)
                    .runSuspending { delete() }
            }
        }

    }

    override fun close() {
        if (closeClientAtShutdown) client.use { doClose() } else doClose()
    }

    private fun doClose() {
        // When closing the kubernetes client, the ExecWebSocketListener may log useless error stack traces about closed
        // sockets. We don't want them to show up to the client at this phase so we increase the logging level.
        LogManager.getLogger(ExecWebSocketListener::class.java)?.level = Level.FATAL
        runBlocking { watcher.cancelAndJoin() }
        scope.cancel()
        if (configuration.deletePodsAfterCompletion) {
            logger.debug("deleting potentially dangling pods...")
            client
                .pods()
                .withLabels(labels)
                .withGracePeriod(0)
                .delete()
        }
    }

    private fun generatePodObject(image: String, args: List<String>): Pod {
        val pod = loadFileFromResources("pod.template.json")?.parseJson<Pod>() ?: userError("pod template not found !")
        return pod.apply {
            metadata.name = "zoe-${UUID.randomUUID()}"
            metadata.labels = labels

            spec.containers.find { it.name == "zoe" }?.apply {
                resources.requests = mapOf(
                    "cpu" to Quantity.parse(configuration.cpu),
                    "memory" to Quantity.parse(configuration.memory)
                )

                setImage(image)
                setArgs(args)
            }
        }
    }

    private suspend fun <T : Any, R> T.runSuspending(action: T.() -> R): R =
        CompletableFuture
            .supplyAsync(Supplier { action() }, executor)
            .await()

}

data class WatcherEvent(val action: Watcher.Action, val resource: Pod?)

@ExperimentalCoroutinesApi
fun Watchable<Watch, Watcher<Pod>>.watchAsFlow(): Flow<WatcherEvent> = callbackFlow {
    val watcher =
        CompletableFuture
            .supplyAsync {
                logger.debug("registering a watch...")
                watch(object : Watcher<Pod> {
                    override fun onClose(cause: KubernetesClientException?) {
                        close(cause)
                    }

                    override fun eventReceived(action: Watcher.Action, resource: Pod?) {
                        if (!isClosedForSend) {
                            sendBlocking(WatcherEvent(action, resource))
                        }
                    }
                })
            }
            .await()

    awaitClose {
        watcher.use {
            logger.debug("closing the watcher (already collected events may still be received over the channel)")
        }
    }
}


@ExperimentalCoroutinesApi
fun Watchable<Watch, Watcher<Pod>>.watchContinuously(): Flow<WatcherEvent> = flow {
    while (true) {
        emitAll(watchAsFlow())
    }
}