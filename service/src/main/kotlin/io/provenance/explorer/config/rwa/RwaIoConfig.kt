package io.provenance.explorer.config.rwa

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.provenance.explorer.OBJECT_MAPPER
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RwaIoConfig {

    @Bean("rwaIoHttpClient")
    fun httpClient(): HttpClient =
        HttpClient(Java) {
            install(ContentNegotiation) {
                jackson {
                    registerModules(JavaTimeModule())
                    OBJECT_MAPPER
                }
            }
        }
}
