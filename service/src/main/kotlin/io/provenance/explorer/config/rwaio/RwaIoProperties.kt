package io.provenance.explorer.config.rwaio

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "rwa-io")
@Validated
class RwaIoProperties(
    val apiKey: String
)
