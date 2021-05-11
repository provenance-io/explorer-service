package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import org.joda.time.DateTime

data class TxQueryParams(
    val addressId: Int?,
    val addressType: String?,
    val address: String?,
    val markerId: Int?,
    val denom: String?,
    val msgTypes: List<Int>,
    val txHeight: Int?,
    val txStatus: TxStatus?,
    val count: Int,
    val offset: Int,
    val fromDate: DateTime?,
    val toDate: DateTime?
)

fun TxQueryParams.onlyTxQuery() = addressId == null && markerId == null && msgTypes.isEmpty()
fun TxQueryParams.onlyAddress() = addressId != null && markerId == null && msgTypes.isEmpty()

data class TxDetails(
    val txHash: String,
    val height: Int,
    val gas: Gas,
    val time: String,
    val status: String,
    val errorCode: Int?,
    val codespace: String?,
    val errorLog: String?,
    val fee: CoinStr,
    val signers: Signatures,
    val memo: String,
    val msg: List<TxMessage>,
    val monikers: Map<String, String>
)

data class Gas(
    val gasUsed: Int,
    val gasWanted: Int,
    val gasLimit: Int,
    val gasPrice: Double
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
        listOf("send", "multisend", "transfer")),
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
            "setmetadata",
            "add_attribute",
            "delete_attribute"
        )),
    NFT(
        "nft",
        listOf(
            "memorialize_contract_request",
            "change_ownership_request",
            "add_scope_request",
            "delete_scope_request",
            "add_session_request",
            "add_record_request",
            "delete_record_request",
            "add_scope_specification_request",
            "delete_scope_specification_request",
            "add_contract_specification_request",
            "delete_contract_specification_request",
            "add_record_specification_request",
            "delete_record_specification_request"
        )),
    IBC(
        "ibc",
        listOf(
            "transfer",
            "update_client",
            "channel_open_confirm",
            "channel_open_try",
            "connection_open_confirm",
            "connection_open_try",
            "create_client"
        ))
}

fun String.getCategoryForType() = MsgTypeSet.values().firstOrNull { it.types.contains(this) }

data class TxSummary(
    val txHash: String,
    val block: Int,
    val msg: List<TxMessage>,
    val monikers: Map<String, String>,
    val time: String,
    val fee: CoinStr,
    val signers: Signatures,
    val status: String,
)

data class TxMessage(
    val type: String,
    val msg: ObjectNode
)

enum class DateTruncGranularity { DAY, HOUR }

enum class TxStatus { SUCCESS, FAILURE }
