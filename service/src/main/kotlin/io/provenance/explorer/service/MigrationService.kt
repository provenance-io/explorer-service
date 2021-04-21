package io.provenance.explorer.service

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.ErrorFinding
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.SigJoinType
import io.provenance.explorer.domain.entities.SignatureJoinRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTable
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.UnknownTxType
import io.provenance.explorer.domain.extensions.toDbHash
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import io.provenance.explorer.service.async.AsyncCaching
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.selectAllBatched
import org.jetbrains.exposed.sql.selectBatched
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class MigrationService(
    private val asyncCaching: AsyncCaching,
    private val validatorService: ValidatorService,
    private val accountService: AccountService,
    private val assetService: AssetService,
    private val markerClient: MarkerGrpcClient,
    private val protoPrinter: JsonFormat.Printer
) {

    protected val logger = logger(MigrationService::class)

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
        val pageLimit = 200
        var offset = 0
        while (offset < origCount) {
            transaction {
                BlockCacheRecord.getBlocksWithTxs(pageLimit, offset).forEach block@{ block ->
                    if (BlockProposerRecord.findById(block.height) == null)
                        validatorService.saveProposerRecord(block.block, block.blockTimestamp, block.height)
                    asyncCaching.saveTxs(block.block)
                }
                offset += pageLimit
            }
        }
        return true
    }

    fun updateProposers(): Boolean {
        BlockProposerRecord.findMissingRecords().forEach { block ->
            validatorService.saveProposerRecord(block.block, block.blockTimestamp, block.height)
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
                            node
                        }
                )
            }
    }

    fun updateValidatorsCache() = validatorService.updateValidatorsAtHeight()

    fun updateAccountJoins() = accountService.updateAccountJoins()

    fun compareDenomObject(denom: String) = transaction {
        val data = markerClient.getMarkerDetail(denom)
        val record = MarkerCacheRecord.findByDenom(denom)!!
        data == record.data
    }

    fun transformTxMessageHashes() {
        val limit = 10000
        var offset = 1
        val total = transaction { TxMessageRecord.count() }

        var findInd = 0
        var commitInd = 0
        while (offset < total) {
            val last = offset+limit-1
            logger.info("fetching records for $offset to $last")
            val list =
                transaction {
                    TxMessageTable.selectBatched(where = { TxMessageTable.id.between(offset, last) })
                        .map { batch ->
                            findInd++
                            batch.mapNotNull { res ->
                                val row = TxMessageRecord.wrapRow(res)
                                val hash = row.txMessage.value.toDbHash()

                                if (row.txMessageHash != hash) row.apply { this.txMessageHash = hash }
                                else null
                            }.also { logger.debug("Updated batch $findInd") }
                        }
                }
            logger.debug("wrapped records for $offset to $last")

            list.map { batch ->
                commitInd++
                transaction trans@{
                    BatchUpdateStatement(TxMessageTable).apply {
                        batch.forEach {
                            addBatch(it.id)
                            this[TxMessageTable.txMessageHash] = it.txMessageHash
                        }
                        execute(this@trans)
                    }.also { logger.debug("Committed batch $commitInd") }
                }
            }

            logger.info("committed records for $offset to $last")
            offset += limit
        }
    }
}

data class MsgObj(
    val type: String,
    val msg: ObjectNode
)
