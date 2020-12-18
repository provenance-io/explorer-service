package io.provenance.explorer.domain

import java.math.BigDecimal

data class PagedResults<T>(val pages: Int, val results: List<T>)

data class RecentTx(val txHash: String, val time: String, val fee: BigDecimal, val denomination: String, val type: String, val blockHeight: Int, val signer: String, val status: String, val errorCode: Int?, val codespace: String?)

data class RecentBlock(val height: Int, val txNum: Int, val time: String, val proposerAddress: String, val votingPower: BigDecimal, val validatorsNum: Int, val validatorsTotal: Int)

data class Validators(val totalVotingPower: Int, val blockHeight: Int, val validators: List<ValidatorDetails>)

data class ValidatorSummary(val moniker: String, val addressId: String, val consensusAddress:String, val proposerPriority: Int, val uptime: BigDecimal, val votingPower: Int )

data class ValidatorDetails(val votingPower: Int, val moniker: String, val operatorAddress: String, val ownerAddress: String, val consensusPubKey: String, val missedBlocks: Int, val totalBlocks: Int, val boundHeight: Int, val uptime: BigDecimal)

data class BlockDetail(val height: Int, val hash: String, val time: String, val proposerAddress: String, val moniker: String, val icon: String,
                       val votingPower: Int, val numValidators: Int, val txNum: Int, val bondedTokenPercent: Int, val bondedTokenAmount: Int,
                       val bondedTokenTotal: Int)

data class TxDetails(val height: Int, val gasUsed: Int, val gasWanted: Int, val gasLimit: Int, val gasPrice: BigDecimal, val time: String,
                     val status: String, val errorCode: Int?, val codespace: String?, val fee: BigDecimal, val feeDenomination: String, val signer: String,
                     val memo: String, val txType: String, val from: String, val amount: Int, val denomination: String, val to: String)

data class TxHistory(val date: String, var numberTxs: Int)

data class Spotlight(val latestBlock: BlockDetail, val avgBlockTime: BigDecimal)