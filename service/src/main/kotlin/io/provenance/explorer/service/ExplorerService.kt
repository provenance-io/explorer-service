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
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.concurrent.atomic.AtomicInteger

@Service
class ExplorerService(private val explorerProperties: ExplorerProperties,
                      private val restTemplate: RestTemplate,
                      private val cacheService: CacheService) {

    protected val logger = logger(ExplorerService::class)

    protected var latestHeight = AtomicInteger(getLatestBlockHeight())

    fun getLatestBlockHeight(): Int = getStatus().syncInfo.latestBlockHeight.toInt()

    fun getStatus() = OBJECT_MAPPER.readValue(getRestResult("${explorerProperties.pbUrl}status").toJsonString(), StatusResult::class.java)

    fun getRestResult(url: String) = let {
        if (!url.contains("status")) logger.info("GET request to $url")
        restTemplate.getForEntity(url, JsonNode::class.java).let { result ->
            if (result.statusCode != HttpStatus.OK || result.body.has("error") || !result.body.has("result"))
                throw Exception("Failed to calling $url status code: ${result.statusCode} response body: ${result.body}")
            result.body.get("result")
        }
    }

    @Scheduled(initialDelay = 0L, fixedDelay = 1000L)
    fun updateLatestBlockHeight() = let {
        latestHeight.set(getLatestBlockHeight())
    }

    //TODO: WIP
    //    @Scheduled(initialDelay = 0L, fixedDelay = 30000L)
    fun updateTodaysTransactionCount() = let {
        val startHeight = latestHeight.get()
        var blocks = getBlockchain(startHeight)
        val cache = cacheService.getTransactionCountByDay(blocks.blockMetas[0].day())
        val today = blocks.blockMetas[0].day();
        if (cache == null) { //new day
            var totalTxs = 0
            var totalTxBlocks = 0
            while (true) {
                var todaysBlocks = blocks.blockMetas.filter { meta -> meta.day() == today }
                totalTxs += todaysBlocks.sumBy { meta -> meta.numTxs.toInt() }
                totalTxBlocks += todaysBlocks.count { meta -> meta.numTxs.toInt() > 0 }
                if (todaysBlocks.size < 20) {
                    val max_height = if (todaysBlocks.isNotEmpty()) todaysBlocks.minHeight()
                    else blocks.blockMetas.maxHeight() + 1
                    cacheService.addTransactionCount(today, totalTxs, totalTxBlocks, null, max_height, startHeight, false)
                    break;
                }
                blocks = getBlockchain(blocks.blockMetas.minHeight() - 1)
            }
        } else {
            var totalTxs = cache[TransactionCountTable.numberTxs]
            var totalTxBlocks = cache[TransactionCountTable.numberTxBlocks]
            val indexHeight = cache[TransactionCountTable.indexHeight]
            while (true) {
                var todaysBlocks = blocks.blockMetas.filter { meta -> meta.day() == today && meta.height() > indexHeight }
                totalTxs += todaysBlocks.sumBy { meta -> meta.numTxs.toInt() }
                totalTxBlocks += todaysBlocks.count { meta -> meta.numTxs.toInt() > 0 }
                if (todaysBlocks.size < 20) {
                    cacheService.updateTransactionCount(today, totalTxs, totalTxBlocks, null, cache[TransactionCountTable.minHeight], startHeight, false)
                    break;
                }
            }
        }
    }

    fun getRecentBlocks(count: Int, page: Int, sort: String) = let {
        var blockHeight = latestHeight.get() - (count * page)
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
            blockHeight -= 1
        }
        if ("asc" == sort.toLowerCase()) result.reverse()
        PagedResults(latestHeight.get() / count, result)
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
        val queryHeight = if (height == null) latestHeight.get() else height
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

    fun getValidator(addressId: String) = getValidators(latestHeight.get()).validators
            .filter { v -> addressId == v.address }
            .map { v -> ValidatorDetail(v.votingPower.toInt(), v.address, "", 0) }
            .firstOrNull()

    fun getRecentValidators(count: Int, page: Int, sort: String) = let {
        val startHeight = latestHeight.get()
        val validatorsResponse = getValidators(startHeight)
        val validators = if ("asc" == sort.toLowerCase()) validatorsResponse.validators.sortedBy { it.address }
        else validatorsResponse.validators.sortedByDescending { it.address }
        hydrateValidatorResponse(validators, (count * page), count)
    }

    fun hydrateValidatorResponse(validators: List<Validator>, startIndex: Int, perPage: Int) = let {
        val endIndex = if(perPage + startIndex >= validators.size)  validators.size else perPage + startIndex
        if (startIndex > validators.size)
            PagedResults(validators.size / perPage, listOf<ValidatorDetail>())
        else PagedResults(validators.size / perPage, validators.subList(startIndex, endIndex)
                .map { v -> ValidatorDetail(v.votingPower.toInt(), "", v.address, 0) })
    }

    fun getRecentTransactions(count: Int, page: Int, sort: String) = runBlocking(Dispatchers.IO) {
        var height = latestHeight.get() - 1000 * (page + 1)
        var recentTxs = mutableListOf<Transaction>()
        while (count != recentTxs.size) {
            logger.info("Getting recent transactions height: $height count: $count page: $page sort: $sort")
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
        PagedResults(latestHeight.get() / count, result)
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

    private fun eventFeeDenom(txResult: TXResult) = let {
        val eventType = txResult.events[1].type
        val log = OBJECT_MAPPER.readValue(txResult.log, Array<TxLog>::class.java)
        var fee = if (eventType == "transfer") log[0].events[1].attributes.find { a -> a.key == "amount" }?.value?.replace("vspn", "")!! else ""
        var denom = if (eventType == "transfer") "vspn" else ""
        Triple(eventType, fee, denom)
    }

    fun hydrateRecentTransaction(transaction: Transaction, block: BlockResponse) = let {
        val eventFeeDenom = eventFeeDenom(transaction.txResult)
        RecentTx(transaction.hash, block.block.header.time, eventFeeDenom.second, eventFeeDenom.third, eventFeeDenom.first)
    }

    fun hydrateTransactionDetails(transaction: Transaction, block: BlockResponse) = let {
        val eventFeeDenom = eventFeeDenom(transaction.txResult)
        TxDetails(transaction.height.toInt(), transaction.txResult.gasUsed.toInt(), transaction.txResult.gasWanted.toInt(), 0, 0, block.block.header.time,
                "status", block.block.header.time, eventFeeDenom.second, eventFeeDenom.third, "signer", "memo", eventFeeDenom.first, "from",
                eventFeeDenom.second, eventFeeDenom.third, "to")
    }

    fun getTransactionHistory(fromDate: String, toDate: String) = let {
        cacheService.getTransactionCounts(fromDate, toDate)
    }

    fun updateTransactionCountToDate(toDate: String) = let {
        val days = cacheService.getTransactionCountsToDate(toDate)
        //loop through list of dates
        //if days does not contain date and is not complete

    }

}