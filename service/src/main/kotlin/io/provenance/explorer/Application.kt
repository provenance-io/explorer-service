package io.provenance.explorer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.extensions.configureProvenance
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

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    SpringApplicationBuilder(Application::class.java).properties(
        "spring.config.location:classpath:/"
    ).build().run(*args)
}

val OBJECT_MAPPER = ObjectMapper()
    .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
    .configureProvenance()

/* Singleton instance that can safely be shared globally */
val JSON_NODE_FACTORY: JsonNodeFactory = JsonNodeFactory.instance
