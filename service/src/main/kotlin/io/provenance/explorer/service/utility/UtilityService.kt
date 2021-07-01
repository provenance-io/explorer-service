package io.provenance.explorer.service.utility

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.ErrorFinding
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.UnknownTxType
import io.provenance.explorer.domain.extensions.fromBase64
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class UtilityService(
    private val protoPrinter: JsonFormat.Printer,
    private val markerClient: MarkerGrpcClient
) {

    protected val logger = logger(UtilityService::class)

    // Updates a TxMsgType with the given info
    fun updateTxMsgType(records: List<UnknownTxType>) = transaction {
        records.forEach { record -> TxMessageTypeRecord.insert(record.type, record.module, record.protoType) }
        "Updated"
    }

    // Retrieves common missing info
    fun getErrors() =
        mapOf(
            "txErrors" to ErrorFinding.getTxErrors(),
            "unknownTxMsgTypes" to ErrorFinding.getUnknownTxTypes()
        )

    // Translates Proto to json, found by tx hash
    fun translateMsgAny(hash: String) = transaction {
        TxMessageRecord.findByHash(hash)
            .first()
            .let {
                MsgObj(
                    it.txMessageType.type,
                    OBJECT_MAPPER.readValue(protoPrinter.print(it.txMessage), ObjectNode::class.java)
                        .let { node ->
                            node.remove("@type")
                            node
                        }
                )
            }
    }

    // searches for accounts that may or may not have the denom balance
    fun searchAccountsForDenom(accounts: List<String>, denom: String): List<Map<String, String>> {
        var offset = 0
        val limit = 100

        val results = markerClient.getMarkerHolders(denom, offset, limit)
        val total = results.pagination?.total ?: results.balancesCount.toLong()
        val holders = results.balancesList.toMutableList()

        while (holders.count() < total) {
            offset += limit
            markerClient.getMarkerHolders(denom, offset, limit).let { holders.addAll(it.balancesList) }
        }

        val map = holders.associateBy { it.address }

        return accounts.toSet().map { a ->
            mapOf("address" to a, denom to (map[a]?.coinsList?.firstOrNull { c -> c.denom == denom }?.amount ?: "Nothing"))
        }
    }

    fun stringToJson(str: String) = str.toObjectNode()

    fun decodeToString(str: String) = str.fromBase64() // do I want to get rid of this?
}

data class MsgObj(
    val type: String,
    val msg: ObjectNode
)


