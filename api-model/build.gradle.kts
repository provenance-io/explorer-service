import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.signing)
    alias(libs.plugins.java.lib)
}

group = project.property("group.id") as String
version = artifactVersion(rootProject)

repositories {
    mavenCentral()
}

dependencies {
    listOf(
        libs.jackson.jodadatatype,
        libs.provenance.proto
    ).forEach(::implementation)
}

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
