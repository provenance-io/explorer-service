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
    const val Protobuf = "com.google.protobuf"
    const val Grpc = "grpc"

    // Provenance
    const val ProvenanceDownloadProtos = "io.provenance.download-protos"
}

object PluginVersions { // please keep this sorted in sections
    // Kotlin
    const val Kotlin = "1.5.30"

    // 3rd Party
    const val Flyway = "7.15.0"
    const val TaskTree = "1.5"
    const val TestLogger = "2.1.1"
    const val DependencyAnalysis = "0.56.0"
    const val GoryLenkoGitProps = "1.5.2"

    const val SpringDependency = "1.0.11.RELEASE"
    const val SpringBoot = "2.5.4"
    const val Protobuf = "0.8.17"
}

object Versions {
    // kotlin
    const val Kotlin = PluginVersions.Kotlin
    const val KotlinXCoroutines = "1.5.1"

    // 3rd Party
    const val ApacheCommonsText = "1.9"
    const val BouncyCastle = "1.69"
    const val Exposed = "0.34.1"
    const val Flyway = PluginVersions.Flyway
    const val Jackson = "2.12.5"
    const val JacksonProtobuf = "0.9.12"
    const val Khttp = "1.0.0"
    const val Logback = "0.1.5"
    const val SpringBoot = PluginVersions.SpringBoot
    const val Swagger = "3.0.0"
    const val Protobuf = "3.15.0"
    const val Grpc = "1.40.1"
    const val GrpcStarter = "4.5.6"
    const val ProtocArtifact = "3.17.3"
    const val Postgres = "42.2.23"

    // Testing
    const val Jupiter = "5.7.1"
    const val SpringMockk = "3.0.1"
    const val Kotest = "4.4.3"

    // external protos
    const val Provenance = "v1.7.0"
    const val Cosmos = "v0.44.0"
    const val Wasmd = "v0.17.0"
    const val Ibc = "v1.1.0"
}

object Libraries {
    // Kotlin
    const val KotlinReflect = "org.jetbrains.kotlin:kotlin-reflect:${Versions.Kotlin}"
    const val KotlinStdlib = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin}"
    const val KotlinXCoRoutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.KotlinXCoroutines}"

    // 3rd Party
    const val Exposed = "org.jetbrains.exposed:exposed-core:${Versions.Exposed}"
    const val ExposedDao = "org.jetbrains.exposed:exposed-dao:${Versions.Exposed}"
    const val ExposedJavaTime = "org.jetbrains.exposed:exposed-jodatime:${Versions.Exposed}"
    const val ExposedJdbc = "org.jetbrains.exposed:exposed-jdbc:${Versions.Exposed}"
    const val FlywayCore = "org.flywaydb:flyway-core:${Versions.Flyway}"
    const val JacksonModuleKotlin = "com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.Jackson}"
    const val JacksonDatatype = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.Jackson}"
    const val JacksonProtobuf = "com.hubspot.jackson:jackson-datatype-protobuf:${Versions.JacksonProtobuf}"
    const val Postgres = "org.postgresql:postgresql:${Versions.Postgres}"
    const val BouncyCastle = "org.bouncycastle:bcprov-jdk15on:${Versions.BouncyCastle}"
    const val ApacheCommonsText = "org.apache.commons:commons-text:${Versions.ApacheCommonsText}"
    const val Khttp = "khttp:khttp:${Versions.Khttp}"

    // Logging
    const val LogbackCore = "ch.qos.logback.contrib:logback-json-core:${Versions.Logback}"
    const val LogbackClassic = "ch.qos.logback.contrib:logback-json-classic:${Versions.Logback}"
    const val LogbackJackson = "ch.qos.logback.contrib:logback-jackson:${Versions.Logback}"

    // Protobuf
    const val ProtobufJava = "com.google.protobuf:protobuf-java:${Versions.Protobuf}"
    const val GrpcProtobuf = "io.grpc:grpc-protobuf:${Versions.Grpc}"
    const val GrpcStub = "io.grpc:grpc-stub:${Versions.Grpc}"
    const val ProtocArtifact = "com.google.protobuf:protoc:${Versions.ProtocArtifact}"
    const val GrpcArtifact = "io.grpc:protoc-gen-grpc-java:${Versions.Grpc}"
    const val GrpcNetty = "io.grpc:grpc-netty:${Versions.Grpc}"
    const val GrpcStart = "io.github.lognet:grpc-spring-boot-starter:${Versions.GrpcStarter}"

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

// gradle configurations
const val kapt = "kapt"
const val api = "api"
const val implementation = "implementation"
const val testCompileOnly = "testCompileOnly"
const val testImplementation = "testImplementation"
const val testRuntimeOnly = "testRuntimeOnly"
const val developmentOnly = "developmentOnly"
