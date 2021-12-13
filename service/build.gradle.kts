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
    api(Libraries.KotlinXCoRoutinesGuava)
    api(Libraries.ApacheCommonsText)
    api(Libraries.Khttp)
    implementation(Libraries.KaseChange)
    api("org.apache.httpcomponents:httpclient:4.5.12")

    implementation(Libraries.GrpcNetty)

    api(Libraries.LogbackCore)
    api(Libraries.LogbackClassic)
    api(Libraries.LogbackJackson)

    api(Libraries.JacksonModuleKotlin)
    api(Libraries.JacksonDatatype)
    api(Libraries.JacksonProtobuf)
    implementation(Libraries.Postgres)

    api(Libraries.Swagger)
    api(Libraries.Exposed)
    api(Libraries.ExposedJavaTime)
    api(Libraries.ExposedDao)
    api(Libraries.ExposedJdbc)

    developmentOnly(Libraries.SpringBootDevTools)
}

dependencyManagement {
    resolutionStrategy {
        cacheChangingModulesFor(0, "seconds")
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.logging.log4j") {
            useVersion("2.15.0")
        }
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
