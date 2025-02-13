package io.provenance.explorer.domain.models.explorer.pulse

import java.math.BigDecimal

data class TransactionSummary(
    val txHash: String,
    val block: Int,
    val time: String,
    val type: String,
    val value: BigDecimal,
    val quoteValue: BigDecimal,
    val quoteDenom: String,
    val details: List<Pair<String, String>>?
)
