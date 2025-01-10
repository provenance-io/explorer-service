import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    kotlin(PluginIds.KotlinSpring) version libs.versions.kotlin
    id(PluginIds.GoryLenkoGitProps) version PluginVersions.GoryLenkoGitProps
    id(PluginIds.SpringDependency) version PluginVersions.SpringDependency
    id(PluginIds.SpringBoot) version PluginVersions.SpringBoot
    id(PluginIds.TestLogger) version PluginVersions.TestLogger apply false
}

sourceSets {
    val test by getting {
        kotlin.srcDirs("src/test/kotlin")
    }
}

dependencies {
    implementation(project(":database"))
    implementation(project(":api-model"))

    listOf(
        libs.bundles.jackson,

        libs.bundles.ktor,

        libs.apache.commons.csv,
        libs.apache.commons.text,
        libs.bouncycastle,
        libs.caffeine,
        libs.exposed,
        libs.exposed.dao,
        libs.exposed.java.datetime,
        libs.exposed.jdbc,
        libs.flyway,
        libs.grpc.netty,
        libs.json,
        libs.kase.change,
        libs.kotlin.reflect,
        libs.kotlinx.coroutines.core,
        libs.postgresql,
        libs.protobuf.kotlin,
        libs.provenance.proto,
        libs.reflections,
        libs.spring.starter.actuator,
        libs.spring.starter.cache,
        libs.spring.starter.jdbc,
        libs.spring.starter.validation,
        libs.spring.starter.web,
        libs.swagger,
    ).forEach(::implementation)

    // We're excluding this due to some weirdness going on in GovService with usage of coroutine code
    // that can't be executed outside of a coroutine. The runBlocking call in there is a bit scary, but we're
    // leaving it for now...
    implementation(libs.kotlinx.coroutines.core.jvm) {
        exclude(module = "kotlinx-coroutines-bom")
    }

    developmentOnly(Libraries.SpringBootDevTools)

    testImplementation(Libraries.SpringBootStarterTest) {
        exclude(module = "junit")
        exclude(module = "assertj-core")
    }
    testImplementation(Libraries.JunitJupiterApi)
    testImplementation(Libraries.H2Database)
    testRuntimeOnly(Libraries.JunitJupiterEngine)
    testImplementation(Libraries.SpringMockk)
    testImplementation(Libraries.KotestAssert)
}

// Configure the bootRun task to default to dev mode rather than having to type the
// config option in each time the service is started.
val profiles = System.getenv("SPRING_PROFILES_ACTIVE") ?: "development"
tasks.getByName<BootRun>("bootRun") {
    args = mutableListOf("--spring.profiles.active=$profiles")
}

//println("\nExclude Spring Boot Dev tools? " + version.toString().contains("main"))
//tasks.getByName<BootJar>("bootJar") {
//    if (!project.version.toString().contains("main"))
//        classpath += configurations.developmentOnly
//    enabled = true
//}

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
    }

    failFast = true
}

springBoot.mainClass.set("io.provenance.explorer.ApplicationKt")