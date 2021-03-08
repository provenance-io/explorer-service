package io.provenance.explorer.domain.models.explorer

import org.joda.time.DateTime
import java.math.BigDecimal
import java.math.BigInteger

data class ValidatorSummary(
    val moniker: String,
    val addressId: String,
    val consensusAddress: String,
    val proposerPriority: Int,
    val uptime: BigDecimal,
    val votingPower: Int,
    val votingPowerPercent: BigDecimal,
    val commission: BigDecimal,
    val bondedTokens: Long,
    val bondedTokensDenomination: String,
    val selfBonded: BigInteger,
    val selfBondedDenomination: String,
    val delegators: Long,
    val bondHeight: Int,
    val status: String
)

data class ValidatorDetails(
    val votingPower: Int,
    val votingPowerPercent: BigDecimal,
    val moniker: String,
    val operatorAddress: String,
    val ownerAddress: String,
    val withdrawalAddress: String,
    val consensusPubKey: String?,
    val missedBlocks: Int,
    val totalBlocks: Int,
    val bondHeight: Int,
    val uptime: BigDecimal,
    val imgUrl: String?,
    val description: String?,
    val siteUrl: String?,
    val identity: String?
)

data class ValidatorDelegation(
    val address: String,
    val amount: BigInteger,
    val denom: String,
    val shares: BigDecimal?,
    val block: Int?,
    val endTime: DateTime?
)

data class ValidatorCommission(
    val bondedTokens: BigInteger,
    val bondedTokensDenom: String,
    val selfBonded: BigInteger,
    val selfBondedDenom: String,
    val delegatorBonded: BigInteger,
    val delegatorBondedDenom: String,
    val delegatorCount: Long,
    val totalShares: BigDecimal,
    val commissionRewards: BigDecimal,
    val commissionRewardsDenom: String,
    val commissionRate:	BigDecimal,
    val commissionMaxRate: BigDecimal,
    val commissionMaxChangeRate: BigDecimal
)
