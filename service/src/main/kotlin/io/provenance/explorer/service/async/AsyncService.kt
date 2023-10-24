package io.provenance.explorer.service.async

import cosmos.authz.v1beta1.msgExec
import cosmos.authz.v1beta1.msgGrant
import cosmos.authz.v1beta1.msgRevoke
import io.provenance.explorer.VANILLA_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN_BASE_DECIMAL_PLACES
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN_BASE_MULTIPLIER
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.BlockTxCountsCacheRecord
import io.provenance.explorer.domain.entities.BlockTxRetryRecord
import io.provenance.explorer.domain.entities.CacheKeys
import io.provenance.explorer.domain.entities.CacheUpdateRecord
import io.provenance.explorer.domain.entities.ChainMarketRateStatsRecord
import io.provenance.explorer.domain.entities.GovProposalRecord
import io.provenance.explorer.domain.entities.ProcessQueueRecord
import io.provenance.explorer.domain.entities.ProcessQueueType
import io.provenance.explorer.domain.entities.ProposalMonitorRecord
import io.provenance.explorer.domain.entities.ProposalMonitorRecord.Companion.checkIfProposalReadyForProcessing
import io.provenance.explorer.domain.entities.TokenHistoricalDailyRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxGasCacheRecord
import io.provenance.explorer.domain.entities.TxHistoryDataViews
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTable
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.TxMsgTypeSubtypeTable
import io.provenance.explorer.domain.entities.TxSingleMessageCacheRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateStatsRecord
import io.provenance.explorer.domain.entities.ValidatorMetricsRecord
import io.provenance.explorer.domain.entities.ValidatorStateRecord
import io.provenance.explorer.domain.extensions.getType
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.monthToQuarter
import io.provenance.explorer.domain.extensions.percentChange
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.models.explorer.DlobHistorical
import io.provenance.explorer.grpc.extensions.getMsgSubTypes
import io.provenance.explorer.grpc.extensions.getMsgType
import io.provenance.explorer.model.CmcHistoricalQuote
import io.provenance.explorer.model.CmcLatestDataAbbrev
import io.provenance.explorer.model.CmcLatestQuoteAbbrev
import io.provenance.explorer.model.CmcQuote
import io.provenance.explorer.model.base.USD_UPPER
import io.provenance.explorer.service.AccountService
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.CacheService
import io.provenance.explorer.service.ExplorerService
import io.provenance.explorer.service.GovService
import io.provenance.explorer.service.MetricsService
import io.provenance.explorer.service.PricingService
import io.provenance.explorer.service.TokenService
import io.provenance.explorer.service.ValidatorService
import io.provenance.explorer.service.getBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Interval
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
    private val pricingService: PricingService,
    private val accountService: AccountService,
    private val valService: ValidatorService,
    private val metricsService: MetricsService
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
        if (!cacheService.getCacheValue(CacheKeys.SPOTLIGHT_PROCESSING.key)!!.cacheValue.toBoolean()) {
            cacheService.updateCacheValue(CacheKeys.SPOTLIGHT_PROCESSING.key, true.toString())
        }
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
            if (addr != null) {
                ValidatorMarketRateStatsRecord.save(addr, min, max, avg, date)
            } else {
                ChainMarketRateStatsRecord.save(min, max, avg, date)
            }
        } else {
            if (addr != null) {
                ValidatorMarketRateStatsRecord.save(addr, null, null, null, date)
            } else {
                ChainMarketRateStatsRecord.save(null, null, null, date)
            }
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
                // dont set price from PE
                if (price.markerDenom != UTILITY_TOKEN) {
                    assetService.getAssetRaw(price.markerDenom).let { pricingService.insertAssetPricing(it, price) }
                } else {
                    // Pull price from CMC, calced to the true base denom price
                    val cmcPrice =
                        tokenService.getTokenLatest()?.quote?.get(USD_UPPER)?.price
                            ?.let {
                                val scale = it.scale()
                                it.setScale(scale + UTILITY_TOKEN_BASE_DECIMAL_PLACES)
                                    .div(UTILITY_TOKEN_BASE_MULTIPLIER)
                            }
                    // If CMC data exists, use that, else use the PE value
                    val newPriceObj = price.copy(usdPrice = cmcPrice ?: price.usdPrice!!)
                    // Save it
                    assetService.getAssetRaw(price.markerDenom).let {
                        pricingService.insertAssetPricing(it, newPriceObj)
                    }
                }
            }
        }.let { cacheService.updateCacheValue(key, now) }
    }

    @Scheduled(cron = "0 0/15 * * * ?") // Every 15 minutes
    fun updateReleaseVersions() = explorerService.getAllChainReleases()

    @Scheduled(cron = "0 0 0/1 * * ?") // Every hour
    fun saveChainAum() = explorerService.saveChainAum()

    @Scheduled(cron = "0 0 1 * * *") // Every day at 1 am
    fun updateTokenHistorical() {
        val today = DateTime.now().startOfDay()
        var startDate = today.minusMonths(1)
        var latest = TokenHistoricalDailyRecord.getLatestDateEntry()
        if (latest != null) {
            startDate = latest.timestamp.minusDays(1)
        }

        val dlobRes = tokenService.getHistoricalFromDlob(startDate) ?: return
        val baseMap = Interval(startDate, today)
            .let { int -> generateSequence(int.start) { dt -> dt.plusDays(1) }.takeWhile { dt -> dt < int.end } }
            .map { it to emptyList<DlobHistorical>() }.toMap().toMutableMap()
        var prevPrice = TokenHistoricalDailyRecord.lastKnownPriceForDate(startDate)

        baseMap.putAll(
            dlobRes.buy
                .filter { DateTime(it.trade_timestamp * 1000).startOfDay() != today }
                .groupBy { DateTime(it.trade_timestamp * 1000).startOfDay() }
        )
        baseMap.forEach { (k, v) ->
            val high = v.maxByOrNull { it.price }
            val low = v.minByOrNull { it.price }
            val open = v.minByOrNull { DateTime(it.trade_timestamp * 1000) }?.price ?: prevPrice
            val close = v.maxByOrNull { DateTime(it.trade_timestamp * 1000) }?.price ?: prevPrice
            val closeDate = k.plusDays(1).minusMillis(1)
            val usdVolume = v.sumOf { it.target_volume }.stripTrailingZeros()
            val record = CmcHistoricalQuote(
                time_open = k,
                time_close = closeDate,
                time_high = if (high != null) DateTime(high.trade_timestamp * 1000) else k,
                time_low = if (low != null) DateTime(low.trade_timestamp * 1000) else k,
                quote = mapOf(
                    USD_UPPER to
                            CmcQuote(
                                open = open,
                                high = high?.price ?: prevPrice,
                                low = low?.price ?: prevPrice,
                                close = close,
                                volume = usdVolume,
                                market_cap = close.multiply(
                                    tokenService.totalSupply().divide(UTILITY_TOKEN_BASE_MULTIPLIER)
                                ),
                                timestamp = closeDate
                            )
                )
            ).also { prevPrice = close }
            TokenHistoricalDailyRecord.save(record.time_open.startOfDay(), record)
        }
    }

    @Scheduled(cron = "0 0/5 * * * ?") // Every 5 minutes
    fun updateTokenLatest() {
        val today = DateTime.now().withZone(DateTimeZone.UTC)
        val startDate = today.minusDays(7)
        tokenService.getHistoricalFromDlob(startDate)?.buy
            ?.sortedBy { it.trade_timestamp }
            ?.let { list ->
                val prevRecIdx = list.indexOfLast { DateTime(it.trade_timestamp * 1000).isBefore(today.minusDays(1)) }
                val prevRecord = list[prevRecIdx]
                val price = list.last().price
                val percentChg = if (prevRecIdx == list.lastIndex) {
                    BigDecimal.ZERO
                } else {
                    price.percentChange(prevRecord.price)
                }
                val vol24Hr = if (prevRecIdx == list.lastIndex) {
                    BigDecimal.ZERO
                } else {
                    list.subList(prevRecIdx + 1, list.lastIndex + 1).sumOf { it.target_volume }.stripTrailingZeros()
                }
                val marketCap = price.multiply(tokenService.totalSupply().divide(UTILITY_TOKEN_BASE_MULTIPLIER))
                val rec = CmcLatestDataAbbrev(
                    today,
                    mapOf(USD_UPPER to CmcLatestQuoteAbbrev(price, percentChg, vol24Hr, marketCap, today))
                )
                CacheUpdateRecord.updateCacheByKey(
                    CacheKeys.UTILITY_TOKEN_LATEST.key,
                    VANILLA_MAPPER.writeValueAsString(rec)
                )
            }
    }

    // Remove once the ranges have been updated
    @Scheduled(cron = "0 0/15 * * * ?") // Every 15 minutes
    fun feeBugOneElevenReprocess() {
        val done = "DONE"
        // Find existing record
        var startBlock = cacheService.getCacheValue(CacheKeys.FEE_BUG_ONE_ELEVEN_START_BLOCK.key)!!.cacheValue
        // If null, update from env. If env comes back null, update to "DONE"
        if (startBlock.isNullOrBlank()) {
            startBlock = props.oneElevenBugRange()?.first()?.toString()
                .also { cacheService.updateCacheValue(CacheKeys.FEE_BUG_ONE_ELEVEN_START_BLOCK.key, it ?: done) }
                ?: done
        }
        // If "DONE" exit out
        if (startBlock == done) return
        var lastBlock = 0
        // Create range, and process next 200 blocks or until the end of the fee bug range, whichever is less
        (startBlock.toInt()..minOf(props.oneElevenBugRange()!!.last, startBlock.toInt().plus(100))).toList()
            .let { BlockCacheRecord.getBlocksForRange(it.first(), it.last()) }
            .forEach { block ->
                if (block.txCount > 0) asyncCache.saveBlockEtc(block.block, Pair(true, false))
                // Check if the last processed block equals the end of the fee bug range
                if (block.height == props.oneElevenBugRange()!!.last) {
                    cacheService.updateCacheValue(CacheKeys.FEE_BUG_ONE_ELEVEN_START_BLOCK.key, done)
                } else {
                    lastBlock = block.height
                }
            }
        // Update the cache value to the last block processed
        cacheService.updateCacheValue(CacheKeys.FEE_BUG_ONE_ELEVEN_START_BLOCK.key, lastBlock.toString())
        logger.info("Updated fee bug range")
    }

    @Scheduled(cron = "0 0 2 * * ?") // Every day at 2 AM
    fun refreshMaterializedViews() {
        logger.info("Refreshing fee-based views")
        TxHistoryDataViews.refreshViews()
    }

    @Scheduled(cron = "0 0/5 * * * ?") // Every 5 minutes
    fun processAuthzMessages() = transaction {
        if (cacheService.getCacheValue(CacheKeys.AUTHZ_PROCESSING.key)!!.cacheValue.toBoolean()) {
            val types = listOf(msgGrant { }.getType(), msgExec { }.getType(), msgRevoke { }.getType())
            val typeIds = TxMessageTypeRecord.findByProtoTypeIn(types)
            val msgs = TxMessageTable
                .innerJoin(TxMsgTypeSubtypeTable, { TxMessageTable.id }, { TxMsgTypeSubtypeTable.txMsgId })
                .slice(TxMessageTable.columns)
                .select { TxMsgTypeSubtypeTable.primaryType inList typeIds }
                .andWhere { TxMsgTypeSubtypeTable.secondaryType.isNull() }
                .orderBy(Pair(TxMessageTable.blockHeight, SortOrder.DESC))
                .limit(5000)
                .let { TxMessageRecord.wrapRows(it).toList() }
            if (msgs.isEmpty()) {
                cacheService.updateCacheValue(CacheKeys.AUTHZ_PROCESSING.key, false.toString())
            } else {
                msgs.forEach { msgRec ->
                    val secondaries = msgRec.txMessage.getMsgSubTypes().filterNotNull()
                        .map { it.getMsgType() }
                        .map { TxMessageTypeRecord.insert(it.type, it.module, it.proto) }
                    val (msgId, primId) = msgRec.txMessageType.toList()[0].let { it to it.primaryType.id }
                    msgRec.txMessageType.map { it.id.value }.let { idList ->
                        TxMsgTypeSubtypeTable.deleteWhere { TxMsgTypeSubtypeTable.id inList idList }
                    }
                    secondaries.forEach { secId ->
                        TxMsgTypeSubtypeTable.insertIgnore {
                            it[this.txMsgId] = msgId.txMsgId
                            it[this.primaryType] = primId
                            it[this.secondaryType] = secId
                            it[this.txTimestamp] = msgId.txTimestamp
                            it[this.txHashId] = msgId.txHashId.id.value
                            it[this.blockHeight] = msgId.blockHeight
                            it[this.txHash] = msgId.txHash
                        }
                    }
                }
            }
            logger.info("Updating AUTHZ subtypes")
        }
    }

    @Scheduled(initialDelay = 5000L, fixedDelay = 5000L)
    fun startAccountProcess() = runBlocking {
        ProcessQueueRecord.reset(ProcessQueueType.ACCOUNT)
        val producer = startAccountProcess()
        repeat(5) { accountProcessor(producer) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun CoroutineScope.startAccountProcess() = produce {
        while (true) {
            ProcessQueueRecord.findByType(ProcessQueueType.ACCOUNT).firstOrNull()?.let {
                try {
                    transaction { it.apply { this.processing = true } }
                    send(it.processValue)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun CoroutineScope.accountProcessor(channel: ReceiveChannel<String>) = launch(Dispatchers.IO) {
        for (msg in channel) {
            accountService.updateTokenCounts(msg)
            ProcessQueueRecord.delete(ProcessQueueType.ACCOUNT, msg)
        }
    }

    @Scheduled(cron = "0 0 0 * * *") // Every beginning of every day
    fun calculateValidatorMetrics() {
        val (year, quarter) = DateTime.now().minusMinutes(5).let { it.year to it.monthOfYear.monthToQuarter() }
        logger.info("Refreshing block spread view")
        BlockTxCountsCacheRecord.updateSpreadView()
        logger.info("Saving validator metrics")
        val spread = BlockTxCountsCacheRecord.getBlockTimeSpread(year, quarter) ?: return
        ValidatorStateRecord.findAll(valService.getActiveSet()).forEach { vali ->
            try {
                val metric = metricsService.processMetricsForValObjectAndSpread(vali, spread)
                ValidatorMetricsRecord.insertIgnore(
                    vali.operatorAddrId,
                    vali.operatorAddress,
                    spread.year,
                    spread.quarter,
                    metric
                )
            } catch (e: Exception) {
                logger.error("Error processing metrics for validator: ${vali.operatorAddress}", e.message)
            }
        }
    }
}
