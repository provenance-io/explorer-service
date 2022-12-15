rootProject.name = "explorer-service"
include("database")
include("service")
include("api-model")
include("api-client")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
