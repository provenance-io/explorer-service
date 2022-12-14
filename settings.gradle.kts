rootProject.name = "explorer-service"
include("database")
include("service")
include("api-model")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
