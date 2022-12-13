package io.provenance.explorer.model

import io.provenance.explorer.model.base.CoinStr
import org.joda.time.DateTime

data class Delegation(
    val delegatorAddr: String,
    val validatorSrcAddr: String,
    val validatorDstAddr: String?,
    val amount: CoinStr,
    val initialBal: CoinStr?,
    val shares: String?,
    val block: Int?,
    val endTime: DateTime?
)

data class UnpaginatedDelegation(
    val records: List<Delegation>,
    val rollupTotals: Map<String, CoinStr>
)
