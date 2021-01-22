package io.provenance.explorer.domain.models.clients.pb

import io.provenance.explorer.domain.models.clients.DenomAmount

data class ValidatorDistribution(
    val operatorAddress: String,
    val selfBondRewards: List<DenomAmount>,
    val valCommission: List<DenomAmount>
)

data class Commission(val commissionRates: CommissionRates, val updateTime: String)

data class CommissionRates(val rate: String, val maxRate: String, val maxChangeRate: String)

data class SigningInfo(
    val address: String,
    val startHeight: String?,
    val indexOffset: String,
    val jailedUntil: String,
    val tombstoned: Boolean,
    val missedBlocksCounter: String?
)

