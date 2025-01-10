import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version PluginVersions.Kotlin
    java
    id(PluginIds.Idea)
    id(PluginIds.TaskTree) version PluginVersions.TaskTree
    id(PluginIds.DependencyAnalysis) version PluginVersions.DependencyAnalysis
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint")
}

group = "io.provenance.explorer"
version = project.property("version")?.takeIf { it != "unspecified" } ?: "1.0-SNAPSHOT"

allprojects {
    group = "io.provenance.explorer"
    version = artifactVersion(this)

    apply {
//        plugin("org.jlleitschuh.gradle.ktlint")
    }

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply {
        plugin(PluginIds.Kotlin)
        plugin(PluginIds.Idea)
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<Javadoc> { enabled = false }

    tasks.withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_11.toString()
        targetCompatibility = sourceCompatibility
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs =
                listOf(
                    "-Xjsr305=strict",
                    "-Xopt-in=kotlin.RequiresOptIn",
                    "-Xopt-in=kotlin.contracts.ExperimentalContracts",
                )
            jvmTarget = "11"
            languageVersion = "1.6"
            apiVersion = "1.6"
        }
    }
}

// Publishing
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri(project.property("nexus.url") as String))
            snapshotRepositoryUrl.set(uri(project.property("nexus.snapshot.repository.url") as String))
            username.set(findProject(project.property("nexus.username") as String)?.toString() ?: System.getenv("OSSRH_USERNAME"))
            password.set(findProject(project.property("nexus.password") as String)?.toString() ?: System.getenv("OSSRH_PASSWORD"))
            stagingProfileId.set(
                project.property("nexus.staging.profile.id") as String,
            ) // prevents querying for the staging profile id, performance optimization
        }
    }
}
