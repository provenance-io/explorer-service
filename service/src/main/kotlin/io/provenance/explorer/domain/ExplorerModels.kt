package io.provenance.explorer.domain

import java.util.*

data class PagedResults<T>(val pages: Int, val results: List<T>)

data class RecentTx(val txHash: String, val time: String, val fee: String, val denomination: String, val type: String, val blockHeight: Int, val signer: String, val status: String)

data class RecentBlock(val height: Int, val txs_count: Int, val time: String)

data class Validators(val totalVotingPower: Int, val blockHeight: Int, val validators: List<ValidatorDetail>)

data class ValidatorDetail(val votingPower: Int, val moniker: String, val addressId: String, val uptime: Int)

data class BlockDetail(val height: Int, val time: String, val validatorHash: String, val moniker: String, val icon: String,
                       val votingPower: Int, val numValidators: Int, val txNum: Int, val bondedTokenPercent: Int, val bondedTokenAmount: Int,
                       val bondedTokenTotal: Int)

data class TxDetails(val height: Int, val gasUsed: Int, val gasWanted: Int, val gasLimit: Int, val gasPrice: Int, val time: String,
                     val status: String, val timestamp: String, val fee: String, val feeDenom: String, val signer: String,
                     val memo: String, val txType: String, val from: String, val amount: String, val denom: String, val to: String)

data class TxHistory(val day: String, var numberTxs: Int, var numberTxBlocks: Int, var maxHeight: Int, var minHeight: Int)
