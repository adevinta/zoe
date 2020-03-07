dependencies {
    implementation(project(":zoe-core"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.2")

    implementation("com.amazonaws:aws-java-sdk-s3:1.11.580")
    implementation("com.amazonaws:aws-java-sdk-lambda:1.11.580")
    implementation("com.schibsted.security:strongbox-sdk:0.2.13")


    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.slf4j:slf4j-log4j12:1.7.26")
    implementation("log4j:log4j:1.2.17")


    implementation("io.fabric8:kubernetes-client:4.7.1")

    testImplementation(group = "junit", name = "junit", version = "4.12")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:2.0.8")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:2.0.8")
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions {
        freeCompilerArgs = listOf("-XXLanguage:+InlineClasses")
    }
}

tasks.compileTestKotlin {
    kotlinOptions.jvmTarget = "1.8"
}

sourceSets {
    main {
        resources.srcDir("resources")
        withConvention(org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet::class) {
            kotlin.srcDir("src")
        }
    }

    test {
        resources.srcDir("testResources")
        withConvention(org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet::class) {
            kotlin.srcDir("test")
        }
    }
}
