// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.runners

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.server.mock.KubernetesServer
import org.junit.Assert
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.Executors

object KubernetesRunnerTest : Spek({

    describe("KubernetesRunner pod generation") {

        val executor = Executors.newSingleThreadExecutor()
        val server = KubernetesServer(false, true)

        beforeGroup {
            server.before()
        }

        afterGroup {
            server.after()
            executor.shutdown()
        }

        it("should set serviceAccountName when provided in configuration") {
            val client: NamespacedKubernetesClient = server.client.inNamespace("test")
            val serviceAccountName = "my-service-account"

            val config = KubernetesRunner.Config(
                deletePodsAfterCompletion = false,
                zoeImage = "wlezzar/zoe:test",
                cpu = "1",
                memory = "512M",
                timeoutMs = 60000L,
                annotations = emptyMap(),
                serviceAccountName = serviceAccountName
            )

            val runner = KubernetesRunner(
                name = "test-runner",
                client = client,
                executor = executor,
                closeClientAtShutdown = false,
                configuration = config
            )

            // Use reflection to access the private generatePodObject method
            val method = KubernetesRunner::class.java.getDeclaredMethod(
                "generatePodObject",
                String::class.java,
                Map::class.java,
                List::class.java
            )
            method.isAccessible = true

            val pod = method.invoke(
                runner,
                "wlezzar/zoe:test",
                emptyMap<String, String>(),
                listOf("arg1", "arg2")
            ) as Pod

            Assert.assertEquals(
                "serviceAccountName should be set in pod spec",
                serviceAccountName,
                pod.spec.serviceAccountName
            )

            runner.close()
        }

        it("should not set serviceAccountName when not provided in configuration") {
            val client: NamespacedKubernetesClient = server.client.inNamespace("test")

            val config = KubernetesRunner.Config(
                deletePodsAfterCompletion = false,
                zoeImage = "wlezzar/zoe:test",
                cpu = "1",
                memory = "512M",
                timeoutMs = 60000L,
                annotations = emptyMap(),
                serviceAccountName = null
            )

            val runner = KubernetesRunner(
                name = "test-runner",
                client = client,
                executor = executor,
                closeClientAtShutdown = false,
                configuration = config
            )

            // Use reflection to access the private generatePodObject method
            val method = KubernetesRunner::class.java.getDeclaredMethod(
                "generatePodObject",
                String::class.java,
                Map::class.java,
                List::class.java
            )
            method.isAccessible = true

            val pod = method.invoke(
                runner,
                "wlezzar/zoe:test",
                emptyMap<String, String>(),
                listOf("arg1", "arg2")
            ) as Pod

            Assert.assertNull(
                "serviceAccountName should be null when not configured",
                pod.spec.serviceAccountName
            )

            runner.close()
        }
    }
})
