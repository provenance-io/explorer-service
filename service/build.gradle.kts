import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    kotlin(PluginIds.KotlinSpring) version PluginVersions.Kotlin
    kotlin(PluginIds.Kapt)
    id(PluginIds.GoryLenkoGitProps) version PluginVersions.GoryLenkoGitProps
    id(PluginIds.SpringDependency) version PluginVersions.SpringDependency
    id(PluginIds.SpringBoot) version PluginVersions.SpringBoot
}

sourceSets {
    test {
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDirs("src/test/kotlin")
            resources.srcDirs("src/test/resources")
        }
    }
    create("integrationTest") {
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDirs("src/test/kotlin")
            compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output +
                configurations.testRuntime + configurations.testCompile
            runtimeClasspath += output + compileClasspath + test.get().output
            resources.srcDirs(file("src/test/resources"))
        }
    }
}

configurations.all {
    exclude(group = "log4j")
}

dependencies {
    api(project(":database"))
    api(project(":proto"))

    api(Libraries.SpringBootStarterWeb)
    api(Libraries.SpringBootDevTools)
    api(Libraries.SpringBootStarterJdbc)
    api(Libraries.SpringBootStarterActuator)
    api(Libraries.SpringBootStarterValidation)
    kapt(Libraries.SpringBootConfigProcessor)

    api(Libraries.BouncyCastle)
    api(Libraries.KotlinXCoRoutinesCore)

    implementation(Libraries.GrpcNetty)
    implementation(Libraries.GrpcStart)

    api(Libraries.LogbackCore)
    api(Libraries.LogbackClassic)
    api(Libraries.LogbackJackson)

    api(Libraries.JacksonModuleKotlin)
    api(Libraries.JacksonDatatype)
    api(Libraries.JacksonProtobuf)
    implementation(Libraries.Postgres)

    api(Libraries.Swagger)
    api(Libraries.Exposed)

    testImplementation(Libraries.SpringBootStarterTest)

    developmentOnly(Libraries.SpringBootDevTools)
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

//tasks.getByName<Jar>("testJar") {
//    archiveClassifier.set("test")
//    from(sourceSets.test.get().output)
//}
