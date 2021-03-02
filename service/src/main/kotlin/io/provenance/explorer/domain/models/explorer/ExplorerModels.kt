package io.provenance.explorer.domain.models.explorer

import cosmos.tx.v1beta1.ServiceOuterClass
import org.joda.time.DateTime
import java.math.BigDecimal

data class PagedResults<T>(val pages: Int, val results: List<T>)

data class RecentTx(
    val txHash: String,
    val time: String,
    val fee: BigDecimal,
    val denomination: String,
    val type: String,
    val blockHeight: Int,
    val signers: Signatures,
    val status: String,
    val errorCode: Int?,
    val codespace: String?
)

data class RecentBlock(
    val height: Int,
    val txNum: Int,
    val time: String,
    val proposerAddress: String,
    val votingPower: BigDecimal,
    val validatorsNum: Int,
    val validatorsTotal: Int
)

data class ValidatorSummary(
    val moniker: String,
    val addressId: String,
    val consensusAddress: String,
    val proposerPriority: Int,
    val uptime: BigDecimal,
    val votingPower: Int,
    val votingPowerPercent: BigDecimal,
    val commission: BigDecimal,
    val bondedTokens: Long,
    val bondedTokensDenomination: String,
    val selfBonded: BigDecimal,
    val selfBondedDenomination: String,
    val delegators: Int,
    val bondHeight: Int,
    val status: String
)

data class ValidatorDetails(
    val votingPower: Int,
    val votingPowerPercent: BigDecimal,
    val moniker: String,
    val operatorAddress: String,
    val ownerAddress: String,
    val consensusPubKey: String?,
    val missedBlocks: Int,
    val totalBlocks: Int,
    val bondHeight: Int,
    val uptime: BigDecimal,
    val imgUrl: String?,
    val description: String?,
    val siteUrl: String?,
    val identity: String?
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

data class TxDetails(
    val height: Int,
    val gasUsed: Int,
    val gasWanted: Int,
    val gasLimit: Int,
    val gasPrice: BigDecimal,
    val time: String,
    val status: String,
    val errorCode: Int?,
    val codespace: String?,
    val fee: BigDecimal,
    val feeDenomination: String,
    val signers: Signatures,
    val memo: String,
    val txType: String,
    val from: String,
    val amount: Int,
    val denomination: String,
    val to: String
)

data class TxHistory(val date: String, var numberTxs: Int)

data class TxFromCache(
    val tx: ServiceOuterClass.GetTxResponse,
    val type: String,
    val timestamp: DateTime
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
