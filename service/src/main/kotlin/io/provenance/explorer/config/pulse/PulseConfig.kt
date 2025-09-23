package io.provenance.explorer.config.pulse

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.provenance.explorer.configureProvenance
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PulseConfig {

    @Bean("pulseHttpClient")
    fun httpClient(): HttpClient =
        HttpClient(Java) {
            install(ContentNegotiation) {
                jackson {
                    registerModules(JavaTimeModule())
                    this.configureProvenance()
                }
            }
        }
}
