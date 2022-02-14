rootProject.name = "explorer-service"
include("database")
include("service")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
