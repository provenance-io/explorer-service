package io.provenance.explorer.service.async

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.ChainGasFeeCacheRecord
import io.provenance.explorer.domain.entities.GovProposalRecord
import io.provenance.explorer.domain.entities.TxSingleMessageCacheRecord
import io.provenance.explorer.domain.entities.ValidatorGasFeeCacheRecord
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.GovService
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import tendermint.types.BlockOuterClass
import java.math.BigDecimal

@Service
class AsyncService(
    private val explorerProperties: ExplorerProperties,
    private val blockService: BlockService,
    private val govService: GovService,
    private val asyncCache: AsyncCaching
) {

    protected val logger = logger(AsyncService::class)

    @Scheduled(initialDelay = 0L, fixedDelay = 1000L)
    fun updateLatestBlockHeightJob() {
        val index = getBlockIndex()
        val startHeight = blockService.getLatestBlockHeight()
        var indexHeight = startHeight
        if (startCollectingHistoricalBlocks(index) || continueCollectingHistoricalBlocks(index!!.first!!, index.second!!)) {
            val endDate = getEndDate()
            var shouldContinue = true
            blockService.updateBlockMaxHeightIndex(startHeight)
            indexHeight = index?.second?.minus(1) ?: indexHeight
            while (shouldContinue && indexHeight > 0) {
                blockService.getBlockAtHeightFromChain(indexHeight).let {
                    if (endDate >= it.block.day()) {
                        shouldContinue = false
                        return
                    }
                    asyncCache.saveBlockEtc(it)
                    indexHeight = it.block.height() - 1
                }
                blockService.updateBlockMinHeightIndex(indexHeight + 1)
            }
        } else {
            while (indexHeight > index.first!!) {
                blockService.getBlockAtHeightFromChain(indexHeight).let {
                    asyncCache.saveBlockEtc(it)
                    indexHeight = it.block.height() - 1
                }
            }
            blockService.updateBlockMaxHeightIndex(startHeight)
        }
    }

    fun getBlockIndex() = blockService.getBlockIndexFromCache()?.let {
        Pair<Int?, Int?>(it.maxHeightRead, it.minHeightRead)
    }

    fun startCollectingHistoricalBlocks(blockIndex: Pair<Int?, Int?>?) =
        blockIndex?.second == null || blockIndex.first == null

    fun continueCollectingHistoricalBlocks(maxRead: Int, minRead: Int): Boolean {
        val historicalDays = BlockCacheRecord.getDaysBetweenHeights(minRead, maxRead)
        return historicalDays < explorerProperties.initialHistoricalDays() && minRead > 1
    }

    fun getEndDate() = LocalDate().toDateTimeAtStartOfDay().minusDays(explorerProperties.initialHistoricalDays() + 1)

    fun BlockOuterClass.Block.day() = this.header.time.toDateTime()

    @Scheduled(cron = "0 0 1 * * ?") // Everyday at 1 am
    fun updateGasFeeCaches() = transaction {
        val date = DateTime.now().startOfDay().minusDays(1)
        val records = BlockProposerRecord.findForDates(date, date, null)
        records.groupBy { it.proposerOperatorAddress }.forEach { (k, v) -> calcAndInsert(v, k, date) }
        calcAndInsert(records.toList(), null, date)
    }

    private fun calcAndInsert(orig: List<BlockProposerRecord>, addr: String?, date: DateTime) = transaction {
        val list = orig.filter { it.minGasFee != null }.map { it.minGasFee!!.toBigDecimal() }
        if (list.isNotEmpty()) {
            val max = list.maxWithOrNull(Comparator.naturalOrder())
            val min = list.minWithOrNull(Comparator.naturalOrder())
            val avg = list.fold(BigDecimal.ZERO, BigDecimal::add).div(list.count().toBigDecimal())
            if (addr != null)
                ValidatorGasFeeCacheRecord.save(addr, min, max, avg, date)
            else
                ChainGasFeeCacheRecord.save(min, max, avg, date)
        } else {
            if (addr != null)
                ValidatorGasFeeCacheRecord.save(addr, null, null, null, date)
            else
                ChainGasFeeCacheRecord.save(null, null, null, date)
        }
    }

    @Scheduled(cron = "0 0 0 * * ?") // Everyday at 12 am
    fun updateProposalStatus() = transaction {
        GovProposalRecord.getNonFinalProposals().forEach { govService.updateProposal(it) }
    }

    @Scheduled(cron = "0 0 0/1 * * ?") // At the start of every hour
    fun updateGasStats() = transaction {
        logger.info("Updating Gas stats")
        TxSingleMessageCacheRecord.updateGasStats()
    }
}
