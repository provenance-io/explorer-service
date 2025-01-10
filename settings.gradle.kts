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

    val KtLintVersion = "12.1.2"
    val KotlinVersion = "1.9.25"

    plugins {
        id("org.jlleitschuh.gradle.ktlint") version KtLintVersion
        id("org.jetbrains.kotlin.kotlin-gradle-plugin") version KotlinVersion
        kotlin("jvm") version KotlinVersion
    }
}
