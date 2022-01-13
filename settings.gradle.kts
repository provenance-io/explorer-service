rootProject.name = "explorer-service"
include("database")
include("service")
include("proto")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
