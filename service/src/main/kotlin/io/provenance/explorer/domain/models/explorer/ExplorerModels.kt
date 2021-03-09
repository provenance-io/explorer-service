package io.provenance.explorer.domain.models.explorer

import java.math.BigDecimal

data class BlockSummary(
    val height: Int,
    val hash: String,
    val time: String,
    val proposerAddress: String,
    val moniker: String,
    val icon: String,
    val votingPower: CountTotal,
    val validatorCount: CountTotal,
    val txNum: Int
)

data class Spotlight(
    val latestBlock: BlockSummary,
    val avgBlockTime: BigDecimal,
    val bondedTokens: BondedTokens
)

data class GasStatistics(
    val time: String,
    val minGasPrice: Int,
    val maxGasPrice: Int,
    val averageGasPrice: BigDecimal
)
