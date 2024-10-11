package io.provenance.explorer.service.async

import cosmos.authz.v1beta1.msgExec
import cosmos.authz.v1beta1.msgGrant
import cosmos.authz.v1beta1.msgRevoke
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockTxCountsCacheRecord
import io.provenance.explorer.domain.entities.BlockTxRetryRecord
import io.provenance.explorer.domain.entities.CacheKeys
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
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.grpc.extensions.getMsgSubTypes
import io.provenance.explorer.grpc.extensions.getMsgType
import io.provenance.explorer.service.AccountService
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.CacheService
import io.provenance.explorer.service.ExplorerService
import io.provenance.explorer.service.GovService
import io.provenance.explorer.service.MetricsService
import io.provenance.explorer.service.TokenService
import io.provenance.explorer.service.ValidatorService
import io.provenance.explorer.service.getBlock
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
import org.joda.time.LocalDate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import tendermint.types.BlockOuterClass
import java.math.BigDecimal
import javax.annotation.PostConstruct

@Service
class ScheduledTaskService(
    private val props: ExplorerProperties,
    private val blockService: BlockService,
    private val govService: GovService,
    private val blockAndTxProcessor: BlockAndTxProcessor,
    private val explorerService: ExplorerService,
    private val cacheService: CacheService,
    private val tokenService: TokenService,
    private val accountService: AccountService,
    private val valService: ValidatorService,
    private val metricsService: MetricsService,
    private val assetService: AssetService
) {

    protected val logger = logger(ScheduledTaskService::class)
    protected var collectHistorical = true

    @PostConstruct
    fun asyncServiceOnStartInit() {
        Thread {
            try {
                Thread.sleep(5000)
                updateTokenHistorical()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.start()
    }

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
                    blockAndTxProcessor.saveBlockEtc(it)
                    indexHeight = it.block.height() - 1
                }
                blockService.updateBlockMinHeightIndex(indexHeight + 1)
            }
            blockService.updateBlockMaxHeightIndex(startHeight)
        } else {
            while (indexHeight > index.first!!) {
                blockService.getBlockAtHeightFromChain(indexHeight)?.let {
                    blockAndTxProcessor.saveBlockEtc(it)
                    indexHeight = it.block.height() - 1
                }
            }
            blockService.updateBlockMaxHeightIndex(startHeight)
        }

        BlockTxCountsCacheRecord.updateTxCounts()
        if (!cacheService.getCacheValue(CacheKeys.SPOTLIGHT_PROCESSING.key)!!.cacheValue.toBoolean()) {
            cacheService.updateCacheValue(CacheKeys.SPOTLIGHT_PROCESSING.key, true.toString())
        }
    }

    fun getBlockIndex() = blockService.getBlockIndexFromCache()?.let {
        Pair(it.maxHeightRead, it.minHeightRead)
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

    @Scheduled(initialDelay = 0L, fixedDelay = 5000L)
    fun updateSpotlight() = explorerService.createSpotlight()

    @Scheduled(initialDelay = 0L, fixedDelay = 30000L)
    fun retryBlockTxs() {
        logger.info("Retrying block/tx records")
        BlockTxRetryRecord.getRecordsToRetry().map { height ->
            logger.info("Retrying block/tx record at $height.")
            var retryException: Exception? = null
            val block = try {
                blockAndTxProcessor.saveBlockEtc(blockService.getBlockAtHeightFromChain(height), Pair(true, false))!!
            } catch (e: Exception) {
                retryException = e
                logger.error("Error saving block $height on retry.", e)
                null
            }

            val success = block?.let {
                val txCount = block.block?.data?.txsCount ?: -1
                val dbTxCount = transaction { TxCacheRecord.findByHeight(height).toList().size }

                if (dbTxCount != txCount) {
                    logger.error("Mismatch in transaction count for retrying block $height. Expected: $txCount, Found: $dbTxCount")
                }

                dbTxCount == txCount
            } ?: false

            logger.info("Finished retrying block/tx $height with success status: $success")
            BlockTxRetryRecord.updateRecord(height, success, retryException)
            height
        }.let { if (it.isNotEmpty()) BlockTxRetryRecord.deleteRecords(it) }
    }

    @Scheduled(initialDelay = 0L, fixedDelay = 300000L) // Every 5 minutes
    fun updateAssetPricing() {
        assetService.updateAssetPricingFromLatestNav()
    }

    @Scheduled(cron = "0 0/15 * * * ?") // Every 15 minutes
    fun updateReleaseVersions() = explorerService.getAllChainReleases()

    @Scheduled(cron = "0 0 0/1 * * ?") // Every hour
    fun saveChainAum() = explorerService.saveChainAum()

    @Scheduled(cron = "0 0 1 * * ?") // Every day at 1 am
    fun updateTokenHistorical() {
        val today = DateTime.now().startOfDay()
        val defaultStartDate = today.minusMonths(1)

        val latestEntryDate = TokenHistoricalDailyRecord.getLatestDateEntry()?.timestamp?.startOfDay()
        val startDate = latestEntryDate?.minusDays(1) ?: defaultStartDate

        tokenService.updateAndSaveTokenHistoricalData(startDate, today)
    }

     @Scheduled(initialDelay = 0L, fixedDelay = 300000L) // Every 5 minutes
    fun updateTokenLatest() {
        val today = DateTime.now().withZone(DateTimeZone.UTC)
        val startDate = today.minusDays(1)
        tokenService.updateAndSaveLatestTokenData(startDate, today)
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
                if (block.txCount > 0) blockAndTxProcessor.saveBlockEtc(block.block, Pair(true, false))
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
    fun startAccountProcess() {
        processAccountRecords()
    }

    fun processAccountRecords() {
        ProcessQueueRecord.reset(ProcessQueueType.ACCOUNT)
        val records = ProcessQueueRecord.findByType(ProcessQueueType.ACCOUNT)
        for (record in records) {
            try {
                transaction { record.apply { this.processing = true } }
                runBlocking { accountService.updateTokenCounts(record.processValue) }
                ProcessQueueRecord.delete(ProcessQueueType.ACCOUNT, record.processValue)
            } catch (_: Exception) {
            }
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
