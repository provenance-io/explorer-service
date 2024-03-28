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
    const val Kotlin = "1.6.21"

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
    const val KotlinXCoroutines = "1.6.4"

    // 3rd Party
    const val ApacheCommonsText = "1.10.0"
    const val ApacheCommonsCsv = "1.9.0"
    const val ApacheHttpClient = "5.2"
    const val BouncyCastle = "1.70"
    const val Exposed = "0.41.1"
    const val Flyway = PluginVersions.Flyway
    const val Jackson = "2.14.0"
    const val JacksonProtobuf = "0.9.13"
    const val Json = "20211205"
    const val KaseChange = "1.3.0"
    const val Ktor = "2.1.3"
    const val SpringBoot = PluginVersions.SpringBoot
    const val Swagger = "3.0.0"
    const val Grpc = "1.50.2"
    const val ProvProto = "1.18.0"
    const val Postgres = "42.2.23"
    const val Protobuf = "3.21.9"
    const val Reflections = "0.9.12"

    // Testing
    const val Jupiter = "5.9.1"
    const val SpringMockk = "3.1.1"
    const val Kotest = "5.5.4"
}

object Libraries {
    // Kotlin
    const val KotlinReflect = "org.jetbrains.kotlin:kotlin-reflect:${Versions.Kotlin}"
    const val KotlinStdlib = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin}"
    const val KotlinXCoRoutinesCoreJvm = "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${Versions.KotlinXCoroutines}"
    const val KotlinXCoRoutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.KotlinXCoroutines}"

    // 3rd Party
    const val Exposed = "org.jetbrains.exposed:exposed-core:${Versions.Exposed}"
    const val ExposedDao = "org.jetbrains.exposed:exposed-dao:${Versions.Exposed}"
    const val ExposedJavaTime = "org.jetbrains.exposed:exposed-jodatime:${Versions.Exposed}"
    const val ExposedJdbc = "org.jetbrains.exposed:exposed-jdbc:${Versions.Exposed}"
    const val FlywayCore = "org.flywaydb:flyway-core:${Versions.Flyway}"
    const val JacksonModuleKotlin = "com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.Jackson}"
    const val JacksonDatatype = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.Jackson}"
    const val JacksonJoda = "com.fasterxml.jackson.datatype:jackson-datatype-joda:${Versions.Jackson}"
    const val JacksonProtobuf = "com.hubspot.jackson:jackson-datatype-protobuf:${Versions.JacksonProtobuf}"
    const val Postgres = "org.postgresql:postgresql:${Versions.Postgres}"
    const val BouncyCastle = "org.bouncycastle:bcprov-jdk15on:${Versions.BouncyCastle}"
    const val ApacheCommonsText = "org.apache.commons:commons-text:${Versions.ApacheCommonsText}"
    const val ApacheCommonsCsv = "org.apache.commons:commons-csv:${Versions.ApacheCommonsCsv}"
    const val ApacheHttpClient = "org.apache.httpcomponents.client5:httpclient5:${Versions.ApacheHttpClient}"
    const val KtorClientCore = "io.ktor:ktor-client-core:${Versions.Ktor}"
    const val KtorClientEngineJava = "io.ktor:ktor-client-java:${Versions.Ktor}"
    const val KtorClientSerialization = "io.ktor:ktor-serialization-jackson:${Versions.Ktor}"
    const val KtorClientContentNeg = "io.ktor:ktor-client-content-negotiation:${Versions.Ktor}"
    const val KaseChange = "net.pearx.kasechange:kasechange:${Versions.KaseChange}"
    const val Json = "org.json:json:${Versions.Json}"
    const val Reflections = "org.reflections:reflections:${Versions.Reflections}"

    // Protobuf
    const val GrpcNetty = "io.grpc:grpc-netty:${Versions.Grpc}"
    const val ProvenanceProto = "io.provenance:proto-kotlin:${Versions.ProvProto}"
    const val ProtobufKotlin = "com.google.protobuf:protobuf-kotlin:${Versions.Protobuf}"

    // Spring
    const val SpringBootDevTools = "org.springframework.boot:spring-boot-devtools:${Versions.SpringBoot}"
    const val SpringBootStarterActuator = "org.springframework.boot:spring-boot-starter-actuator:${Versions.SpringBoot}"
    const val SpringBootStarterJdbc = "org.springframework.boot:spring-boot-starter-jdbc:${Versions.SpringBoot}"
    const val SpringBootConfigProcessor = "org.springframework.boot:spring-boot-configuration-processor:${Versions.SpringBoot}"
    const val SpringBootStarterValidation = "org.springframework.boot:spring-boot-starter-validation:${Versions.SpringBoot}"
    const val SpringBootStarterWeb = "org.springframework.boot:spring-boot-starter-web:${Versions.SpringBoot}"
    const val SpringBootStarterTest = "org.springframework.boot:spring-boot-starter-test:${Versions.SpringBoot}"

    const val Swagger = "io.springfox:springfox-boot-starter:${Versions.Swagger}"

    // Testing
    const val JunitJupiterApi = "org.junit.jupiter:junit-jupiter-api:${Versions.Jupiter}"
    const val JunitJupiterEngine = "org.junit.jupiter:junit-jupiter-engine:${Versions.Jupiter}"
    const val SpringMockk = "com.ninja-squad:springmockk:${Versions.SpringMockk}"
    const val KotestAssert = "io.kotest:kotest-assertions-core:${Versions.Kotest}"
}
