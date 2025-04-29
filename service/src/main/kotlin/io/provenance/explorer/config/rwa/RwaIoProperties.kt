package io.provenance.explorer.config.rwa

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "rwa-io")
@Validated
class RwaIoProperties(
    val baseUrl: String,
    val slug: String,
    val apiKey: String
)
