package io.provenance.explorer.service.utility

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.ErrorFinding
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.UnknownTxType
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import io.provenance.explorer.service.async.AsyncCaching
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class UtilityService(
    private val protoPrinter: JsonFormat.Printer
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
}

data class MsgObj(
    val type: String,
    val msg: ObjectNode
)
