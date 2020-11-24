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


@Service
class ExplorerService(private val explorerProperties: ExplorerProperties,
                      private val restTemplate: RestTemplate,
                      private val cacheService: CacheService) {

    protected val logger = logger(ExplorerService::class)

    fun updateCache() {
        val index = cacheService.getBlockIndex()
        val startHeight = getLatestBlockHeight()
        var indexHeight = startHeight
        cacheService.updateBlockMaxHeightIndex(indexHeight)
        if (index == null || index[BlockIndexTable.minHeightRead] == null) {
            val days = mutableSetOf<String>()
            while (days.count() <= explorerProperties.initialHistoryicalDays()) {
                getBlockchain(indexHeight).blockMetas.forEach {
                    val block = OBJECT_MAPPER.readValue(it.toString(), BlockMeta::class.java)
                    cacheService.addBlockToCache(block.height(), block.numTxs.toInt(), DateTime.parse(block.header.time), it)
                    days.add(block.day())
                    indexHeight = block.height() - 1
                }
            }
        } else {
            while (indexHeight > index[BlockIndexTable.maxHeightRead]) {
                getBlockchain(indexHeight).blockMetas.forEach {
                    val block = OBJECT_MAPPER.readValue(it.toString(), BlockMeta::class.java)
                    cacheService.addBlockToCache(block.height(), block.numTxs.toInt(), DateTime.parse(block.header.time), it)
                    indexHeight = block.height() - 1
                }
            }
        }
        cacheService.updateBlockMinHeightIndex(indexHeight + 1)
    }

    fun getLatestBlockHeightIndex(): Int = cacheService.getBlockIndex()!![BlockIndexTable.maxHeightRead]

    fun getLatestBlockHeight(): Int = getStatus().syncInfo.latestBlockHeight.toInt()

    fun getStatus() = OBJECT_MAPPER.readValue(getRestResult("${explorerProperties.pbUrl}status").toJsonString(), StatusResult::class.java)

    fun getRestResult(url: String) = let {
        if (!url.contains("status")) logger.info("GET request to $url")
        restTemplate.getForEntity(url, JsonNode::class.java).let { result ->
            if (result.statusCode != HttpStatus.OK || result.body.has("error") || !result.body.has("result")) {
                logger.error("Failed calling $url status code: ${result.statusCode} response body: ${result.body}")
                throw TendermintApiException(result.body.toString())
            }
            result.body.get("result")
        }
    }

    @Scheduled(initialDelay = 0L, fixedDelay = 1000L)
    fun updateLatestBlockHeightJob() = updateCache()

    fun getRecentBlocks(count: Int, page: Int, sort: String) = let {
        val currentHeight = getLatestBlockHeightIndex()
        var blockHeight = if (page < 0) currentHeight else currentHeight - (count * page)
        val result = mutableListOf<RecentBlock>()
        while (result.size < count) {
            var blockMeta = getBlock(blockHeight)
            result.add(RecentBlock(blockMeta.header.height.toInt(),
                    blockMeta.numTxs.toInt(),
                    blockMeta.header.time))
            blockHeight = blockMeta.header.height.toInt()
            blockHeight--
        }
        if ("asc" == sort.toLowerCase()) result.reverse()
        PagedResults((currentHeight / count) + 1, result)
    }

    private fun getBlockchain(maxHeight: Int) = let {
        var blocks = cacheService.getBlockchainFromMaxHeight(maxHeight)
        if (blocks == null) {
            logger.info("cache miss for blockchain max height $maxHeight")
            blocks = getRestResult("${explorerProperties.pbUrl}blockchain?maxHeight=$maxHeight")
            cacheService.addBlockchainToCache(maxHeight, blocks)
        }
        OBJECT_MAPPER.readValue(blocks.toString(), Blockchain::class.java)
    }

    fun getBlockAtHeight(height: Int?) = runBlocking(Dispatchers.IO) {
        val queryHeight = if (height == null) getLatestBlockHeightIndex() else height
        val blockResponse = async { getBlock(queryHeight) }
        val validatorsResponse = async { getValidators(queryHeight) }
        hydrateBlock(blockResponse.await(), validatorsResponse.await())
    }

    fun hydrateBlock(blockResponse: BlockMeta, validatorsResponse: ValidatorsResponse) = let {
        BlockDetail(blockResponse.header.height.toInt(),
                blockResponse.header.time,
                blockResponse.header.proposerAddress,
                "",
                "",
                validatorsResponse.validators.sumBy { v -> v.votingPower.toInt() },
                validatorsResponse.count.toInt(),
                blockResponse.numTxs.toInt(),
                0,
                0,
                0)
    }

    fun getBlock(blockHeight: Int) = transaction {
        var block = cacheService.getBlockByHeight(blockHeight)
        if (block == null) {
            logger.info("cache miss for block height $blockHeight")
            getBlockchain(blockHeight).blockMetas.filter {
                it.get("header").get("height").asInt() == blockHeight
            }.map {
                val block = OBJECT_MAPPER.readValue(it.toString(), BlockMeta::class.java)
                cacheService.addBlockToCache(block.height(), block.numTxs.toInt(), DateTime.parse(block.header.time), it)
                block
            }
        }
        OBJECT_MAPPER.readValue(block.toString(), BlockMeta::class.java)
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

    fun getValidator(addressId: String) = getValidators(getLatestBlockHeightIndex()).validators
            .filter { v -> addressId == v.address }
            .map { v -> ValidatorDetail(v.votingPower.toInt(), v.address, "", 0) }
            .firstOrNull()

    fun getRecentValidators(count: Int, page: Int, sort: String) = getValidatorsAtHeight(getLatestBlockHeightIndex(), count, page, sort)

    fun getValidatorsAtHeight(blockHeight: Int, count: Int, page: Int, sort: String) = let {
        val validatorsResponse = getValidators(blockHeight)
        val validators = if ("asc" == sort.toLowerCase()) validatorsResponse.validators.sortedBy { it.votingPower }
        else validatorsResponse.validators.sortedByDescending { it.votingPower }
        hydrateValidatorResponse(validators, (count * page), count)
    }

    fun hydrateValidatorResponse(validators: List<Validator>, startIndex: Int, perPage: Int) = let {
        val endIndex = if (perPage + startIndex >= validators.size) validators.size else perPage + startIndex
        if (startIndex > validators.size)
            PagedResults((validators.size / perPage) + 1, listOf<ValidatorDetail>())
        else PagedResults((validators.size / perPage) + 1, validators.subList(startIndex, endIndex)
                .map { v -> ValidatorDetail(v.votingPower.toInt(), "", v.address, 0) })
    }

    fun getRecentTransactions(count: Int, page: Int, sort: String) = runBlocking(Dispatchers.IO) {
        var height = getLatestBlockHeightIndex() - 1000 * (page + 1)
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
        PagedResults((getLatestBlockHeightIndex() / count) + 1, result)
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

    fun hydrateRecentTransaction(transaction: Transaction, block: BlockMeta) = let {
        val eventMap = mapLogAttributes(transaction.txResult.log)
        RecentTx(transaction.hash, block.header.time, transaction.txResult.fee(explorerProperties.minGasPrice()),
                eventMap.getOrDefault("amount", "").replace("[\\d.]".toRegex(), ""), transaction.txResult.events[1].type, block.height(), "", "")
    }

    fun mapLogAttributes(log: String) = let {
        val log = OBJECT_MAPPER.readValue(log, Array<TxLog>::class.java)
        val eventMap = mutableMapOf<String, String>()
        log[0].events.flatMap { x -> x.attributes }.forEach { x -> eventMap.put(x.key, x.value) }
        eventMap
    }

    fun hydrateTransactionDetails(transaction: Transaction, block: BlockMeta) = let {
        val eventMap = mapLogAttributes(transaction.txResult.log)
        val denom = eventMap.getOrDefault("amount", "").replace("[\\d.]".toRegex(), "")
        val amount = eventMap.getOrDefault("amount", "0").replace("[a-zA-Z]".toRegex(), "").toInt()
        TxDetails(transaction.height.toInt(), transaction.txResult.gasUsed.toInt(), transaction.txResult.gasWanted.toInt(), 0, 0, block.header.time,
                "TODO status",
                transaction.txResult.fee(explorerProperties.minGasPrice()),
                denom, "TODO signer", "TODO memo",
                transaction.txResult.events[1].type, eventMap.getOrDefault("sender", ""), amount,
                denom, eventMap.getOrDefault("recipient", ""))
    }

    fun getTransactionHistory(fromDate: DateTime, toDate: DateTime, granularity: String) = let {
        val metrics = cacheService.getTransactionCountsForDates(fromDate.toString("yyyy-MM-dd"), toDate.toString("yyyy-MM-dd"), granularity)
        val results = mutableListOf<TxHistory>()
        while (metrics.next()) {
            results.add(TxHistory(metrics.getString(1), metrics.getInt(2)))
        }
        results
    }

}