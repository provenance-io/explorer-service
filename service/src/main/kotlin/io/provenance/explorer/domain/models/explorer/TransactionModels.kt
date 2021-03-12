package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigDecimal

data class TxDetails(
    val txHash: String,
    val height: Int,
    val gas: Gas,
    val time: String,
    val status: String,
    val errorCode: Int?,
    val codespace: String?,
    val errorLog: String?,
    val fee: Coin,
    val signers: Signatures,
    val memo: String,
    val msg: List<TxMessage>,
    val monikers: Map<String, String>
)

data class Gas(
    val gasUsed: Int,
    val gasWanted: Int,
    val gasLimit: Int,
    val gasPrice: BigDecimal
)

data class TxHistory(val date: String, var numberTxs: Int)

data class TxType(
    val module: String,
    val type: String
)

enum class MsgTypeSet(val mainCategory: String, val types: List<String>) {
    DELEGATION(
        "staking",
        listOf(
            "begin_redelegate",
            "begin_unbonding",
            "delegate",
            "fund_community_pool",
            "set_withdraw_address",
            "withdraw_delegator_reward"
        )),
    VALIDATION(
        "staking",
        listOf("create_validator", "edit_validator", "unjail", "withdraw_validator_commission")),
    GOVERNANCE(
        "governance",
        listOf("submit_proposal", "deposit", "vote")),
    TRANSFER(
        "transfer",
        listOf("send", "multisend")),
    ASSET(
        "asset",
        listOf(
            "addmarker",
            "addaccess",
            "deleteaccess",
            "finalize",
            "activate",
            "cancel",
            "delete",
            "mint",
            "burn",
            "withdraw",
            "transfer",
            "setmetadata",
            "add_attribute",
            "delete_attribute"
        )
    )
}

fun String.getCategoryForType() = MsgTypeSet.values().firstOrNull { it.types.contains(this) }

data class TxSummary(
    val txHash: String,
    val block: Int,
    val msg: List<TxMessage>,
    val monikers: Map<String, String>,
    val time: String,
    val fee: Coin,
    val signers: Signatures,
    val status: String,
)

data class TxMessage(
    val type: String,
    val msg: ObjectNode
)

enum class DateTruncGranularity { DAY, HOUR }

enum class TxStatus { SUCCESS, FAILURE }
