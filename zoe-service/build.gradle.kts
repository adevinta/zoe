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

    implementation(platform("software.amazon.awssdk:bom:2.15.9"))
    implementation("software.amazon.awssdk:lambda")
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:secretsmanager")

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
        kotlinOptions {
            jvmTarget = "21"
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlinx.coroutines.FlowPreview",
                "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
            )
        }
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "21"
    }
}

sourceSets {
    main {
        resources.srcDir("resources")
        kotlin.srcDir("src")
    }

    test {
        resources.srcDir("testResources")
        kotlin.srcDir("test")
    }
}
