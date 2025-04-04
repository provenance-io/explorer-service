package io.provenance.explorer.config.pulse

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "pulse")
@Validated
class PulseProperties(
    val ftsLoanDataUrl: String,
    /*
    Locked hash addresses are used to filter out locked hash transactions from
    circulating supply and market cap metrics. Hash held in these address are
    not available for trading and should not be included in the circulating
    supply.
     */
    val hashHoldersExcludedFromCirculatingSupply: List<String>
)
