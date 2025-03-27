package io.provenance.explorer.config.pulse

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "pulse")
@Validated
class PulseProperties(
    val ftsLoanDataUrl: String
)
