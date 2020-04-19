package com.adevinta.oss.gradle.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

open class JPackageTask : DefaultTask() {

    @Input
    val appVersion: Property<String> = project.objects.property(String::class.java)

    @Input
    val imageName: Property<String> = project.objects.property(String::class.java)

    @Input
    val installerName: Property<String> = project.objects.property(String::class.java)

    @Input
    val libs: Property<String> = project.objects.property(String::class.java)

    @Input
    val mainClass: Property<String> = project.objects.property(String::class.java)

    @Input
    val mainJar: Property<String> = project.objects.property(String::class.java)

    @Input
    val installType: Property<String> = project.objects.property(String::class.java)

    @OutputDirectory
    val output: DirectoryProperty = project.objects.directoryProperty()

    @TaskAction
    fun execute() {
        val result = project.exec {
            it.executable("jpackage")
            it.args(
                "-i", libs.get(),
                "-n", imageName.get(),
                "--main-class", mainClass.get(),
                "--main-jar", mainJar.get(),
                "--type", installType.get(),
                "--dest", output.asFile.get().absolutePath,
                "--app-version", appVersion.get()
            )
        }
        result.assertNormalExitValue()
    }

}
