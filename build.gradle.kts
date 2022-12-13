import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version PluginVersions.Kotlin
    java
    id(PluginIds.Idea)
    id(PluginIds.TaskTree) version PluginVersions.TaskTree
    id(PluginIds.DependencyAnalysis) version PluginVersions.DependencyAnalysis
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

group = "io.provenance.explorer"
version = project.property("version")?.takeIf { it != "unspecified" } ?: "1.0-SNAPSHOT"

// Publishing
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri(project.property("nexus.url") as String))
            snapshotRepositoryUrl.set(uri(project.property("nexus.snapshot.repository.url") as String))
            username.set(findProject(project.property("nexus.username") as String)?.toString() ?: System.getenv("OSSRH_USERNAME"))
            password.set(findProject(project.property("nexus.password") as String)?.toString() ?: System.getenv("OSSRH_PASSWORD"))
            stagingProfileId.set(project.property("nexus.staging.profile.id") as String) // prevents querying for the staging profile id, performance optimization
        }
    }
}

allprojects {
    group = "io.provenance.explorer"
    version = artifactVersion(this)

    repositories {
        mavenCentral()
    }
}

configurations.all {
    resolutionStrategy {
        cacheDynamicVersionsFor(0, "seconds")
        cacheChangingModulesFor(0, "seconds")
    }
}

subprojects {
    apply {
        plugin(PluginIds.Kotlin)
        plugin(PluginIds.Idea)
    }

    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = project.uri("https://maven.java.net/content/groups/public") }
        maven { url = project.uri("https://oss.sonatype.org/content/repositories/snapshots") }
        maven { url = project.uri("https://dl.bintray.com/kotlin/exposed") }
    }

    tasks.withType<Javadoc> { enabled = false }

    tasks.withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_11.toString()
        targetCompatibility = sourceCompatibility
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf(
                "-Xjsr305=strict",
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xopt-in=kotlin.contracts.ExperimentalContracts"
            )
            jvmTarget = "11"
            languageVersion = "1.6"
            apiVersion = "1.6"
        }
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.apache.logging.log4j" && (requested.version == "2.14.1") || (requested.version == "2.15.0")) {
                useVersion("2.15.0")
                because("CVE-2021-44228")
            }
        }
    }
}
