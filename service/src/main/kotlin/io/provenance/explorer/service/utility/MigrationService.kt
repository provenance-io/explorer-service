package io.provenance.explorer.service.utility

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockCacheTable
import io.provenance.explorer.service.AccountService
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.ValidatorService
import io.provenance.explorer.service.async.AsyncCachingV2
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class MigrationService(
    private val asyncCaching: AsyncCachingV2,
    private val validatorService: ValidatorService,
    private val accountService: AccountService,
    private val blockService: BlockService
) {

    protected val logger = logger(MigrationService::class)

    fun updateAccounts(list: List<String>) = transaction {
        list.forEach { accountService.saveAccount(it) }
    }

    fun updateBlocks(startHeight: Int, endHeight: Int, inc: Int) {
        logger.info("Start height: $startHeight")
        var start = startHeight
        while (start <= endHeight) {
            transaction {
                logger.info("Fetching $start to ${start + inc - 1}")
                BlockCacheRecord.find { BlockCacheTable.id.between(start, start + inc - 1) }
                    .orderBy(Pair(BlockCacheTable.id, SortOrder.ASC)).forEach {
                        asyncCaching.saveBlockEtc(it.block, Pair(true, false))
                    }
            }
            start += inc
        }
        logger.info("End height: $endHeight")
    }

    fun insertBlocks(blocks: List<Int>, pullFromDb: Boolean) = transaction {
        blocks.forEach { block ->
            blockService.getBlockAtHeightFromChain(block)?.let {
                asyncCaching.saveBlockEtc(it, Pair(true, pullFromDb))
            }
        }
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
