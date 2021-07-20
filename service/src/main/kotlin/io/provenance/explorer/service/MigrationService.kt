package io.provenance.explorer.service

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.AccountRecord.Companion.update
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockCacheTable
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.service.async.AsyncCaching
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

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

    fun updateProposers(): Boolean {
        BlockProposerRecord.findMissingRecords().forEach { block ->
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
}
