import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    kotlin(PluginIds.KotlinSpring) version libs.versions.kotlin
//    id("org.jetbrains.kotlin.kapt")
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
        libs.bouncycastle,
        libs.caffeine,
        libs.exposed,
        libs.kotlin.reflect,
        libs.protobuf.kotlin,
        libs.provenance.proto,
        libs.reflections,
    ).forEach(::implementation)

//    implementation(Libraries.KotlinReflect)
    implementation(Libraries.KotlinStdlib)
//    implementation(Libraries.ProtobufKotlin)
//    implementation(Libraries.ProvenanceProto)
//    implementation(Libraries.Reflections)
//    implementation(Libraries.Caffeine)

    implementation(Libraries.SpringBootStarterWeb)
    implementation(Libraries.SpringBootStarterJdbc)
    implementation(Libraries.SpringBootStarterActuator)
    implementation(Libraries.SpringBootStarterValidation)
    implementation(Libraries.SpringBootStarterCache)
//    kapt(Libraries.SpringBootConfigProcessor)

//    implementation(Libraries.BouncyCastle)
    implementation(Libraries.KotlinXCoRoutinesCoreJvm) {
        exclude(module = "kotlinx-coroutines-bom")
    }
    implementation(Libraries.KotlinXCoRoutinesCore)
    implementation(Libraries.ApacheCommonsText)
    implementation(Libraries.ApacheCommonsCsv)
    implementation(Libraries.KaseChange)
    implementation(Libraries.ApacheHttpClient)
    implementation(Libraries.KtorClientCore)
    implementation(Libraries.KtorClientEngineJava)
    implementation(Libraries.KtorClientSerialization)
    implementation(Libraries.KtorClientContentNeg)
    implementation(Libraries.Json)

    implementation(Libraries.GrpcNetty)

    implementation(Libraries.JacksonModuleKotlin)
    implementation(Libraries.JacksonDatatype)
    implementation(Libraries.JacksonJoda)
    implementation(Libraries.JacksonProtobuf)
    implementation(Libraries.Postgres)

    implementation(Libraries.Swagger)
//    implementation(Libraries.Exposed)
    implementation(Libraries.ExposedJavaTime)
    implementation(Libraries.ExposedDao)
    implementation(Libraries.ExposedJdbc)
    implementation(Libraries.FlywayCore)

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