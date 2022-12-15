package io.provenance.explorer.model

import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.CountStrTotal
import io.provenance.explorer.model.base.CountTotal
import org.joda.time.DateTime
import java.math.BigDecimal

data class ValidatorSummary(
    val moniker: String,
    val addressId: String,
    val consensusAddress: String,
    val proposerPriority: Int?,
    val votingPower: CountTotal?,
    val commission: String,
    val bondedTokens: CountStrTotal,
    val delegators: Long?,
    val status: String,
    val unbondingHeight: Long?,
    val imgUrl: String?,
    val hr24Change: String?,
    val uptime: BigDecimal
)

data class ValidatorSummaryAbbrev(
    val moniker: String,
    val addressId: String,
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
    val status: String,
    val unbondingHeight: Long?,
    val jailedUntil: DateTime?,
    val removed: Boolean
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

data class CommissionList(
    val rate: String,
    val blockHeight: Int
)

data class ValidatorCommissionHistory(
    val operatorAddress: String,
    val commissionList: List<CommissionList>
)

data class ValidatorMarketRate(
    val operatorAddress: String,
    val time: String,
    val minMarketRate: BigDecimal?,
    val maxMarketRate: BigDecimal?,
    val averageMarketRate: BigDecimal?
)

data class BlockLatencyData(
    val proposer: String,
    val data: Map<Int, BigDecimal>,
    val averageLatency: BigDecimal
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
    var moniker: String?,
    var currentState: ValidatorState?
)

enum class ValidatorState { ACTIVE, CANDIDATE, JAILED, REMOVED, ALL }

data class MissedBlocksTimeframe(
    val fromHeight: Int,
    val toHeight: Int,
    val addresses: List<ValidatorMissedBlocks>
)

data class ValidatorUptimeStats(
    val validator: ValidatorMoniker,
    val uptimeCount: Int,
    val uptimeCountPercentage: String,
    val missedCount: Int,
    val missedCountPercentage: String
)

data class UptimeDataSet(
    val fromHeight: Long,
    val toHeight: Long,
    val blockWindowCount: Long,
    val slashedBlockCount: Long,
    val slashedPercentage: String,
    val avgUptimeCount: Int,
    val avgUptimeCountPercentage: String,
    val validatorsAtRisk: List<ValidatorUptimeStats>
)
