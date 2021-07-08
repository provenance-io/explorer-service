package io.provenance.explorer.domain.models.explorer

import java.math.BigDecimal
import java.math.BigInteger

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
    val bondedTokens: CountStrTotal,
    val totalTxCount: BigInteger
)

// we want to use this for specific message types
data class GasStatistics(
    val time: String,
    val messageType: String, // idk if we want to use a string or some typed variable somewhere?
    val minGasPrice: Int,
    val maxGasPrice: Int,
    val averageGasPrice: BigDecimal,
    val stdDevGasPrice: BigDecimal,
)
