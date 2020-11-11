package io.provenance.explorer.service

import com.fasterxml.jackson.databind.JsonNode
import io.provenance.core.extensions.logger
import io.provenance.core.extensions.toJsonString
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.concurrent.atomic.AtomicLong

@Service
@EnableScheduling
class TendermintService(private val explorerProperties: ExplorerProperties,
                        private val restTemplate: RestTemplate,
                        private val cacheService: CacheService) {

    protected val logger = logger(TendermintService::class)

    protected var latestHeight = AtomicLong(getLastestBlockHeight())

    fun getLastestBlockHeight(): Long = getStatus().syncInfo.latestBlockHeight.toLong()

    fun getStatus() = OBJECT_MAPPER.readValue(getRestResult("${explorerProperties.pbUrl}status").toJsonString(), StatusResult::class.java)

    fun getRestResult(url: String) = let {
        if(!url.contains("status")) logger.info("GET request to $url")
        restTemplate.getForEntity(url, JsonNode::class.java).let { result ->
            if (result.statusCode != HttpStatus.OK || result.body.has("error") || !result.body.has("result"))
                throw Exception("Failed to calling $url status code: ${result.statusCode} response body: ${result.body}")
            result.body.get("result")
        }
    }

    @Scheduled(initialDelay = 0L, fixedDelay = 1000L)
    fun updateLatestBlockHeight() = let {
        latestHeight.set(getLastestBlockHeight())
    }

    fun getRecentBlocks(count: Int, page: Int, sort: String) = let {
        var blockHeight = latestHeight.get() - (count * page)
        val result = mutableListOf<RecentBlock>()
        while (result.size < count) {
            var blocks = getRestResult("${explorerProperties.pbUrl}blockchain?maxHeight=$blockHeight")
            blocks.get("block_metas")?.forEach { node ->
                if (result.size < count) {
                    result.add(RecentBlock(node.get("header").get("height").asLong(),
                            node.get("num_txs").asInt(),
                            node.get("header").get("time").asText()))
                    blockHeight = node.get("header").get("height").asLong()
                }
            }
            blockHeight -= 1
        }
        if (sort.toLowerCase() == "asc") {
            result.reverse()
        }
        result
    }

    fun getBlockAtHeight(height: Long) = runBlocking(Dispatchers.IO) {
        val blockResponse = async { getBlock(height) }
        val validatorsResponse = async { getValidators(height) }
        hydrateBlock(blockResponse.await(), validatorsResponse.await())
    }

    fun hydrateBlock(blockResponse: BlockResponse, validatorsResponse: ValidatorsResponse) = let {

        BlockDetail(blockResponse.block.header.height.toLong(),
                blockResponse.block.header.time,
                blockResponse.block.header.validatorsHash,
                "",
                "",
                validatorsResponse.validators.sumBy { v -> v.votingPower.toInt() },
                validatorsResponse.count.toInt(),
                if(blockResponse.block.data.txs == null) 0 else blockResponse.block.data.txs.size,
                0,
                0,
                0)
    }

    fun getBlock(blockHeight: Long) = transaction {
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

    fun getValidators(blockHeight: Long) = let {
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

    fun getRecentTransactions(count: Int, page: Int, sort: String) = let {
        var height = latestHeight.get() - 1000 * (page + 1)
        var recentTxs = mutableListOf<Transaction>()
        while(count != recentTxs.size) {
            logger.info("Getting recent transactions height: $height count: $count page: $page sort: $sort")
            val jsonString = getRestResult(recentTransactionUrl(height, count, page)).toString()
            val txs = OBJECT_MAPPER.readValue(jsonString, TXSearchResult::class.java).txs
            if(txs.size == count)recentTxs.addAll(txs)
            height -= 1000
        }
        val result = recentTxs.map { tx ->
            val txBlock = getBlock(tx.height.toLong())
            hydrateRecentTransaction(tx, txBlock)
        }
        if (!sort.isNullOrEmpty() && sort.toLowerCase() == "asc") recentTxs.reversed()
        result
    }

    private fun recentTransactionUrl(height: Long, count:Int, page: Int) = "${explorerProperties.pbUrl}tx_search?query=\"tx.height>$height\"&page=$page&per_page=$count&order_by=\"desc\""

    fun hydrateRecentTransaction(txResult: Transaction, block: BlockResponse) = let {
        val eventType = txResult.txResult.events[1].type
        val log = OBJECT_MAPPER.readValue(txResult.txResult.log, Array<TxLog>::class.java)
        var fee = ""
        var denom = ""
        if (eventType == "transfer") {
            denom = "vspn"
            fee = log[0].events[1].attributes.find { a -> a.key == "amount" }?.value?.replace("vspn", "")!!
        }
        RecentTx(txResult.hash!!, block.block.header.time, fee, denom, eventType)
    }

}