import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    id("com.google.cloud.tools.jib")
    id("com.github.johnrengelman.shadow")
}

jib {

    to {
        image = "adevinta/zoe-core"
        tags = setOf(project.version.toString(), "latest")
    }

    container {
        jvmFlags = listOf("-client")
        mainClass = "com.adevinta.oss.zoe.core.MainKt"
    }
}

dependencies {
    implementation("com.amazonaws:aws-lambda-java-core:1.1.0")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.7")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.confluent:kafka-avro-serializer:5.2.2")

    implementation("org.slf4j:slf4j-log4j12:1.7.26")
    implementation("log4j:log4j:1.2.17")
    implementation("org.apache.kafka:kafka-clients:2.3.1")
    implementation("org.apache.avro:avro-compiler:1.8.2")
    implementation("com.google.guava:guava:28.0-jre")

    implementation(group = "io.burt", name = "jmespath-jackson", version = "0.2.0")

    testImplementation(group = "junit", name = "junit", version = "4.12")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:2.0.8")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:2.0.8")

    // spek requires kotlin-reflect, can be omitted if already in the classpath
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-reflect")
}

// setup the test task
tasks.test {
    useJUnitPlatform {
        includeEngines("spek2")
    }
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
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
