package io.provenance.explorer.domain.models.clients.pb

import com.fasterxml.jackson.databind.JsonNode
import io.provenance.explorer.domain.models.clients.DenomAmount
import io.provenance.explorer.domain.models.clients.PubKey
import io.provenance.explorer.domain.models.clients.TxLog

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
