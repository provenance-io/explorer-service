import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.lint)
    alias(libs.plugins.java)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.gradle.gitprops)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.testlogger).apply(false)
}

// There is some generated but committed java code that has linting issues. We'll just
// exclude that here for now.
configure<KtlintExtension> {
    filter {
        exclude("**/src/java/**")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs =
            listOf(
                "-Xjsr305=strict",
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xopt-in=kotlin.contracts.ExperimentalContracts",
            )
    }
}

sourceSets {
    val test by getting {
        kotlin.srcDirs("src/test/kotlin")
    }
}

dependencies {
    implementation(project(":database"))
    implementation(project(":api-model"))

    implementation(libs.bundles.exposed)
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.logback)

    implementation(libs.apache.commons.csv)
    implementation(libs.apache.commons.text)
    implementation(libs.bouncycastle)
    implementation(libs.caffeine)
    implementation(libs.flyway)
    implementation(libs.grpc.netty)
    implementation(libs.json)
    implementation(libs.kase.change)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.postgresql)
    implementation(libs.protobuf.kotlin)
    implementation(libs.provenance.proto)
    implementation(libs.reflections)
    implementation(libs.spring.starter.actuator)
    implementation(libs.spring.starter.cache)
    implementation(libs.spring.starter.jdbc)
    implementation(libs.spring.starter.validation)
    implementation(libs.spring.starter.web)
    implementation(libs.springdoc.openapi.starter.webmvc.api)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // We're excluding this due to some weirdness going on in GovService with usage of coroutine code
    // that can't be executed outside of a coroutine. The runBlocking call in there is a bit scary, but we're
    // leaving it for now...
    implementation(libs.kotlinx.coroutines.core.jvm) {
        exclude(module = "kotlinx-coroutines-bom")
    }

    developmentOnly(libs.spring.dev.tools)

    testImplementation(libs.spring.starter.test) {
        exclude(module = "junit")
        exclude(module = "assertj-core")
    }

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.h2)
    testImplementation(libs.spring.mock)
    testImplementation(libs.kotest.assert)

    testRuntimeOnly(libs.junit.jupiter.engine)
}

// Configure the bootRun task to default to dev mode rather than having to type the
// config option in each time the service is started.
val profiles = System.getenv("SPRING_PROFILES_ACTIVE") ?: "development"
tasks.getByName<BootRun>("bootRun") {
    args = mutableListOf("--spring.profiles.active=$profiles")
}

// println("\nExclude Spring Boot Dev tools? " + version.toString().contains("main"))
// tasks.getByName<BootJar>("bootJar") {
//    if (!project.version.toString().contains("main"))
//        classpath += configurations.developmentOnly
//    enabled = true
// }

plugins.withType<com.adarshr.gradle.testlogger.TestLoggerPlugin> {
    configure<com.adarshr.gradle.testlogger.TestLoggerExtension> {
        theme = com.adarshr.gradle.testlogger.theme.ThemeType.STANDARD
        showCauses = true
        slowThreshold = 1000
        showSummary = true
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("STANDARD_OUT")
    }
}

springBoot.mainClass.set("io.provenance.explorer.ApplicationKt")
