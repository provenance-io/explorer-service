package io.provenance.explorer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import io.provenance.core.json.configureProvenance
import io.provenance.explorer.config.ExplorerProperties
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import springfox.documentation.swagger2.annotations.EnableSwagger2
import java.util.*

@ComponentScan(basePackages = ["io.provenance.explorer" ])
@EnableSwagger2
@EnableAutoConfiguration(
        exclude = [
            HibernateJpaAutoConfiguration::class
        ]
)
@EnableConfigurationProperties(value = [ExplorerProperties::class])
open class Application

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    SpringApplicationBuilder(Application::class.java).properties(
            "spring.config.location:classpath:/"
    ).build().run(*args)
}

val OBJECT_MAPPER = ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
        .configureProvenance()