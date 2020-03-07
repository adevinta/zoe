import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    application
    id("org.beryx.runtime") version "1.8.0"
}

application {
    mainClassName = "com.adevinta.oss.zoe.cli.MainKt"
    executableDir = "zoe-cli"
    applicationDefaultJvmArgs = listOf("-client")
    applicationName = "zoe"
}

runtime {
    addOptions("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages", "--strip-native-commands")
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

// setup the test task
tasks.test {
    useJUnitPlatform {
        includeEngines("spek2")
    }
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.compileTestKotlin {
    kotlinOptions.jvmTarget = "1.8"
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