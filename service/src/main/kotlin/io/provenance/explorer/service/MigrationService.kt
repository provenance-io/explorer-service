package io.provenance.explorer.service

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.service.async.AsyncCaching
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
}
