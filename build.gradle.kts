import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version PluginVersions.Kotlin
    java
    id(PluginIds.Idea)
    id(PluginIds.TaskTree) version PluginVersions.TaskTree
    id(PluginIds.TestLogger) version PluginVersions.TestLogger apply false
    id(PluginIds.DependencyAnalysis) version PluginVersions.DependencyAnalysis
    id(PluginIds.Protobuf) version PluginVersions.Protobuf
    id(PluginIds.ProvenanceDownloadProtos)
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
        plugin(PluginIds.TestLogger)
        plugin(PluginIds.Protobuf)
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
            freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn")
            jvmTarget = "11"
            languageVersion = "1.5"
            apiVersion = "1.5"
        }
    }

    plugins.withType<com.adarshr.gradle.testlogger.TestLoggerPlugin> {
        configure<com.adarshr.gradle.testlogger.TestLoggerExtension> {
            theme = com.adarshr.gradle.testlogger.theme.ThemeType.STANDARD
            showCauses = true
            slowThreshold = 1000
            showSummary = true
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform {
            excludeTags("intTest")
            includeTags("junit-jupiter", "junit-vintage")
        }

        failFast = true
    }

    dependencies {
        implementation(Libraries.KotlinReflect)
        implementation(Libraries.KotlinStdlib)

        testImplementation(Libraries.SpringBootStarterTest) {
            exclude(module = "junit")
            exclude(module = "mockito-core")
            exclude(module = "assertj-core")
        }
        testImplementation(Libraries.JunitJupiterApi)
        testRuntimeOnly(Libraries.JunitJupiterEngine)
        testImplementation(Libraries.SpringMockk)
        testImplementation(Libraries.KotestAssert)
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.apache.logging.log4j") {
                useVersion("2.15.0")
            }
        }
    }
}
