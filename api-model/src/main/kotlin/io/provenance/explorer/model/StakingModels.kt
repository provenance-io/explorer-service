package io.provenance.explorer.model

import io.provenance.explorer.model.base.CoinStr
import java.time.LocalDateTime

data class Delegation(
    val delegatorAddr: String,
    val validatorSrcAddr: String,
    val validatorDstAddr: String?,
    val amount: CoinStr,
    val initialBal: CoinStr?,
    val shares: String?,
    val block: Int?,
    val endTime: LocalDateTime?
)

data class UnpaginatedDelegation(
    val records: List<Delegation>,
    val rollupTotals: Map<String, CoinStr>
)
