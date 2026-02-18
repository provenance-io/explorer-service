package io.provenance.explorer.domain.models.explorer.pulse

import java.math.BigDecimal
import java.util.UUID

data class ExchangeSummary(
    val id: UUID,
    val marketAddress: String,
    val name: String,
    val symbol: String,
    val display: String,
    val description: String,
    val iconUri: String,
    val websiteUrl: String,
    val base: String,
    val quote: String,
    val committed: BigDecimal,
    val settlement: BigDecimal,
    val volume: BigDecimal
)
