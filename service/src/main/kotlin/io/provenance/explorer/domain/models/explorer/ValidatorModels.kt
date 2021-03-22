package io.provenance.explorer.domain.models.explorer

import org.joda.time.DateTime
import java.math.BigDecimal

data class ValidatorSummary(
    val moniker: String,
    val addressId: String,
    val consensusAddress: String,
    val proposerPriority: Int,
    val uptime: BigDecimal,
    val votingPower: CountTotal,
    val commission: BigDecimal,
    val bondedTokens: BondedTokens,
    val selfBonded: BondedTokens,
    val delegators: Long,
    val bondHeight: Int,
    val status: String
)

data class ValidatorDetails(
    val votingPower: CountTotal,
    val moniker: String,
    val operatorAddress: String,
    val ownerAddress: String,
    val withdrawalAddress: String,
    val consensusPubKey: String?,
    val blockCount: CountTotal,
    val bondHeight: Int,
    val uptime: BigDecimal,
    val imgUrl: String?,
    val description: String?,
    val siteUrl: String?,
    val identity: String?
)

data class ValidatorDelegation(
    val address: String,
    val amount: Coin,
    val shares: BigDecimal?,
    val block: Int?,
    val endTime: DateTime?
)

data class ValidatorCommission(
    val bondedTokens: BondedTokens,
    val selfBonded: BondedTokens,
    val delegatorBonded: BondedTokens,
    val delegatorCount: Long,
    val totalShares: BigDecimal,
    val commissionRewards: CoinDec,
    val commissionRate:	CommissionRate
)

data class CommissionRate(
    val rate:	BigDecimal,
    val maxRate: BigDecimal,
    val maxChangeRate: BigDecimal
)