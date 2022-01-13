package io.provenance.explorer.domain.models.explorer

import cosmos.staking.v1beta1.Staking
import org.joda.time.DateTime
import java.math.BigDecimal

data class ValidatorSummary(
    val moniker: String,
    val addressId: String,
    val consensusAddress: String,
    val proposerPriority: Int?,
    val uptime: BigDecimal?,
    val votingPower: CountTotal?,
    val commission: String,
    val bondedTokens: CountStrTotal,
    val selfBonded: CountStrTotal,
    val delegators: Long?,
    val bondHeight: Long?,
    val status: String,
    val currentGasFee: Double?,
    val unbondingHeight: Long?,
    val imgUrl: String?
)

data class ValidatorSummaryAbbrev(
    val moniker: String,
    val addressId: String,
    val votingPower: CountTotal?,
    val commission: String,
    val imgUrl: String?
)

data class ValidatorDetails(
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

data class ValidatorCommission(
    val bondedTokens: CountStrTotal,
    val selfBonded: CountStrTotal,
    val delegatorBonded: CountStrTotal,
    val delegatorCount: Long,
    val totalShares: String,
    val commissionRewards: CoinStr,
    val commissionRate: CommissionRate
)

data class CommissionRate(
    val rate: String,
    val maxRate: String,
    val maxChangeRate: String
)

data class CurrentValidatorState(
    val operatorAddrId: Int,
    val operatorAddress: String,
    val blockHeight: Int,
    val moniker: String,
    val status: String,
    val jailed: Boolean,
    val tokenCount: BigDecimal,
    val json: Staking.Validator,
    val accountAddr: String,
    val consensusAddr: String,
    val consensusPubKey: String
)

data class BlockLatencyData(
    val proposer: String,
    val data: Map<Int, BigDecimal>,
    val averageLatency: BigDecimal
)

data class ValidatorAtHeight(
    val moniker: String,
    val addressId: String,
    val consensusAddress: String,
    val proposerPriority: Int?,
    val votingPower: CountTotal?,
    val imgUrl: String?,
    val isProposer: Boolean = false,
    val didVote: Boolean = true
)

data class MissedBlocksTimeframe(
    val fromHeight: Int,
    val toHeight: Int,
    val addresses: List<ValidatorMissedBlocks>
)

data class ValidatorMissedBlocks(
    val validator: ValidatorMoniker,
    val missedBlocks: List<MissedBlockSet> = listOf()
)

data class MissedBlockSet(
    val min: Int,
    val max: Int,
    val count: Int
)

data class ValidatorMoniker(
    val valConsAddress: String,
    var operatorAddr: String?,
    var moniker: String?
)

data class MissedBlockPeriod(
    val validator: ValidatorMoniker,
    val blocks: List<Int>
)
