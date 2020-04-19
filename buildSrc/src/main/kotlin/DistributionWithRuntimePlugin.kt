package com.adevinta.oss.gradle.plugins

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.internal.plugins.DefaultTemplateBasedStartScriptGenerator
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.application.CreateStartScripts
import java.net.URL

open class DistributionWithRuntimePlugin : Plugin<Project> {

    private val runtimes: Runtimes =
        DistributionWithRuntimePlugin::class.java.getResourceAsStream("/jre.json")
            ?.let { ObjectMapper().registerKotlinModule().readValue<Runtimes>(it) }
            ?: throw IllegalArgumentException("runtimes config not found!")


    override fun apply(project: Project) {
        // TODO: apply the application plugin and the shadow jar plugin

        val extension = project.extensions.create(
            "distributionWithRuntime", DistributionWithRuntimeExtension::class.java
        )

        val distributions = project.extensions.getByName("distributions") as DistributionContainer

        val baseDistribution = extension.baseDistribution.flatMap(distributions::named)
        val withRuntimeDistribution = distributions.create("withRuntime") {
            it.distributionBaseName.convention("zoe-with-runtime")
        }

        val runtimeDistribTasks =
            sequenceOf(
                "installWithRuntimeDist",
                "withRuntimeDistTar",
                "withRuntimeDistZip",
                "assembleWithRuntimeDist"
            ).map { project.tasks.getByName(it) }

        runtimeDistribTasks.forEach { task -> task.doFirst { project.delete(it.outputs.files) } }

        val downloadJreTask = project.tasks.register("downloadJre", DownloadJreTask::class.java) { task ->
            task.jreUrl.set(project.provider {
                val runtimeConfig = extension.runtimeConfig.get()
                runtimes
                    .runtimes
                    .find { it.platform == runtimeConfig.platform && it.version == runtimeConfig.version }
                    ?.distributions
                    ?.get(runtimeConfig.distribution)
                    ?: throw IllegalArgumentException("distribution not found: $runtimeConfig")
            })
        }

        project.afterEvaluate {
            withRuntimeDistribution.contents { content ->
                content.from(downloadJreTask) { jre -> jre.into("runtime") }
                content.with(baseDistribution.get().contents)
                content.exclude("**/*.bat")
            }
        }

        project.gradle.taskGraph.whenReady { taskGraph ->
            val isRuntimeDistrib =
                runtimeDistribTasks.filter { taskGraph.hasTask(it) }.firstOrNull() != null

            project.tasks.withType(CreateStartScripts::class.java) {
                it.inputs.property("isStartScriptWithRuntime", isRuntimeDistrib)
                if (isRuntimeDistrib) {
                    (it.unixStartScriptGenerator as DefaultTemplateBasedStartScriptGenerator).template =
                        project.getTextResource("/unixStartScript.txt")
                    (it.windowsStartScriptGenerator as DefaultTemplateBasedStartScriptGenerator).template =
                        project.getTextResource("/windowsStartScript.txt")
                }
            }
        }
    }

    private fun Project.getTextResource(path: String): TextResource {
        val template: URL =
            DistributionWithRuntimePlugin::class.java.getResource(path)
                ?: throw GradleException("Resource $path not found.")

        return resources.text.fromString(template.readText(Charsets.UTF_8))
    }
}