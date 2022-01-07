package io.provenance.explorer.service.utility

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.sql.Distinct
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockCacheTable
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxCacheTable
import io.provenance.explorer.domain.entities.TxMessageTable
import io.provenance.explorer.service.AccountService
import io.provenance.explorer.service.ValidatorService
import io.provenance.explorer.service.async.AsyncCachingV2
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class MigrationService(
    private val asyncCaching: AsyncCachingV2,
    private val validatorService: ValidatorService,
    private val accountService: AccountService
) {

    protected val logger = logger(MigrationService::class)

    fun updateProposers(min: Int, max: Int, limit: Int): Boolean {
        BlockProposerRecord.findMissingRecords(min, max, limit).forEach { block ->
            validatorService.saveProposerRecord(block.block, block.blockTimestamp, block.height)
        }
        return true
    }

    fun updateAccounts(list: List<String>) = transaction {
        list.forEach { accountService.saveAccount(it) }
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

    fun reprocessTxsFromTypeId(types: List<Int>, min: Int, max: Int, inc: Int) {
        logger.info("Start height: $min")
        var start = min
        val columns: MutableList<Expression<*>> =
            mutableListOf(Distinct(TxCacheTable.id, IntegerColumnType()).alias("dist"))
        columns.addAll(TxCacheTable.columns.toMutableList())
        while (start <= max) {
            logger.info("Fetching $start to ${start + inc - 1}")
            transaction {
                val query = TxMessageTable
                    .innerJoin(TxCacheTable, { TxMessageTable.txHashId }, { TxCacheTable.id })
                    .slice(columns)
                    .select { TxMessageTable.txMessageType inList types }
                    .andWhere { TxCacheTable.height.between(start, start + inc - 1) }
                    .orderBy(TxCacheTable.height)
                TxCacheRecord.wrapRows(query).toSet()
                    .forEach {
                        logger.info("Tx Hash Id : ${it.id.value}")
                        asyncCaching.addTxToCache(it.txV2, it.txTimestamp)
                    }
            }
            start += inc
        }
        logger.info("End height: $max")
    }

    fun reprocessTxsFromHeight(heights: List<Int>) {
        logger.info("Start height: ${heights.minOrNull()}")
        transaction {
            TxCacheRecord.find { TxCacheTable.height inList heights }
                .orderBy(TxCacheTable.height to SortOrder.ASC)
                .forEach {
                    logger.info("Tx Hash Id : ${it.id.value}")
                    asyncCaching.addTxToCache(it.txV2, it.txTimestamp)
                }
        }
        logger.info("End height: ${heights.maxOrNull()}")
    }
}
