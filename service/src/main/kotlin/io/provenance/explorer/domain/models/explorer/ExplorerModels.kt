package io.provenance.explorer.domain.models.explorer

import java.math.BigDecimal


data class RecentBlock(
    val height: Int,
    val txNum: Int,
    val time: String,
    val proposerAddress: String,
    val votingPower: BigDecimal,
    val validatorsNum: Int,
    val validatorsTotal: Int
)

data class BlockDetail(
    val height: Int,
    val hash: String,
    val time: String,
    val proposerAddress: String,
    val moniker: String,
    val icon: String,
    val votingPower: Int,
    val numValidators: Int,
    val txNum: Int
)

data class Spotlight(
    val latestBlock: BlockDetail,
    val avgBlockTime: BigDecimal,
    val bondedTokenPercent: BigDecimal,
    val bondedTokenAmount: Long,
    val bondedTokenTotal: BigDecimal
)

data class GasStatistics(
    val time: String,
    val operationType: String,
    val minGasPrice: Long,
    val maxGasPrice: Long,
    val averageGasPrice: BigDecimal
)
