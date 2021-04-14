package io.provenance.explorer.domain.models.explorer

import org.joda.time.DateTime
import java.math.BigDecimal

data class ValidatorSummary (
    val moniker: String,
    val addressId: String,
    val consensusAddress: String,
    val proposerPriority: Int?,
    val uptime: BigDecimal?,
    val votingPower: CountTotal?,
    val commission: String,
    val bondedTokens: BondedTokens,
    val selfBonded: BondedTokens,
    val delegators: Long?,
    val bondHeight: Long?,
    val status: String,
    val currentGasFee: Double?,
    val unbondingHeight: Long?
)

data class ValidatorDetails (
    val votingPower: CountTotal?,
    val moniker: String,
    val operatorAddress: String,
    val ownerAddress: String,
    val withdrawalAddress: String,
    val consensusPubKey: String?,
    val blockCount: CountTotal?,
    val bondHeight: Long?,
    val uptime: BigDecimal?,
    val imgUrl: String?,
    val description: String?,
    val siteUrl: String?,
    val identity: String?,
    val currentGasFee: Double?,
    val status: String,
    val unbondingHeight: Long?,
    val jailedUntil: DateTime?
)

data class ValidatorDelegation (
    val address: String,
    val amount: CoinStr,
    val shares: String?,
    val block: Int?,
    val endTime: DateTime?
)

data class ValidatorCommission (
    val bondedTokens: BondedTokens,
    val selfBonded: BondedTokens,
    val delegatorBonded: BondedTokens,
    val delegatorCount: Long,
    val totalShares: String,
    val commissionRewards: CoinStr,
    val commissionRate:	CommissionRate
)

data class CommissionRate (
    val rate: String,
    val maxRate: String,
    val maxChangeRate: String
)
