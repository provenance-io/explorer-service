package io.provenance.explorer.service

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.AccountRecord.Companion.update
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockCacheTable
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.TxCacheTable
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTable
import io.provenance.explorer.domain.extensions.execAndMap
import io.provenance.explorer.domain.extensions.mapper
import io.provenance.explorer.service.async.AsyncCaching
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.sql.ResultSet

@Service
class MigrationService(
    private val asyncCaching: AsyncCaching,
    private val validatorService: ValidatorService
) {

    protected val logger = logger(MigrationService::class)

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

    fun updateBlocks(blocks: List<Int>) =
        blocks.forEach { block ->
            transaction { BlockCacheRecord.findById(block) }?.let {
                asyncCaching.saveTxs(it.block)
            }
        }.let { true }

    fun updateProposers(min: Int, max: Int, limit: Int): Boolean {
        BlockProposerRecord.findMissingRecords(min, max, limit).forEach { block ->
            validatorService.saveProposerRecord(block.block, block.blockTimestamp, block.height)
        }
        return true
    }

    fun updateValidatorsCache() = validatorService.updateValidatorsAtHeight().let { true }

    fun updateAccounts(list: List<String>) = transaction {
        AccountRecord.findListByAddress(list).forEach { it.update(it.data!!) }
    }

    fun updateMissedBlocks(startHeight: Int, endHeight: Int, inc: Int) {
        logger.info("Start height: $startHeight")
        var start = startHeight
        while (start <= endHeight) {
            transaction {
                logger.info("Fetching $start to ${start + inc - 1}")
                BlockCacheRecord.find { BlockCacheTable.id.between(start, start + inc - 1) }
                    .orderBy(Pair(BlockCacheTable.id, SortOrder.ASC)).forEach {
                        validatorService.saveMissedBlocks(it.block)
                    }
            }
            start += inc
        }
        logger.info("End height: $endHeight")
    }

    fun updateTxMessageMsgIdx(startId: Int, endId: Int, inc: Int) {
        logger.info("Start id: $startId")
        transaction {
            val list = transaction {
                val query = """select q.*, tc.tx_v2 json from (
                        select tm.tx_hash_id, count(tm.id) msgCount, min(tm.msg_idx) minIdx, max(tm.msg_idx) maxIdx
                        from tx_message tm
                        where tm.tx_hash_id between $startId and $endId
                        group by tm.tx_hash_id
                    ) q
                    join tx_cache tc on q.tx_hash_id = tc.id
                    where msgCount > 1 and minIdx = maxIdx
                    order by tx_hash_id
                    limit $inc
                """.trimIndent()
                query.execAndMap { it.toTxMessageMsgIdxResult() }
            }

            logger.info("deleting tx messages")
            transaction { TxMessageTable.deleteWhere { TxMessageTable.txHashId inList list.map { it.txHashId } } }
            list.forEach { obj ->
                logger.info("saving tx messages")
                asyncCaching.saveMessages(EntityID(obj.txHashId, TxCacheTable), obj.json)
            }
        }
        logger.info("End id: $endId")
    }

    fun updateTxMessageCount(min: Int, max: Int, limit: Int) {
        logger.info("Finding tx hashes")
        val list = transaction {
            val query = """select id, hash, height, json, txCount, tmCount from (
                    select tc.id,
                         tc.hash,
                         tc.height,
                         tc.tx_v2 json,
                         jsonb_array_length((tc.tx_v2 -> 'tx' -> 'body' ->> 'messages')::jsonb) txCount,
                         count(tm.id)                                                           tmCount
                    from tx_cache tc
                           join tx_message tm on tm.tx_hash_id = tc.id
                            where tc.id between $min and $max
                    group by tc.id
                              ) q
                    where txCount != tmCount
                    limit $limit
            """.trimIndent()
            query.execAndMap { it.toTxMessageCountResult() }
        }
        val recLimit = 200
        logger.info("mapping tx hashes")
        list.forEach { obj ->
            var offset = 0
            val keepList: MutableList<String> = mutableListOf()
            val deleteList: MutableList<Int> = mutableListOf()
            if (obj.txCount > obj.tmCount) {
                val recList =
                    transaction { TxMessageRecord.findByHashIdPaginated(obj.txHashId, listOf(), recLimit, offset) }
                deleteList.addAll(recList.map { it.id.value })
                transaction { TxMessageTable.deleteWhere { TxMessageTable.id inList deleteList } }
                logger.info("saving tx messages")
                asyncCaching.saveMessages(EntityID(obj.txHashId, TxCacheTable), obj.json)
            } else {
                var recList =
                    transaction { TxMessageRecord.findByHashIdPaginated(obj.txHashId, listOf(), recLimit, offset) }
                do {
                    recList.forEach { rec ->
                        if (keepList.contains(rec.txMessageHash))
                            deleteList.add(rec.id.value)
                        else keepList.add(rec.txMessageHash)
                    }
                    offset += recLimit
                    recList =
                        transaction { TxMessageRecord.findByHashIdPaginated(obj.txHashId, listOf(), recLimit, offset) }
                } while (recList.size > 0)

                logger.info("deleting tx messages")
                if (keepList.size == obj.txCount)
                    transaction { TxMessageTable.deleteWhere { TxMessageTable.id inList deleteList } }
                else logger.warn("Not enough msgs found for txHashId ${obj.txHashId}")
            }
        }
    }
}

data class TxMessageMsgIdxResult(
    val txHashId: Int,
    val msgCount: Int,
    val minIdx: Int,
    val maxIdx: Int,
    val json: ServiceOuterClass.GetTxResponse
)

fun ResultSet.toTxMessageMsgIdxResult() =
    TxMessageMsgIdxResult(
        this.getInt("tx_hash_id"),
        this.getInt("msgCount"),
        this.getInt("minIdx"),
        this.getInt("maxIdx"),
        this.getString("json").mapper(ServiceOuterClass.GetTxResponse::class.java),
    )

data class TxMessageCountResult(
    val txHashId: Int,
    val txCount: Int,
    val tmCount: Int,
    val json: ServiceOuterClass.GetTxResponse
)

fun ResultSet.toTxMessageCountResult() =
    TxMessageCountResult(
        this.getInt("id"),
        this.getInt("txCount"),
        this.getInt("tmCount"),
        this.getString("json").mapper(ServiceOuterClass.GetTxResponse::class.java),
    )
