package io.provenance.explorer.domain.models.explorer

import org.joda.time.DateTime
import java.math.BigDecimal
import java.math.BigInteger

data class BlockSummary(
    val height: Int,
    val hash: String,
    val time: String,
    val proposerAddress: String,
    val moniker: String,
    val icon: String?,
    val votingPower: CountTotal,
    val validatorCount: CountTotal,
    val txNum: Int
)

data class BlockProposer(
    var blockHeight: Int,
    var proposerOperatorAddress: String,
    var blockTimestamp: DateTime,
    var blockLatency: BigDecimal? = null
)

data class Spotlight(
    val latestBlock: BlockSummary,
    val avgBlockTime: BigDecimal,
    val bondedTokens: CountStrTotal,
    val totalTxCount: BigInteger,
    val totalAum: CoinStr
)

data class ValidatorMarketRate(
    val operatorAddress: String,
    val time: String,
    val minMarketRate: BigDecimal?,
    val maxMarketRate: BigDecimal?,
    val averageMarketRate: BigDecimal?
)

data class ChainMarketRate(
    val time: String,
    val minMarketRate: BigDecimal?,
    val maxMarketRate: BigDecimal?,
    val averageMarketRate: BigDecimal?
)

data class MarketRateAvg(
    val dataCount: Int,
    val minMarketRate: BigDecimal,
    val maxMarketRate: BigDecimal,
    val averageMarketRate: BigDecimal
)

data class GasStats(
    val date: String,
    val minGasUsed: Int,
    val maxGasUsed: Int,
    val avgGasUsed: Int,
    val stdDevGasUsed: Int,
    val messageType: String
)

data class ChainUpgrade(
    val upgradeHeight: Int,
    val upgradeName: String,
    val initialVersion: String,
    val currentVersion: String,
    val skipped: Boolean,
    var scheduled: Boolean = false,
    var releaseUrl: String
)

enum class PrefixType { VALIDATOR, ACCOUNT, SCOPE }

data class ChainPrefix(
    val type: PrefixType,
    val prefix: String
)

data class GithubReleaseData(
    val releaseVersion: String,
    val createdAt: String,
    val releaseUrl: String
)

data class MsgBasedFee(
    val msgTypeUrl: String,
    val additionalFee: CoinStr
)
