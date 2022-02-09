package io.provenance.explorer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.extensions.configureProvenance
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import springfox.documentation.swagger2.annotations.EnableSwagger2
import java.util.TimeZone

@ComponentScan(basePackages = ["io.provenance.explorer" ])
@EnableAutoConfiguration(exclude = [HibernateJpaAutoConfiguration::class])
@EnableConfigurationProperties(value = [ExplorerProperties::class])
@EnableScheduling
@EnableWebMvc
@EnableSwagger2
class Application

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    SpringApplicationBuilder(Application::class.java).properties(
        "spring.config.location:classpath:/"
    ).build().run(*args)
}

val OBJECT_MAPPER = ObjectMapper()
    .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
    .configureProvenance()

val VANILLA_MAPPER = ObjectMapper().configureProvenance()

/* Singleton instance that can safely be shared globally */
val JSON_NODE_FACTORY: JsonNodeFactory = JsonNodeFactory.instance

val KTOR_CLIENT_JAVA = HttpClient(Java) {
    install(JsonFeature) {
        serializer = JacksonSerializer(VANILLA_MAPPER)
    }
}
