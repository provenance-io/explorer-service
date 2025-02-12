group = "io.provenance.explorer"
version = project.property("version")?.takeIf { it != "unspecified" } ?: "1.0-SNAPSHOT"

plugins {
    alias(libs.plugins.idea)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.tasktree)
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.nexus.publish)
}

allprojects {
    group = "io.provenance.explorer"
    version = artifactVersion(this)

    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.withType<Javadoc> { enabled = false }
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
