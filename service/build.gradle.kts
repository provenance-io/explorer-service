import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    kotlin(PluginIds.KotlinSpring) version PluginVersions.Kotlin
    kotlin(PluginIds.Kapt)
    id(PluginIds.GoryLenkoGitProps) version PluginVersions.GoryLenkoGitProps
    id(PluginIds.SpringDependency) version PluginVersions.SpringDependency
    id(PluginIds.SpringBoot) version PluginVersions.SpringBoot
    id(PluginIds.TestLogger) version PluginVersions.TestLogger apply false
}

sourceSets {
    test {
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDirs("src/test/kotlin")
            resources.srcDirs("src/test/resources")
        }
    }
}

dependencies {
    implementation(project(":database"))
    implementation(project(":api-model"))
    implementation(Libraries.KotlinReflect)
    implementation(Libraries.KotlinStdlib)
    implementation(Libraries.ProtobufKotlin)
    implementation(Libraries.ProvenanceProto)
    implementation(Libraries.Reflections)

    implementation(Libraries.SpringBootStarterWeb)
    implementation(Libraries.SpringBootStarterJdbc)
    implementation(Libraries.SpringBootStarterActuator)
    implementation(Libraries.SpringBootStarterValidation)
    kapt(Libraries.SpringBootConfigProcessor)

    implementation(Libraries.BouncyCastle)
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
    implementation(Libraries.Exposed)
    implementation(Libraries.ExposedJavaTime)
    implementation(Libraries.ExposedDao)
    implementation(Libraries.ExposedJdbc)
    implementation(Libraries.FlywayCore)

    developmentOnly(Libraries.SpringBootDevTools)

    testImplementation(Libraries.SpringBootStarterTest) {
        exclude(module = "junit")
        //exclude(module = "mockito-core")
        exclude(module = "assertj-core")
    }
    testImplementation(Libraries.JunitJupiterApi)
    testImplementation("com.h2database:h2:2.2.224")
    testRuntimeOnly(Libraries.JunitJupiterEngine)
    testImplementation(Libraries.SpringMockk)
    testImplementation(Libraries.KotestAssert)
}

dependencyManagement {
    resolutionStrategy {
        cacheChangingModulesFor(0, "seconds")
    }
}

var profiles = System.getenv("SPRING_PROFILES_ACTIVE") ?: "development"

tasks.getByName<BootRun>("bootRun") {
    args = mutableListOf("--spring.profiles.active=$profiles")
}

springBoot.mainClass.set("io.provenance.explorer.ApplicationKt")

println("\nExclude Spring Boot Dev tools? " + version.toString().contains("main"))
tasks.getByName<BootJar>("bootJar") {
    if (!project.version.toString().contains("main"))
        classpath += configurations.developmentOnly
    enabled = true
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
    }

    failFast = true
}
