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
    kotlin("jvm") version "1.3.70" apply false
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
    apply(plugin = "com.github.johnrengelman.shadow")
    apply(plugin = "maven-publish")

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
    }

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }
}
