package io.provenance.explorer.domain.models.explorer

import cosmos.gov.v1beta1.Gov
import cosmos.gov.v1beta1.QueryOuterClass
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

data class GasStatistics(
    val time: String,
    val minGasPrice: Int,
    val maxGasPrice: Int,
    val averageGasPrice: BigDecimal
)

data class Params(
    // probably need to change this name
    val votingParams: QueryOuterClass.QueryParamsResponse
)
