package io.provenance.explorer.domain.models.clients.pb

import io.provenance.explorer.domain.models.clients.DenomAmount
import io.provenance.explorer.domain.models.clients.Pagination

data class ValidatorDistribution(
    val operatorAddress: String,
    val selfBondRewards: List<DenomAmount>?,
    val valCommission: ValCommission
)

data class ValCommission(val commission: Commission?)

data class Commission(val commissionRates: CommissionRates, val updateTime: String)

data class CommissionRates(val rate: String, val maxRate: String, val maxChangeRate: String)

data class SigningInfoPaged(
    val info: List<SigningInfo>,
    val pagination: Pagination
)

data class SigningInfo(
    val address: String,
    val startHeight: String,
    val indexOffset: String,
    val jailedUntil: String,
    val tombstoned: Boolean,
    val missedBlocksCounter: String
)

data class Supply(val amount : DenomAmount)
