// https://docs.gradle.org/current/userguide/kotlin_dsl.html#sec:kotlin-dsl_plugin
plugins {
    // Note that libs.versions.toml isn't available here, so we're entering the version again...
    kotlin("jvm") version "2.1.10"
}

repositories {
    mavenCentral()
}