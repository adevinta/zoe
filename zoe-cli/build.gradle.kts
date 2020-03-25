import com.fasterxml.jackson.databind.ObjectMapper
import org.beryx.runtime.JPackageImageTask
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.time.LocalDateTime

plugins {
    application
    id("org.beryx.runtime") version "1.8.0"
    id("com.google.cloud.tools.jib")
}

application {
    mainClassName = "com.adevinta.oss.zoe.cli.MainKt"
    applicationDefaultJvmArgs = listOf("-client")
    applicationName = "zoe"
}

mapOf(
    "zip" to Zip::class,
    "tar" to Tar::class
).forEach { (archiveType, archiveClass) ->
    tasks.register("${archiveType}DistWithoutJdk", archiveClass) {
        val installDistTask = tasks.getByName<Sync>("installDist")

        val outputDir = findProperty("distWithoutJdk.outputDir") ?: "$buildDir/distWithoutJdk"

        dependsOn(installDistTask)
        from(installDistTask.destinationDir)

        archiveFileName.set("zoe-${project.version}.${archiveType}")
        destinationDirectory.set(file(outputDir))

        into("zoe")
    }
}

tasks.register<Zip>("zipJpackageImage") {
    val jPackageImageTask = tasks.getByName<JPackageImageTask>("jpackageImage")

    val outputDir = findProperty("$name.outputDir") ?: "$buildDir/jpackageImageZip"
    val suffix = findProperty("$name.suffix") ?: ""

    dependsOn(jPackageImageTask)
    from(jPackageImageTask.distDir.asFile)

    archiveFileName.set("zoe-${project.version}-with-jdk${suffix}.zip")
    destinationDirectory.set(file(outputDir))

    into("zoe")
}

runtime {
    jpackage {
        imageName = "zoe"

        findProperty("jpackage.installerType")?.toString()?.run {
            installerType = this
        }

        findProperty("jpackage.output")?.let { file(it.toString()) }?.run {
            imageOutputDir = this
            installerOutputDir = this
        }
    }
}

jib {

    from {
        image = "gcr.io/distroless/java:11"
    }

    to {
        image = "adevinta/zoe-cli"
        tags = setOf(project.version.toString(), "latest")
    }

    container {
        jvmFlags = listOf("-client")
        mainClass = "com.adevinta.oss.zoe.cli.MainKt"
    }
}

dependencies {
    implementation(project(":zoe-service"))
    implementation(project(":zoe-core"))

    implementation("com.amazonaws:aws-java-sdk-lambda:1.11.580")
    implementation("com.amazonaws:aws-java-sdk-iam:1.11.580")
    implementation("com.amazonaws:aws-java-sdk-cloudformation:1.11.580")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.2")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.7")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.7")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.7.0.202003090808-r")

    implementation("org.koin:koin-core:2.0.1")
    implementation("com.jakewharton.picnic:picnic:0.2.0")
    implementation("com.github.ajalt:clikt:2.5.0")
    implementation("com.github.ajalt:mordant:1.2.1")
    implementation("org.slf4j:slf4j-log4j12:1.7.26")
    implementation("log4j:log4j:1.2.17")

    testImplementation(group = "junit", name = "junit", version = "4.12")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:2.0.8")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:2.0.8")
}

tasks {
    val generateVersionFile by registering {
        val output = buildDir.resolve("resources/main/version.json")
        outputs.file(output)

        doLast {
            output.writeText(
                ObjectMapper().writeValueAsString(
                    mapOf(
                        "projectVersion" to project.version,
                        "buildTimestamp" to LocalDateTime.now().toString(),
                        "createdBy" to "Gradle ${gradle.gradleVersion}",
                        "buildJdk" to with(System.getProperties()) {
                            "${get("java.version")} (${get("java.vendor")} ${get("java.vm.version")})"
                        },
                        "buildOS" to with(System.getProperties()) {
                            "${get("os.name")} ${get("os.arch")} ${get("os.version")}"
                        }
                    )
                )
            )
        }
    }

    val processResources by getting(ProcessResources::class) {
        dependsOn(generateVersionFile)
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    test {
        useJUnitPlatform {
            includeEngines("spek2")
        }
    }
}

sourceSets {
    main {
        resources.srcDir("resources")
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDir("src")
        }
    }

    test {
        resources.srcDir("testResources")
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDir("test")
        }
    }
}