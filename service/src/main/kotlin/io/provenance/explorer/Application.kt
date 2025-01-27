package io.provenance.explorer

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.provenance.explorer.config.ExplorerProperties
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import java.util.TimeZone

@ComponentScan(basePackages = ["io.provenance.explorer" ])
@EnableAutoConfiguration(exclude = [HibernateJpaAutoConfiguration::class])
@EnableConfigurationProperties(value = [ExplorerProperties::class])
@EnableScheduling
@EnableWebMvc
class Application

const val TIMEZONE = "UTC"

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone(TIMEZONE))
    SpringApplicationBuilder(Application::class.java).properties(
        "spring.config.location:classpath:/"
    ).build().run(*args)
}

/**
 * ObjectMapper extension for getting the ObjectMapper configured
 * Attach to
 * Spring Boot via @Bean and @Primary:
 *  @Primary
 *  @Bean
 *  fun mapper(): ObjectMapper = ObjectMapper().configureFigure()
 */
fun ObjectMapper.configureProvenance(): ObjectMapper = this.registerKotlinModule()
//    .registerModule(JavaTimeModule())
    .registerModule(ProtobufModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
//    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

val OBJECT_MAPPER = ObjectMapper()
    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    .configureProvenance().apply {
        JavaTimeModule().apply {
            addSerializer(LocalDateTimeSerializer.INSTANCE)
        }.also { javaTime ->
            registerModule(javaTime)
        }
    }

val VANILLA_MAPPER = ObjectMapper().configureProvenance()

/* Singleton instance that can safely be shared globally */
val JSON_NODE_FACTORY: JsonNodeFactory = JsonNodeFactory.instance

val KTOR_CLIENT_JAVA = HttpClient(Java) {
    install(ContentNegotiation) {
        jackson {
            this.configureProvenance()
        }
    }
}
