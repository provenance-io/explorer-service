rootProject.name = "explorer-service"
include(
    "database",
    "service",
    "api-model",
    "api-client",
)

// Be more prescriptive with where we fetch plugins.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
        id("org.jetbrains.kotlin.kotlin-gradle-plugin") version "1.9.25"
        kotlin("jvm") version "1.9.25"
    }
}
