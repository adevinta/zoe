// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

dependencies {
    api(project(":zoe-core"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.6")

    implementation("com.amazonaws:aws-java-sdk-s3:1.11.779")
    implementation("com.amazonaws:aws-java-sdk-lambda:1.11.779")
    implementation("com.schibsted.security:strongbox-sdk:0.2.21")
    implementation("com.amazonaws:aws-java-sdk-secretsmanager:1.11.779")


    implementation("org.slf4j:slf4j-log4j12:1.7.30")
    implementation("log4j:log4j:1.2.17")

    implementation("io.fabric8:kubernetes-client:4.10.1")

    testImplementation(group = "junit", name = "junit", version = "4.12")
    testImplementation("org.testcontainers:testcontainers:1.14.1")
    testImplementation("org.testcontainers:kafka:1.14.1")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:2.0.10")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:2.0.10")
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
