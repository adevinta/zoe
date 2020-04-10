// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

import java.net.URI

buildscript {

    repositories {
        mavenLocal()
        jcenter()
    }

    dependencies {
        classpath("com.github.jengelman.gradle.plugins:shadow:4.0.4")
    }
}

plugins {
    java
    kotlin("jvm") version "1.3.71" apply false
    id("com.github.johnrengelman.shadow") version "4.0.4" apply false
    id("com.google.cloud.tools.jib") version "2.1.0" apply false
    id("com.palantir.git-version") version "0.12.2"
}

val versionDetails: groovy.lang.Closure<com.palantir.gradle.gitversion.VersionDetails> by extra

allprojects {
    group = "com.adevinta.oss"

    /**
     * Assumes tags have the format of v*.*.*
     * strips the starting 'v' character to make the version number compatible with '.deb' packages
     */
    version = versionDetails().lastTag.replace("^v".toRegex(), "")

    repositories {
        mavenLocal()
        jcenter()
        mavenCentral()
        maven { url = URI("https://packages.confluent.io/maven/") }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
    }

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }
}
