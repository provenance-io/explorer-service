
object PluginIds { // please keep this sorted in sections
    // Kotlin
    const val Kotlin = "kotlin"
    const val KotlinSpring = "plugin.spring"
    const val Kapt = "kapt"

    // 3rd Party
    const val Flyway = "org.flywaydb.flyway"
    const val Idea = "idea"
    const val TaskTree = "com.dorongold.task-tree"
    const val TestLogger = "com.adarshr.test-logger"
    const val DependencyAnalysis = "com.autonomousapps.dependency-analysis"
    const val GoryLenkoGitProps = "com.gorylenko.gradle-git-properties"

    const val SpringDependency = "io.spring.dependency-management"
    const val SpringBoot = "org.springframework.boot"
}

object PluginVersions { // please keep this sorted in sections
    // Kotlin
    const val Kotlin = "1.9.25"

    // 3rd Party
    const val Flyway = "7.15.0"
    const val TaskTree = "2.1.0"
    const val TestLogger = "3.2.0"
    const val DependencyAnalysis = "1.14.1"
    const val GoryLenkoGitProps = "2.4.1"

    const val SpringDependency = "1.1.0"
    const val SpringBoot = "2.6.6"
}

object Versions {
    // kotlin
    const val Kotlin = PluginVersions.Kotlin

    // 3rd Party
    const val Flyway = PluginVersions.Flyway
    const val Jackson = "2.14.0"
    const val SpringBoot = PluginVersions.SpringBoot
    const val ProvProto = "1.20.1"

    // Testing
    const val Jupiter = "5.9.1"
    const val SpringMockk = "3.1.1"
    const val Kotest = "5.5.4"
    const val H2Database = "2.1.214"
}

object Libraries {
    // 3rd Party
    const val FlywayCore = "org.flywaydb:flyway-core:${Versions.Flyway}"
    const val JacksonJoda = "com.fasterxml.jackson.datatype:jackson-datatype-joda:${Versions.Jackson}"

    // Protobuf
    const val ProvenanceProto = "io.provenance:proto-kotlin:${Versions.ProvProto}"

    // Spring
    const val SpringBootDevTools = "org.springframework.boot:spring-boot-devtools:${Versions.SpringBoot}"
    const val SpringBootStarterTest = "org.springframework.boot:spring-boot-starter-test:${Versions.SpringBoot}"

    // Testing
    const val JunitJupiterApi = "org.junit.jupiter:junit-jupiter-api:${Versions.Jupiter}"
    const val JunitJupiterEngine = "org.junit.jupiter:junit-jupiter-engine:${Versions.Jupiter}"
    const val SpringMockk = "com.ninja-squad:springmockk:${Versions.SpringMockk}"
    const val KotestAssert = "io.kotest:kotest-assertions-core:${Versions.Kotest}"
    const val H2Database = "com.h2database:h2:${Versions.H2Database}"
}
