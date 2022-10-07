package io.provenance.explorer.service.async

import io.provenance.explorer.VANILLA_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.BlockTxCountsCacheRecord
import io.provenance.explorer.domain.entities.BlockTxRetryRecord
import io.provenance.explorer.domain.entities.CacheKeys
import io.provenance.explorer.domain.entities.CacheUpdateRecord
import io.provenance.explorer.domain.entities.ChainMarketRateStatsRecord
import io.provenance.explorer.domain.entities.GovProposalRecord
import io.provenance.explorer.domain.entities.ProposalMonitorRecord
import io.provenance.explorer.domain.entities.ProposalMonitorRecord.Companion.checkIfProposalReadyForProcessing
import io.provenance.explorer.domain.entities.TokenHistoricalDailyRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxGasCacheRecord
import io.provenance.explorer.domain.entities.TxSingleMessageCacheRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateStatsRecord
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.CacheService
import io.provenance.explorer.service.ExplorerService
import io.provenance.explorer.service.GovService
import io.provenance.explorer.service.PricingService
import io.provenance.explorer.service.TokenService
import io.provenance.explorer.service.getBlock
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import tendermint.types.BlockOuterClass
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class AsyncService(
    private val props: ExplorerProperties,
    private val blockService: BlockService,
    private val assetService: AssetService,
    private val govService: GovService,
    private val asyncCache: AsyncCachingV2,
    private val explorerService: ExplorerService,
    private val cacheService: CacheService,
    private val tokenService: TokenService,
    private val pricingService: PricingService
) {

    protected val logger = logger(AsyncService::class)
    protected var collectHistorical = true

    @Scheduled(initialDelay = 0L, fixedDelay = 5000L)
    fun updateLatestBlockHeightJob() {
        val index = getBlockIndex()
        val startHeight = blockService.getLatestBlockHeight()
        var indexHeight = startHeight
        if (startCollectingHistoricalBlocks(index) ||
            continueCollectingHistoricalBlocks(index!!.first!!, index.second!!)
        ) {
            val endDate = getEndDate()
            var shouldContinue = true
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
            blockService.updateBlockMaxHeightIndex(startHeight)
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
        if (!cacheService.getCacheValue(CacheKeys.SPOTLIGHT_PROCESSING.key)!!.cacheValue.toBoolean())
            cacheService.updateCacheValue(CacheKeys.SPOTLIGHT_PROCESSING.key, true.toString())
    }

    fun getBlockIndex() = blockService.getBlockIndexFromCache()?.let {
        Pair<Int?, Int?>(it.maxHeightRead, it.minHeightRead)
    }

    fun startCollectingHistoricalBlocks(blockIndex: Pair<Int?, Int?>?) =
        blockIndex?.second == null || blockIndex.first == null

    fun continueCollectingHistoricalBlocks(maxRead: Int, minRead: Int): Boolean {
        if (collectHistorical) {
            val historicalDays = BlockCacheRecord.getDaysBetweenHeights(minRead, maxRead)
            collectHistorical = historicalDays < props.initialHistoricalDays() && minRead > 1
        }
        return collectHistorical
    }

    fun getEndDate() = LocalDate().toDateTimeAtStartOfDay().minusDays(props.initialHistoricalDays() + 1)

    fun BlockOuterClass.Block.day() = this.header.time.toDateTime()

    @Scheduled(cron = "0 0 1 * * ?") // Everyday at 1 am
    fun updateMarketRateStats() = transaction {
        logger.info("Updating market rate stats")
        val date = DateTime.now().startOfDay().minusDays(1)
        val records = ValidatorMarketRateRecord.findForDates(date, date, null)
        records.groupBy { it.proposerAddress }.forEach { (k, v) -> calcAndInsert(v, k, date) }
        calcAndInsert(records.toList(), null, date)
    }

    private fun calcAndInsert(orig: List<ValidatorMarketRateRecord>, addr: String?, date: DateTime) = transaction {
        val list = orig.map { it.marketRate }
        if (list.isNotEmpty()) {
            val max = list.maxWithOrNull(Comparator.naturalOrder())
            val min = list.minWithOrNull(Comparator.naturalOrder())
            val avg = list.fold(BigDecimal.ZERO, BigDecimal::add).div(list.count().toBigDecimal())
            if (addr != null) ValidatorMarketRateStatsRecord.save(addr, min, max, avg, date)
            else ChainMarketRateStatsRecord.save(min, max, avg, date)
        } else {
            if (addr != null) ValidatorMarketRateStatsRecord.save(addr, null, null, null, date)
            else ChainMarketRateStatsRecord.save(null, null, null, date)
        }
    }

    @Scheduled(cron = "0 0/5 * * * ?") // Every 5 minutes
    fun performProposalUpdates() = transaction {
        logger.info("Performing proposal updates")
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
        logger.info("Updating Single Msg Gas stats")
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
        tokenService.updateTokenDistributionStats(UTILITY_TOKEN)
    }

    @Scheduled(cron = "0/5 * * * * ?") // Every 5 seconds
    fun updateSpotlight() = explorerService.createSpotlight()

    @Scheduled(cron = "0 0/5 * * * ?") // Every 5 minute
    fun retryBlockTxs() {
        logger.info("Retrying block/tx records")
        BlockTxRetryRecord.getRecordsToRetry().map { height ->
            val block = try {
                asyncCache.saveBlockEtc(blockService.getBlockAtHeightFromChain(height), Pair(true, false))!!
            } catch (e: Exception) {
                null
            }
            val success =
                transaction { TxCacheRecord.findByHeight(height).toList() }.size == (block?.block?.data?.txsCount ?: -1)
            BlockTxRetryRecord.updateRecord(height, success)
            height
        }.let { if (it.isNotEmpty()) BlockTxRetryRecord.deleteRecords(it) }
    }

    @Scheduled(cron = "0 0/15 * * * ?") // Every 15 minutes
    fun updateAssetPricing() {
        logger.info("Updating asset pricing")
        val key = CacheKeys.PRICING_UPDATE.key
        val now = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).toString()
        cacheService.getCacheValue(key)!!.let { cache ->
            pricingService.getPricingAsync(cache.cacheValue!!, "async pricing update").forEach { price ->
                assetService.getAssetRaw(price.markerDenom).let { pricingService.insertAssetPricing(it, price) }
            }
        }.let { cacheService.updateCacheValue(key, now) }
    }

    @Scheduled(cron = "0 0/15 * * * ?") // Every 15 minutes
    fun updateReleaseVersions() = explorerService.getAllChainReleases()

    @Scheduled(cron = "0 0 0/1 * * ?") // Every hour
    fun saveChainAum() = explorerService.saveChainAum()

    @Scheduled(cron = "0 0 1 * * ?") // Every day at 1 am
    fun updateTokenHistorical() =
        tokenService.updateTokenHistorical()?.data?.quotes?.forEach {
            TokenHistoricalDailyRecord.save(it.time_open.startOfDay(), it)
        }

    @Scheduled(cron = "0 0/5 * * * ?") // Every 5 minutes
    fun updateTokenLatest() =
        tokenService.updateTokenLatest()?.data?.get("19960")?.let {
            CacheUpdateRecord.updateCacheByKey(CacheKeys.UTILITY_TOKEN_LATEST.key, VANILLA_MAPPER.writeValueAsString(it))
        }

    // Remove once the ranges have been updated
    @Scheduled(cron = "0 0/15 * * * ?") // Every 15 minutes
    fun feeBugOneElevenReprocess() {
        val done = "DONE"
        // Find existing record
        var startBlock = cacheService.getCacheValue(CacheKeys.FEE_BUG_ONE_ELEVEN_START_BLOCK.key)!!.cacheValue
        // If null, update from env. If env comes back null, update to "DONE"
        if (startBlock.isNullOrBlank())
            startBlock = props.oneElevenBugRange()?.first()?.toString()
                .also { cacheService.updateCacheValue(CacheKeys.FEE_BUG_ONE_ELEVEN_START_BLOCK.key, it ?: done) }
                ?: done
        // If "DONE" exit out
        if (startBlock == done) return
        var lastBlock = 0
        // Create range, and process next 200 blocks or until the end of the fee bug range, whichever is less
        (startBlock.toInt()..minOf(props.oneElevenBugRange()!!.last, startBlock.toInt().plus(1000))).toList()
            .forEach { block ->
                (blockService.getBlockAtHeight(block)?.block ?: blockService.getBlockAtHeightFromChain(block))
                    ?.let { asyncCache.saveBlockEtc(it, Pair(true, false)) }
                // Check if the last processed block equals the end of the fee bug range
                if (block == props.oneElevenBugRange()!!.last)
                    cacheService.updateCacheValue(CacheKeys.FEE_BUG_ONE_ELEVEN_START_BLOCK.key, done)
                else
                    lastBlock = block
            }
        // Update the cache value to the last block processed
        cacheService.updateCacheValue(CacheKeys.FEE_BUG_ONE_ELEVEN_START_BLOCK.key, lastBlock.toString())
        logger.info("Updated fee bug range")
    }
}
