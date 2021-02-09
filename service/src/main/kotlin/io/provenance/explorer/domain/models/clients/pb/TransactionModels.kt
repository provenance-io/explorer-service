package io.provenance.explorer.domain.models.clients.pb

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import io.provenance.explorer.domain.models.clients.CustomPubKey
import io.provenance.explorer.domain.models.clients.DenomAmount
import io.provenance.explorer.domain.models.clients.Pagination
import io.provenance.explorer.domain.models.clients.PubKey

data class PbTxSearchResponse(
    val totalCount: String,
    val count: String,
    val pageNumber: String,
    val limit: String,
    val txs: List<PbTransaction>
)

data class PbTransaction(
    val height: String,
    val txhash: String,
    val codespace: String?,
    val code: Int?,
    val logs: List<TxLog>?,
    val gasWanted: String,
    val gasUsed: String,
    val tx: Tx,
    val timestamp: String
)

data class Tx(val type: String, val value: TxValue)

data class TxValue(val msg: List<TxMsg>, val fee: TxFee, val signatures: List<TxSignature>, val memo: String)

data class TxMsg(val type: String, val value: JsonNode)

data class TxFee(val gas: String, val amount: List<DenomAmount>)

data class TxSignature(val pubKey: PubKey, val signature: String)

data class TxLog(val msgIndex: Int?, val log: String?, val events: List<TxEvent>)

data class TxEvent(val type: String, val attributes: List<TxEvenAttribute>)

data class TxEvenAttribute(val key: String, val value: String)




//////////// V2

data class TxPaged(val txs: List<TxV2>, val txResponses: List<TxResponse>, val pagination: Pagination)

data class TxSingle(val tx: TxV2, val txResponse: TxResponse)

data class TxV2(val body: TxBody, val authInfo: TxAuthInfo, val signatures: List<String>)

data class TxBody(
    val messages: List<TxMessage>,
    val memo: String,
    val timeoutHeight: String,
    val extensionOptions: List<String>,
    val nonCriticalExtensionOptions: List<String>
)

sealed class TxMessage { abstract val type: String }

// Only used for unknown transaction types until they can be enumerated
data class UnknownTxMessage(
    override val type: String,
    val txObject: JsonNode
) : TxMessage()

data class SendTxMessage(
    @JsonProperty("@type") override val type: String,
    val fromAddress: String,
    val toAddress: String,
    val amount: List<DenomAmount>
) : TxMessage() {
    companion object {
        const val TYPE = "MsgSend"
    }
}

data class VoteTxMessage(
    @JsonProperty("@type") override val type: String,
    val proposalId: String,
    val voter: String,
    val option: String
) : TxMessage() {
    companion object {
        const val TYPE = "MsgVote"
    }
}

data class SubmitProposalTxMessage(
    @JsonProperty("@type") override val type: String,
    val content: TxBodyMessageContent,
    val initialDeposit: List<DenomAmount>,
    val proposer: String
) : TxMessage() {
    companion object {
        const val TYPE = "MsgSubmitProposal"
    }
}

data class TxBodyMessageContent(
    @JsonProperty("@type") val type: String,
    val title: String,
    val description: String,
    val changes: List<TxBodyMessageContentChanges>
)

data class TxBodyMessageContentChanges(
    val subspace: String,
    val key: String,
    val value: String
)

data class TxAuthInfo(
    val signerInfos: List<TxAuthInfoSigner>,
    val fee: TxAuthInfoFee
)

data class TxAuthInfoSigner(
    val publicKey: CustomPubKey,
    val modeInfo: TxAuthInfoSignerModeInfo,
    val sequence: String
)

data class TxAuthInfoSignerModeInfo(val single: TxAuthInfoSignerModeInfoSingle?)

data class TxAuthInfoSignerModeInfoSingle(val mode: String)

data class TxAuthInfoFee(
    val amount: List<DenomAmount>,
    val gasLimit: String,
    val payer: String,
    val granter: String
)

data class TxResponse(
    val height: String,
    val txhash: String,
    val codespace: String,
    val code: Int,
    val data: String,
    val rawLog: String,
    val logs: List<TxLog>,
    val info: String,
    val gasWanted: String,
    val gasUsed: String,
    val tx: String?,
    val timestamp: String
)
