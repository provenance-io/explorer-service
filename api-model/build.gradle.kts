plugins {
    id("kotlin")
    id("maven-publish")
    id("signing")
    id("java-library")
}

group = project.property("group.id") as String
version = artifactVersion(rootProject)

repositories {
    mavenCentral()
}

dependencies {
    listOf (
        libs.jackson.jodadatatype,
        libs.provenance.proto
    ).forEach(::implementation)
}

tasks.jar {
    archiveBaseName.set("explorer-${project.name}")
}

tasks.withType<Javadoc> { enabled = true }

// Generate sources Jar and Javadocs
java {
    withJavadocJar()
    withSourcesJar()
}

// Maven publishing
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            afterEvaluate {
                groupId = project.group.toString()
                artifactId = tasks.jar.get().archiveBaseName.get()
                version = tasks.jar.get().archiveVersion.get()
            }

            pom {
                name.set(project.property("pom.name") as String)
                description.set(project.property("pom.description") as String)
                url.set(project.property("pom.url") as String)

                licenses {
                    license {
                        name.set(project.property("license.name") as String)
                        url.set(project.property("license.url") as String)
                    }
                }

                developers {
                    developer {
                        id.set(project.property("developer.id") as String)
                        name.set(project.property("developer.name") as String)
                        email.set(project.property("developer.email") as String)
                    }
                }

                scm {
                    connection.set(project.property("scm.connection") as String)
                    developerConnection.set(project.property("scm.developerConnection") as String)
                    url.set((project.property("scm.url") as String) + "/tree/main/api-model")
                }
            }
        }
    }

    signing {
        sign(publishing.publications["mavenJava"])
    }
}
