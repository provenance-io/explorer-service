package io.provenance.explorer.domain

data class RecentTx(val txHash: String, val time: String, val fee: String, val denomination: String, val type: String)

data class RecentBlock(val height: Long, val txs_count: Int, val time: String)

data class BlockDetail(val height: Long, val time: String, val validatorHash: String, val moniker: String, val icon: String,
                       val votingPower: Int, val numValidators: Int, val txNum: Int, val bondedTokenPercent: Int, val bondedTokenAmount: Int,
                       val bondedTokenTotal: Int)

data class TxDetails(val height: Long, val gasUsed: Int, val gasWanted: Int, val gasLimit: Int, val gasPrice: Int, val time: String,
                     val status: String, val timestamp: String, val fee: String, val feeDenom: String, val signer: String,
                     val memo: String, val txType: String, val from: String, val amount: String, val denom: String, val to: String)

