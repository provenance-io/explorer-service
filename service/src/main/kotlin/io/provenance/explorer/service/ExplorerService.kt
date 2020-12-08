package io.provenance.explorer.service

import com.fasterxml.jackson.core.type.TypeReference
import io.provenance.core.extensions.logger
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.*
import io.provenance.explorer.domain.Blockchain
import io.provenance.explorer.domain.Validator
import io.provenance.pbc.clients.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode


@Service
class ExplorerService(private val explorerProperties: ExplorerProperties,
                      private val cacheService: CacheService,
                      private val pbClient: PbClient,
                      private val tendermintClient: TendermintClient
) {

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

    fun getStatus() = OBJECT_MAPPER.readValue(tendermintClient.getStatus().toString(), StatusResult::class.java)

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
            blocks = tendermintClient.getBlockchain(maxHeight)
            cacheService.addBlockchainToCache(maxHeight, blocks)
        }
        OBJECT_MAPPER.readValue(blocks.toString(), Blockchain::class.java)
    }

    fun getBlockAtHeight(height: Int?) = runBlocking(Dispatchers.IO) {
        val queryHeight = if (height == null) getLatestBlockHeightIndex() else height
        val blockResponse = async { getBlock(queryHeight) }
        val validatorsResponse = async { getValidatorsV2(queryHeight) }
        hydrateBlock(blockResponse.await(), validatorsResponse.await())
    }

    fun hydrateBlock(blockResponse: BlockMeta, validatorsResponse: PbValidatorsResponse) = let {
        BlockDetail(blockResponse.header.height.toInt(),
                blockResponse.header.time,
                blockResponse.header.proposerAddress,
                "",
                "",
                validatorsResponse.validators.sumBy { v -> v.votingPower.toInt() },
                validatorsResponse.validators.size,
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

    fun hydrateValidatorResponse(validators: List<Validator>, startIndex: Int, perPage: Int) = let {
        val endIndex = if (perPage + startIndex >= validators.size) validators.size else perPage + startIndex
        if (startIndex > validators.size)
            PagedResults((validators.size / perPage) + 1, listOf<ValidatorDetail>())
        else PagedResults((validators.size / perPage) + 1, validators.subList(startIndex, endIndex)
                .map { v -> ValidatorDetail(v.votingPower.toInt(), "", v.address, BigDecimal(100.00)) })
    }


    fun getRecentValidatorsV2(count: Int, page: Int, sort: String, status: String) =
            getValidatorsAtHeight(getLatestBlockHeightIndex(), count, page, sort, status)


    fun getValidatorsAtHeight(height: Int, count: Int, page: Int, sort: String, status: String) = let {
        var validators = aggregateValidators(height, count, page, status)
        validators = if ("asc" == sort.toLowerCase()) validators.sortedBy { it.votingPower }
        else validators.sortedByDescending { it.votingPower }
        PagedResults<ValidatorDetail>(validators.size / count, validators)
    }

    fun aggregateValidators(blockHeight: Int, count: Int, page: Int, status: String) = let {
        val validators = getValidatorsV2(blockHeight)
        val stakingValidators = getRecentStakingValidators(count, page, status)
        hydrateValidatorsV2(validators.validators, stakingValidators.result)
    }

    fun getRecentStakingValidators(count: Int, page: Int, status: String) =
            OBJECT_MAPPER.readValue(pbClient.getStakingValidators(status, page, count).toString(), object : TypeReference<PbResponse<List<PbStakingValidator>>>() {})

    fun getValidatorsV2(blockHeight: Int) = let {
        var validators = cacheService.getValidatorsByHeight(blockHeight)
        if (validators == null) {
            logger.info("cache miss for validators height $blockHeight")
            validators = pbClient.getValidatorsAtHeight(getLatestBlockHeightIndex()).let {
                cacheService.addValidatorsToCache(blockHeight, it.get("result"))
                it
            }
        }
        OBJECT_MAPPER.readValue(validators.toString(), PbValidatorsResponse::class.java)
    }

    fun hydrateValidatorsV2(validators: List<PbValidator>, stakingValidators: List<PbStakingValidator>) = let {
        val stakingPubKeys = stakingValidators.map { it.consensusPubkey }
        val signingInfos = getSigningInfos()
        val height = signingInfos.height
        validators.filter { stakingPubKeys.contains(it.pubKey) }.map { validator ->
            val stakingValidator = stakingValidators.find { it.consensusPubkey == validator.pubKey }
            val signingInfo = signingInfos.result.find { it.address == validator.address }
            hydrateValidatorV2(validator, stakingValidator!!, signingInfo!!, height.toInt())
        }
    }

    //TODO: add caching
    fun getSigningInfos() = let {
        val signingInfos = pbClient.getSlashingSigningInfo()
        OBJECT_MAPPER.readValue(signingInfos.toString(), object : TypeReference<PbResponse<List<SigningInfo>>>() {})
    }

    fun hydrateValidatorV2(validator: PbValidator, stakingValidator: PbStakingValidator, signingInfo: SigningInfo, height: Int) =
            ValidatorDetail(moniker = stakingValidator.description.moniker,
                    votingPower = validator.votingPower.toInt(),
                    addressId = validator.address,
                    uptime = signingInfo.uptime(height))


    fun getRecentTransactions(count: Int, page: Int, sort: String) = runBlocking(Dispatchers.IO) {
        var height = getLatestBlockHeightIndex() - 1000 * (page + 1)
        var recentTxs = mutableListOf<Transaction>()
        while (count != recentTxs.size) {
            val jsonString = tendermintClient.getRecentTransactions(height, page, count).toString()
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

    fun getTransactionByHash(hash: String) = let {
        var tx = cacheService.getTransactionByHash(hash)
        if (tx == null) {
            logger.info("cache miss for transaction hash $hash")
            tx = tendermintClient.getTransaction(hash)
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

    fun getTransactionHistory(fromDate: DateTime, toDate: DateTime, granularity: String) = cacheService.getTransactionCountsForDates(fromDate.toString("yyyy-MM-dd"), toDate.plusDays(1).toString("yyyy-MM-dd"), granularity)

    fun getAverageBlockCreationTime() = let {
        val laggedCreationInter = cacheService.getLatestBlockCreationIntervals(100).filter { it.second != null }.map { it.second }
        var sum = BigDecimal(0.00)
        laggedCreationInter.forEach { sum = sum.add(it!!) }
        sum.divide(BigDecimal(laggedCreationInter.size), 3, RoundingMode.CEILING)
    }

    fun getSpotlightStatistics() = let {
        Spotlight(getBlockAtHeight(null), getAverageBlockCreationTime())
    }

}