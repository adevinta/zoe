plugins {
    java
    kotlin("jvm") version "1.3.71"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    testImplementation("junit:junit:4.12")
}