package io.provenance.explorer.service

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.SigJoinType
import io.provenance.explorer.domain.entities.SignatureJoinRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxMessageRecord
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class MigrationService(private val txService: TransactionService, private val protoPrinter: JsonFormat.Printer) {

    private fun populateTxSignatures() = transaction {
        TxCacheRecord.all().forEach {
            it.txV2.tx.authInfo.signerInfosList.forEach { sig ->
                SignatureJoinRecord.insert(sig.publicKey, SigJoinType.TRANSACTION, it.txV2.txResponse.txhash)
            }
        }
    }

    private fun populateAccSignatures() = transaction {
        AccountRecord.all().forEach {
            SignatureJoinRecord.insert(it.baseAccount.pubKey, SigJoinType.ACCOUNT, it.accountAddress)
        }
    }

    fun populateSigs(): Boolean {
        populateTxSignatures()
        populateAccSignatures()
        return true
    }

    fun populateTxs(): Boolean {
        listOf(585724,585722,584292,569451,569447,569266,569262,569251,556956,556910,556907,556511,556337,556334,
            556245,556241,555781,555778,553299,553281,552820,552806)
            .forEach { txService.tryAddTxs(it) }
        return true
    }

    fun translateMsgAny(hash: String) = transaction {
        TxMessageRecord.findByHash(hash)
            .first()
            .let {
                MsgObj(
                    it.txMessageType.type,
                    OBJECT_MAPPER.readValue(protoPrinter.print(it.txMessage), ObjectNode::class.java)
                        .let { node ->
                            node.remove("@type")
                            node }
                )
            }
    }

}

data class MsgObj(
    val type: String,
    val msg: ObjectNode
)
