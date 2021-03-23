package io.provenance.explorer.service

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.ErrorFinding
import io.provenance.explorer.domain.entities.SigJoinType
import io.provenance.explorer.domain.entities.SignatureJoinRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.UnknownTxType
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class MigrationService(
    private val txService: TransactionService,
    private val validatorService: ValidatorService,
    private val protoPrinter: JsonFormat.Printer
) {

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

    fun updateTxs(): Boolean {
        val origCount = BlockCacheRecord.getCountWithTxs()
        var count = origCount
        val pageLimit = 200
        var offset = 0
        while (count > 0) {
            BlockCacheRecord.getBlocksWithTxs(200, 0).forEach block@{ block ->
                if (BlockProposerRecord.findById(block.height)?.minGasFee != null)
                    return@block
                validatorService.saveProposerRecord(block.block, block.blockTimestamp, block.height)
                var txs = TxCacheRecord.findByHeight(block.height)
                if (origCount > txs.count()) {
                    txService.addTxsToCache(block.height, origCount.toInt())
                    txs = TxCacheRecord.findByHeight(block.height)
                }
                txService.calculateBlockTxFee(txs.map { it.txV2.tx }, block.height)
            }
            count -= pageLimit
            offset += offset
        }

        return true
    }

    fun updateTxMsgType(record: UnknownTxType) = transaction {
        TxMessageTypeRecord.insert(record.type, record.module, record.protoType)
        "Updated"
    }

    fun getErrors() =
        mapOf(
            "txErrors" to ErrorFinding.getTxErrors(),
            "unknownTxMsgTypes" to ErrorFinding.getUnknownTxTypes()
        )

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
