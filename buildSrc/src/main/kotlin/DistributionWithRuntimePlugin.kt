package com.adevinta.oss.gradle.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.internal.plugins.DefaultTemplateBasedStartScriptGenerator
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.application.CreateStartScripts
import java.net.URL

open class DistributionWithRuntimePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // TODO: apply the application plugin and the shadow jar plugin

        val extension = project.extensions.create(
            "distributionWithRuntime",
            DistributionWithRuntimeExtension::class.java
        )

        val distributions = project.extensions.getByName("distributions") as DistributionContainer

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

        runtimeDistribTasks.forEach { task ->
            task.doFirst { project.delete(it.outputs.files) }
        }

        project.afterEvaluate {
            withRuntimeDistribution.contents { content ->
                content.from(extension.jreDir) { jre -> jre.into("runtime") }
                content.with(distributions.getByName(extension.baseDistribution.get()).contents)
                content.exclude("**/*.bat")
            }

            runtimeDistribTasks.forEach {
                extension.dependencies.get().forEach { dep -> it.dependsOn(dep) }
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