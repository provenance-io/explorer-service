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
    }
}
