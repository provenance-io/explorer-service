package io.provenance.explorer.service.async

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.BlockTxCountsCacheRecord
import io.provenance.explorer.domain.entities.BlockTxRetryRecord
import io.provenance.explorer.domain.entities.ChainGasFeeCacheRecord
import io.provenance.explorer.domain.entities.GovProposalRecord
import io.provenance.explorer.domain.entities.ProposalMonitorRecord
import io.provenance.explorer.domain.entities.ProposalMonitorRecord.Companion.checkIfProposalReadyForProcessing
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxGasCacheRecord
import io.provenance.explorer.domain.entities.TxSingleMessageCacheRecord
import io.provenance.explorer.domain.entities.ValidatorGasFeeCacheRecord
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.ExplorerService
import io.provenance.explorer.service.GovService
import io.provenance.explorer.service.getBlock
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
    private val assetService: AssetService,
    private val govService: GovService,
    private val asyncCache: AsyncCaching,
    private val explorerService: ExplorerService
) {

    protected val logger = logger(AsyncService::class)
    protected var collectHistorical = true

    @Scheduled(initialDelay = 0L, fixedDelay = 5000L)
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
                blockService.getBlockAtHeightFromChain(indexHeight)?.let {
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
                blockService.getBlockAtHeightFromChain(indexHeight)?.let {
                    asyncCache.saveBlockEtc(it)
                    indexHeight = it.block.height() - 1
                }
            }
            blockService.updateBlockMaxHeightIndex(startHeight)
        }

        BlockTxCountsCacheRecord.updateTxCounts()
        BlockProposerRecord.calcLatency()
    }

    fun getBlockIndex() = blockService.getBlockIndexFromCache()?.let {
        Pair<Int?, Int?>(it.maxHeightRead, it.minHeightRead)
    }

    fun startCollectingHistoricalBlocks(blockIndex: Pair<Int?, Int?>?) =
        blockIndex?.second == null || blockIndex.first == null

    fun continueCollectingHistoricalBlocks(maxRead: Int, minRead: Int): Boolean {
        if (collectHistorical) {
            val historicalDays = BlockCacheRecord.getDaysBetweenHeights(minRead, maxRead)
            collectHistorical = historicalDays < explorerProperties.initialHistoricalDays() && minRead > 1
        }
        return collectHistorical
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

    @Scheduled(cron = "0 30 0/1 * * ?") // Every hour at the 30 minute mark
    fun performProposalUpdates() = transaction {
        GovProposalRecord.getNonFinalProposals().forEach { govService.updateProposal(it) }
        val currentBlock = blockService.getMaxBlockCacheHeight().getBlock()
        ProposalMonitorRecord.getUnprocessed().forEach {
            val proposal = GovProposalRecord.findByProposalId(it.proposalId)!!
            it.checkIfProposalReadyForProcessing(proposal.status, currentBlock.blockTimestamp)
        }
        ProposalMonitorRecord.getReadyForProcessing().forEach { govService.processProposal(it) }
    }

    @Scheduled(cron = "0 0 0/1 * * ?") // At the start of every hour
    fun updateGasStats() = transaction {
        logger.info("Updating Gas stats")
        TxSingleMessageCacheRecord.updateGasStats()
    }

    @Scheduled(cron = "0 0 0/1 * * ?") // At the start of every hour
    fun updateGasVolume() = transaction {
        logger.info("Updating Gas volume")
        TxGasCacheRecord.updateGasFeeVolume()
    }

    @Scheduled(cron = "0 0 1 * * ?") // Everyday at 1 am
    fun updateTokenDistributionAmounts() = transaction {
        logger.info("Updating token distribution amounts")
        assetService.updateTokenDistributionStats(NHASH)
    }

    @Scheduled(cron = "0/5 * * * * ?") // Every 5 seconds
    fun updateSpotlight() = transaction {
        explorerService.createSpotlight()
    }

    @Scheduled(cron = "0 0/5 * * * ?") // Every 5 minute
    fun retryBlockTxs() {
        BlockTxRetryRecord.getRecordsToRetry().map { height ->
            val block = asyncCache.getBlock(height, true)!!
            val success = transaction { TxCacheRecord.findByHeight(height).toList() }.size == block.block.data.txsCount
            BlockTxRetryRecord.updateRecord(height, success)
            height
        }.let { if (it.isNotEmpty()) BlockTxRetryRecord.deleteRecords(it) }
    }
}
