package io.provenance.explorer.service

import com.fasterxml.jackson.databind.JsonNode
import io.provenance.core.extensions.logger
import io.provenance.core.extensions.toJsonString
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import kotlin.system.measureTimeMillis

@Service
class ExplorerService(private val explorerProperties: ExplorerProperties,
                      private val restTemplate: RestTemplate,
                      private val cacheService: CacheService) {

    protected val logger = logger(ExplorerService::class)

    @Volatile
    protected var latestHeight = getLatestBlockHeight()

    fun getLatestBlockHeight(): Int = getStatus().syncInfo.latestBlockHeight.toInt()

    fun getStatus() = OBJECT_MAPPER.readValue(getRestResult("${explorerProperties.pbUrl}status").toJsonString(), StatusResult::class.java)

    fun getRestResult(url: String) = let {
        if (!url.contains("status")) logger.info("GET request to $url")
        restTemplate.getForEntity(url, JsonNode::class.java).let { result ->
            if (result.statusCode != HttpStatus.OK || result.body.has("error") || !result.body.has("result")) {
                logger.error("Failed to calling $url status code: ${result.statusCode} response body: ${result.body}")
                throw Exception(result.body.asText())
            }
            result.body.get("result")
        }
    }

    @Scheduled(initialDelay = 0L, fixedDelay = 1000L)
    fun updateLatestBlockHeightJob() = let {
        latestHeight = getLatestBlockHeight()
    }

    @Scheduled(initialDelay = 0L, fixedDelay = 60000L)
    fun updateTransactionCountJob() = let {
        if (explorerProperties.isMetricJobEnabled()) updateTransactionCount();
    }

    @Synchronized
    fun updateTransactionCount() {
        val transactionCountIndex = cacheService.getTransactionCountIndex()
        val startIndex = latestHeight
        var currentIndex = startIndex
        val dayTxMetrics = mutableMapOf<String, TxHistory>()
        val startTime = DateTime.now()
        logger.info("Starting update of transaction metrics from current height $startIndex")
        val time = measureTimeMillis {
            if (transactionCountIndex == null) {
                while (dayTxMetrics.keys.size < explorerProperties.txsInitDays() + 1 && currentIndex >= 0) {
                    getBlockchain(currentIndex).blockMetas.sortedByDescending { it.height() }.forEach {
                        currentIndex = calculateDayMetrics(dayTxMetrics, it)
                    }
                }
                val incompleteDay = dayTxMetrics.keys.sortedByDescending { it }.last()
                dayTxMetrics.remove(incompleteDay)
            } else {
                val endIndex = transactionCountIndex[TransactionCountIndex.maxHeightRead]
                while (currentIndex > endIndex) {
                    getBlockchain(currentIndex).blockMetas.sortedByDescending { it.height() }.forEach {
                        if (it.height() > endIndex) {
                            currentIndex = calculateDayMetrics(dayTxMetrics, it)
                        }
                    }
                }
            }
        }
        val endIndex = dayTxMetrics.values.sortedBy { it.minHeight }.first().minHeight
        cacheService.addTransactionCounts(dayTxMetrics, startIndex, endIndex, startTime)
        logger.info("Finished updating transactions read ${startIndex - endIndex} blocks from $startIndex to $endIndex in $time ms.")
    }

    fun calculateDayMetrics(dayTxMetrics: MutableMap<String, TxHistory>, blockMeta: BlockMeta) = let {
        if (!dayTxMetrics.containsKey(blockMeta.day())) {
            dayTxMetrics.put(blockMeta.day(), TxHistory(blockMeta.day(), blockMeta.numTxs.toInt(), if (blockMeta.numTxs.toInt() > 0) 1 else 0, blockMeta.height(), blockMeta.height()))
        } else {
            val history = dayTxMetrics.get(blockMeta.day())!!
            history.minHeight = blockMeta.height()
            history.numberTxs += blockMeta.numTxs.toInt()
            history.numberTxBlocks += if (blockMeta.numTxs.toInt() > 0) 1 else 0
        }
        blockMeta.height() - 1
    }

    fun getRecentBlocks(count: Int, page: Int, sort: String) = let {
        var blockHeight = if (page < 0) latestHeight else latestHeight - (count * page)
        val result = mutableListOf<RecentBlock>()
        while (result.size < count) {
            var blockchain = getBlockchain(blockHeight)
            blockchain.blockMetas.forEach { blockMeta ->
                if (result.size < count) {
                    result.add(RecentBlock(blockMeta.header.height.toInt(),
                            blockMeta.numTxs.toInt(),
                            blockMeta.header.time))
                    blockHeight = blockMeta.header.height.toInt()
                }
            }
            blockHeight = blockchain.blockMetas.minHeight() - 1
        }
        if ("asc" == sort.toLowerCase()) result.reverse()
        PagedResults(latestHeight / count, result)
    }

    fun getBlockchain(maxHeight: Int) = let {
        var blocks = cacheService.getBlockchainFromMaxHeight(maxHeight)
        if (blocks == null) {
            logger.info("cache miss for blockchain max height $maxHeight")
            blocks = getRestResult("${explorerProperties.pbUrl}blockchain?maxHeight=$maxHeight")
            cacheService.addBlockchainToCache(maxHeight, blocks)
        }
        OBJECT_MAPPER.readValue(blocks.toString(), Blockchain::class.java)
    }

    fun getBlockAtHeight(height: Int?) = runBlocking(Dispatchers.IO) {
        val queryHeight = if (height == null) latestHeight else height
        val blockResponse = async { getBlock(queryHeight) }
        val validatorsResponse = async { getValidators(queryHeight) }
        hydrateBlock(blockResponse.await(), validatorsResponse.await())
    }

    fun hydrateBlock(blockResponse: BlockResponse, validatorsResponse: ValidatorsResponse) = let {
        BlockDetail(blockResponse.block.header.height.toInt(),
                blockResponse.block.header.time,
                blockResponse.block.header.validatorsHash,
                "",
                "",
                validatorsResponse.validators.sumBy { v -> v.votingPower.toInt() },
                validatorsResponse.count.toInt(),
                if (blockResponse.block.data.txs == null) 0 else blockResponse.block.data.txs.size,
                0,
                0,
                0)
    }

    fun getBlock(blockHeight: Int) = transaction {
        var block = cacheService.getBlockByHeight(blockHeight)
        if (block == null) {
            logger.info("cache miss for block height $blockHeight")
            block = getRestResult("${explorerProperties.pbUrl}block?height=$blockHeight").let { node ->
                cacheService.addBlockToCache(blockHeight, node)
                node
            }
        }
        OBJECT_MAPPER.readValue(block.toString(), BlockResponse::class.java)
    }

    fun getValidators(blockHeight: Int) = let {
        var validators = cacheService.getValidatorsByHeight(blockHeight)
        if (validators == null) {
            logger.info("cache miss for validators height $blockHeight")
            validators = getRestResult("${explorerProperties.pbUrl}validators?height=$blockHeight").let { node ->
                cacheService.addValidatorsToCache(blockHeight, node)
                node
            }
        }
        OBJECT_MAPPER.readValue(validators.toString(), ValidatorsResponse::class.java)
    }

    fun getValidator(addressId: String) = getValidators(latestHeight).validators
            .filter { v -> addressId == v.address }
            .map { v -> ValidatorDetail(v.votingPower.toInt(), v.address, "", 0) }
            .firstOrNull()

    fun getRecentValidators(count: Int, page: Int, sort: String) = getValidatorsAtHeight(latestHeight, count, page, sort)

    fun getValidatorsAtHeight(blockHeight: Int, count: Int, page: Int, sort: String) = let {
        val validatorsResponse = getValidators(blockHeight)
        val validators = if ("asc" == sort.toLowerCase()) validatorsResponse.validators.sortedBy { it.address }
        else validatorsResponse.validators.sortedByDescending { it.address }
        hydrateValidatorResponse(validators, (count * page), count)
    }

    fun hydrateValidatorResponse(validators: List<Validator>, startIndex: Int, perPage: Int) = let {
        val endIndex = if (perPage + startIndex >= validators.size) validators.size else perPage + startIndex
        if (startIndex > validators.size)
            PagedResults(validators.size / perPage, listOf<ValidatorDetail>())
        else PagedResults(validators.size / perPage, validators.subList(startIndex, endIndex)
                .map { v -> ValidatorDetail(v.votingPower.toInt(), "", v.address, 0) })
    }

    fun getRecentTransactions(count: Int, page: Int, sort: String) = runBlocking(Dispatchers.IO) {
        var height = latestHeight - 1000 * (page + 1)
        var recentTxs = mutableListOf<Transaction>()
        while (count != recentTxs.size) {
            val jsonString = getRestResult(recentTransactionUrl(height, count, page)).toString()
            val txs = OBJECT_MAPPER.readValue(jsonString, TXSearchResult::class.java).txs
            if (txs.size == count) recentTxs.addAll(txs)
            height -= 1000
        }
        val result = recentTxs.map { tx ->
            async {
                val txBlock = getBlock(tx.height.toInt())
                hydrateRecentTransaction(tx, txBlock)
            }
        }.awaitAll()
        if (!sort.isNullOrEmpty() && sort.toLowerCase() == "asc") result.reversed()
        PagedResults(latestHeight / count, result)
    }

    private fun recentTransactionUrl(height: Int, count: Int, page: Int) = "${explorerProperties.pbUrl}tx_search?query=\"tx.height>$height\"&page=$page&per_page=$count&order_by=\"desc\""

    fun getTransactionByHash(hash: String) = let {
        var tx = cacheService.getTransactionByHash(hash)
        if (tx == null) {
            logger.info("cache miss for transaction hash $hash")
            val url = "${explorerProperties.pbUrl}tx?hash=" + if (!hash.startsWith("0x")) "0x$hash" else hash
            tx = getRestResult(url)
            cacheService.addTransactionToCache(hash, tx)
        }
        val transaction = OBJECT_MAPPER.readValue(tx.toString(), Transaction::class.java)
        hydrateTransactionDetails(transaction, getBlock(transaction.height.toInt()))
    }

    private fun eventAmountDenom(txResult: TxResult) = let {
        val eventType = txResult.events[1].type
        val log = OBJECT_MAPPER.readValue(txResult.log, Array<TxLog>::class.java)
        var amount = if (eventType == "transfer") log[0].events[1].attributes.find { a -> a.key == "amount" }?.value?.replace("vspn", "")!!.toInt() else 0
        var denom = if (eventType == "transfer") "vspn" else ""
        Triple(eventType, amount, denom)
    }

    fun hydrateRecentTransaction(transaction: Transaction, block: BlockResponse) = let {
        val eventAmountDenom = eventAmountDenom(transaction.txResult)
        RecentTx(transaction.hash, block.block.header.time, transaction.txResult.fee(explorerProperties.minGasPrice()),
                eventAmountDenom.third, eventAmountDenom.first, block.height(), "", "")
    }

    fun hydrateTransactionDetails(transaction: Transaction, block: BlockResponse) = let {
        val eventAmountDenom = eventAmountDenom(transaction.txResult)
        TxDetails(transaction.height.toInt(), transaction.txResult.gasUsed.toInt(), transaction.txResult.gasWanted.toInt(), 0, 0, block.block.header.time,
                "TODO status",
                block.block.header.time,
                transaction.txResult.fee(explorerProperties.minGasPrice()),
                eventAmountDenom.third, "TODO signer", "TODO memo", eventAmountDenom.first, "TODO from",
                eventAmountDenom.second.toInt(), eventAmountDenom.third, "TODO to")
    }

    fun getTransactionHistory(fromDate: String, toDate: String) = let {
        cacheService.getTransactionCounts(fromDate, toDate)
    }

}