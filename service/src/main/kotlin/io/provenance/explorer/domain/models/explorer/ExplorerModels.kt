package io.provenance.explorer.domain.models.explorer

import java.math.BigDecimal

data class BlockSummary(
    val height: Int,
    val hash: String,
    val time: String,
    val proposerAddress: String,
    val moniker: String,
    val icon: String,
    val votingPower: Int,
    val votingPowerTotal: Int,
    val numValidators: Int,
    val numValidatorsTotal: Int,
    val txNum: Int
)

data class Spotlight(
    val latestBlock: BlockSummary,
    val avgBlockTime: BigDecimal,
    val bondedTokenPercent: BigDecimal,
    val bondedTokenAmount: Long,
    val bondedTokenTotal: BigDecimal
)

data class GasStatistics(
    val time: String,
    val minGasPrice: Int,
    val maxGasPrice: Int,
    val averageGasPrice: BigDecimal
)
