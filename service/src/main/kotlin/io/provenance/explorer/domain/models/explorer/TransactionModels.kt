package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import org.joda.time.DateTime
import java.math.BigDecimal
import java.math.BigInteger

data class TxData(
    val blockHeight: Int,
    val txHashId: Int?,
    val txHash: String,
    val txTimestamp: DateTime
)

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
    val toDate: DateTime?,
    val nftId: Int?,
    val nftType: String?,
    val nftUuid: String?
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
    val monikers: Map<String, String>
)

data class Gas(
    val gasUsed: Int,
    val gasWanted: Int,
    val gasLimit: BigInteger,
    val gasPrice: Double
)

data class TxHistory(val date: String, val numberTxs: Int)

data class TxHeatmapRaw(
    val dow: Int,
    val day: String,
    val hour: Int,
    val numberTxs: Int
)

data class TxHeatmapData(
    val hour: Int,
    val numberTxs: Int
)

data class TxHeatmap(
    val dow: Int,
    val day: String,
    val data: List<TxHeatmapData>
)

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
        )
    ),
    VALIDATION(
        "staking",
        listOf("create_validator", "edit_validator", "unjail", "withdraw_validator_commission")
    ),
    GOVERNANCE(
        "governance",
        listOf("submit_proposal", "deposit", "vote")
    ),
    TRANSFER(
        "transfer",
        listOf("send", "multisend", "transfer", "ibc_transfer")
    ),
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
        )
    ),
    NFT(
        "nft",
        listOf(
            "p8e_memorialize_contract_request",
            "write_p8e_contract_spec_request",
            "write_scope_request",
            "delete_scope_request",
            "add_scope_data_access_request",
            "delete_scope_data_access_request",
            "add_scope_owner_request",
            "delete_scope_owner_request",
            "write_session_request",
            "write_record_request",
            "delete_record_request",
            "write_scope_specification_request",
            "delete_scope_specification_request",
            "write_contract_specification_request",
            "delete_contract_specification_request",
            "write_record_specification_request",
            "delete_record_specification_request",
            "write_os_locator_request",
            "delete_os_locator_request",
            "modify_os_locator_request"
        )
    ),
    IBC(
        "ibc",
        listOf(
            "update_client",
            "channel_open_confirm",
            "channel_open_try",
            "channel_open_ack",
            "channel_open_init",
            "connection_open_confirm",
            "connection_open_try",
            "connection_open_ack",
            "connection_open_init",
            "channel_close_init",
            "channel_close_confirm",
            "create_client",
            "upgrade_client",
            "client_misbehaviour",
            "acknowledge_packet",
            "recv_packet",
            "ibc_transfer"
        )
    )
}

fun String.getCategoryForType() = MsgTypeSet.values().firstOrNull { it.types.contains(this) }

data class TxSummary(
    val txHash: String,
    val block: Int,
    val msg: MsgInfo,
    val monikers: Map<String, String>,
    val time: String,
    val fee: CoinStr,
    val signers: Signatures,
    val status: String
)

data class MsgInfo(
    val msgCount: Long,
    val displayMsgType: String
)

data class TxMessage(
    val type: String,
    val msg: ObjectNode
)

enum class DateTruncGranularity { DAY, HOUR }

enum class TxStatus { SUCCESS, FAILURE }

data class TxGov(
    val txHash: String,
    val txMsgType: String,
    val depositAmount: CoinStr?,
    val proposalType: String,
    val proposalId: Long,
    val proposalTitle: String,
    val block: Int,
    val txTime: String,
    val txFee: CoinStr,
    val signers: Signatures,
    val txStatus: String
)

data class TxGasVolume(
    val date: String,
    val gasWanted: BigInteger,
    val gasUsed: BigInteger,
    val feeAmount: BigDecimal
)
