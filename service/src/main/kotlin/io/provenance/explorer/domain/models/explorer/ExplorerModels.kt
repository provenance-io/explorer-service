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
    var minGasFee: Double? = null,
    var blockLatency: BigDecimal? = null
)

data class Spotlight(
    val latestBlock: BlockSummary,
    val avgBlockTime: BigDecimal,
    val bondedTokens: CountStrTotal,
    val totalTxCount: BigInteger,
    val totalAum: CoinStr
)

data class GasStatistics(
    val time: String,
    val minGasPrice: BigDecimal,
    val maxGasPrice: BigDecimal,
    val averageGasPrice: BigDecimal
)

data class GasStats(
    val date: String,
    val minGasPrice: Int,
    val maxGasPrice: Int,
    val avgGasPrice: Int,
    val stdDevGasPrice: Int,
    val messageType: String
)

data class ChainUpgrade(
    val upgradeHeight: Int,
    val upgradeName: String,
    val initialVersion: String,
    val currentVersion: String,
    val skipped: Boolean,
    var scheduled: Boolean = false
)

enum class PrefixType { VALIDATOR, ACCOUNT, SCOPE }

data class ChainPrefix(
    val type: PrefixType,
    val prefix: String
)
