package io.provenance.explorer.model

import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.CountStrTotal
import java.math.BigDecimal
import java.math.BigInteger

data class Spotlight(
    val latestBlock: BlockSummary,
    val avgBlockTime: BigDecimal,
    val bondedTokens: CountStrTotal,
    val totalTxCount: BigInteger,
    val totalAum: CoinStr
)

data class GasStats(
    val date: String,
    val minGasUsed: Int,
    val maxGasUsed: Int,
    val avgGasUsed: Int,
    val stdDevGasUsed: Int,
    val messageType: String
)

data class TxGasVolume(
    val date: String,
    val gasWanted: BigInteger,
    val gasUsed: BigInteger,
    val feeAmount: BigDecimal
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

data class MsgBasedFee(
    val msgTypeUrl: String,
    val additionalFee: CoinStr
)

data class ChainAum(
    val dateTime: String,
    val denom: String,
    val amount: BigDecimal
)
