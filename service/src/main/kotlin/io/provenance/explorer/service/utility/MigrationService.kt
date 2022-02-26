package io.provenance.explorer.service.utility

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockCacheTable
import io.provenance.explorer.service.AccountService
import io.provenance.explorer.service.ValidatorService
import io.provenance.explorer.service.async.AsyncCachingV2
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class MigrationService(
    private val asyncCaching: AsyncCachingV2,
    private val validatorService: ValidatorService,
    private val accountService: AccountService
) {

    protected val logger = logger(MigrationService::class)

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
}
