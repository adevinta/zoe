package com.adevinta.oss.gradle.plugins

import org.apache.commons.codec.digest.DigestUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URL
import java.nio.file.Files

open class DownloadJreTask : DefaultTask() {
    enum class ArchiveType { Zip, Tar, TarGz }

    @Input
    val jreUrl: Property<String> = project.objects.property(String::class.java)

    @Input
    val archiveType: Property<ArchiveType> =
        project.objects.property(ArchiveType::class.java).convention(jreUrl.map(::inferArchiveType))

    @OutputDirectory
    val jre: DirectoryProperty = project.objects
        .directoryProperty()
        .convention(jreUrl.flatMap {
            project.layout.buildDirectory.dir("downloadedJre/${DigestUtils.sha256Hex(it.toString())}")
        })

    @TaskAction
    fun execute() {
        // delete old directory if exist
        project.delete(jre)

        Files.createTempFile(null, null).toFile().useThenDelete { tempDownload ->
            tempDownload.outputStream().use { out -> URL(jreUrl.get()).openStream().use { it.copyTo(out) } }

            val archive = when (archiveType.get()) {
                ArchiveType.TarGz -> project.tarTree(project.resources.gzip(tempDownload))
                ArchiveType.Tar -> project.tarTree(tempDownload)
                ArchiveType.Zip -> project.zipTree(tempDownload)
                else -> throw IllegalArgumentException("unknown archive type: $jreUrl")
            }

            Files.createTempDirectory(null).toFile().useThenDelete { tempUnarchive ->
                project.copy { it.from(archive).into(tempUnarchive) }
                project.copy { it.from(findJavaHome(tempUnarchive)).into(jre) }
            }
        }
    }
}

private fun <T> File.useThenDelete(action: (File) -> T): T {
    try {
        return action(this)
    } finally {
        deleteRecursively()
    }
}

private fun findJavaHome(root: File): File =
    findJavaHomeOrNull(root) ?: throw IllegalArgumentException("Java home not found in directory: $root")

private fun findJavaHomeOrNull(root: File): File? =
    if (hasJavaHomeLayout(root)) root
    else root.listFiles()
        ?.asSequence()
        ?.map { findJavaHomeOrNull(it) }
        ?.filterNotNull()
        ?.firstOrNull()

private fun hasJavaHomeLayout(file: File): Boolean = file.listFiles()
    ?.let { files ->
        val binDir =
            files.find { it.name == "bin" && it.isDirectory && it.list()?.contains("java") == true }

        val libDir =
            files.find { it.name == "lib" && it.isDirectory }

        binDir != null && libDir != null
    }
    ?: false


private fun inferArchiveType(url: String): DownloadJreTask.ArchiveType = when {
    url.endsWith(".tar.gz") -> DownloadJreTask.ArchiveType.TarGz
    url.endsWith(".tar") -> DownloadJreTask.ArchiveType.Tar
    url.endsWith(".zip") -> DownloadJreTask.ArchiveType.Zip
    else -> throw IllegalArgumentException("unknown archive type for: $url")
}

data class Runtimes(val runtimes: List<Runtime>)
data class Runtime(val version: String, val platform: Platform, val distributions: Map<String, String>)
enum class Platform { MacOs, Linux, Windows }
