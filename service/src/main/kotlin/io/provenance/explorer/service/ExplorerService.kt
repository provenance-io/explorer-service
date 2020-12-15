package io.provenance.explorer.service

import com.fasterxml.jackson.core.type.TypeReference
import io.provenance.core.extensions.logger
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.*
import io.provenance.explorer.domain.Blockchain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
            while (days.count() <= explorerProperties.initialHistoricalDays()) {
                getBlockchain(indexHeight).blockMetas.forEach {
                    val block = OBJECT_MAPPER.readValue(it.toString(), BlockMeta::class.java)
                    cacheService.addBlockToCache(block.height(), block.numTxs.toInt(), DateTime.parse(block.header.time), it)
                    days.add(block.day())
                    if (block.numTxs.toInt() > 0) {
                        addTransactionsToCache(block.height(), block.numTxs.toInt())
                    }
                    indexHeight = block.height() - 1
                }
                cacheService.updateBlockMinHeightIndex(indexHeight + 1)
            }
        } else {
            while (indexHeight > index[BlockIndexTable.maxHeightRead]) {
                getBlockchain(indexHeight).blockMetas.forEach {
                    val block = OBJECT_MAPPER.readValue(it.toString(), BlockMeta::class.java)
                    if (block.height() == index[BlockIndexTable.maxHeightRead]) return@forEach
                    cacheService.addBlockToCache(block.height(), block.numTxs.toInt(), DateTime.parse(block.header.time), it)
                    indexHeight = block.height() - 1
                    if (block.numTxs.toInt() > 0) {
                        addTransactionsToCache(block.height(), block.numTxs.toInt())
                    }
                }
            }
        }
    }

    fun addTransactionsToCache(blockHeight: Int, expectedNumTxs: Int) =
            if (cacheService.transactionCountForHeight(blockHeight) == expectedNumTxs)
                logger.info("Cache hit for transaction at height $blockHeight with $expectedNumTxs transactions")
            else {
                logger.info("Searching for $expectedNumTxs transactions at height $blockHeight")
                val searchResult = pbClient.getTxsByHeights(blockHeight, blockHeight, 1, 20)
                searchResult.txs.forEach {
                    cacheService.addTransactionToCache(it)
                }
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
        val validatorsResponse = async { getValidators(queryHeight) }
        hydrateBlock(blockResponse.await(), validatorsResponse.await())
    }

    fun hydrateBlock(blockResponse: BlockMeta, validatorsResponse: PbValidatorsResponse) = let {
        BlockDetail(blockResponse.header.height.toInt(),
                blockResponse.blockId.hash,
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

    fun getRecentValidators(count: Int, page: Int, sort: String, status: String) =
            getValidatorsAtHeight(getLatestBlockHeightIndex(), count, page, sort, status)


    fun getValidatorsAtHeight(height: Int, count: Int, page: Int, sort: String, status: String) = let {
        var validators = aggregateValidators(height, count, page, status)
        validators = if ("asc" == sort.toLowerCase()) validators.sortedBy { it.votingPower }
        else validators.sortedByDescending { it.votingPower }
        PagedResults<ValidatorDetail>(validators.size / count, validators)
    }

    fun aggregateValidators(blockHeight: Int, count: Int, page: Int, status: String) = let {
        val validators = getValidators(blockHeight)
        val stakingValidators = getRecentStakingValidators(count, page, status)
        hydrateValidators(validators.validators, stakingValidators.result)
    }

    fun getRecentStakingValidators(count: Int, page: Int, status: String) =
            OBJECT_MAPPER.readValue(pbClient.getStakingValidators(status, page, count).toString(), object : TypeReference<PbResponse<List<PbStakingValidator>>>() {})

    fun getValidators(blockHeight: Int) = let {
        var validators = cacheService.getValidatorsByHeight(blockHeight)
        if (validators == null) {
            logger.info("cache miss for validators height $blockHeight")
            validators = pbClient.getValidatorsAtHeight(getLatestBlockHeightIndex()).let {
                cacheService.addValidatorsToCache(blockHeight, it.get("result"))
                it
            }.get("result")
        }
        OBJECT_MAPPER.readValue(validators.toString(), PbValidatorsResponse::class.java)
    }

    fun hydrateValidators(validators: List<PbValidator>, stakingValidators: List<PbStakingValidator>) = let {
        val stakingPubKeys = stakingValidators.map { it.consensusPubkey }
        val signingInfos = getSigningInfos()
        val height = signingInfos.height
        validators.filter { stakingPubKeys.contains(it.pubKey) }.map { validator ->
            val stakingValidator = stakingValidators.find { it.consensusPubkey == validator.pubKey }
            val signingInfo = signingInfos.result.find { it.address == validator.address }
            hydrateValidator(validator, stakingValidator!!, signingInfo!!, height.toInt())
        }
    }

    fun hydrateValidator(validator: PbValidator, stakingValidator: PbStakingValidator, signingInfo: SigningInfo, height: Int) =
            ValidatorDetail(moniker = stakingValidator.description.moniker,
                    votingPower = validator.votingPower.toInt(),
                    addressId = stakingValidator.operatorAddress,
                    uptime = signingInfo.uptime(height)
            )


    //TODO: add caching
    fun getSigningInfos() = pbClient.getSlashingSigningInfo()

    fun getRecentTransactions(count: Int, page: Int, sort: String) = let {
        val result = cacheService.getTransactions(count, count * (page - 1)).map { tx ->
            RecentTx(tx.txhash,
                    tx.timestamp,
                    tx.fee(explorerProperties.minGasPrice()),
                    tx.tx.value.fee.amount[0].denom,
                    tx.type()!!,
                    tx.height.toInt(),
                    tx.feePayer().pubKey.value.pubKeyToBech32(explorerProperties.provenancePrefix()),
                    if (tx.code != null) "failed" else "success", tx.code, tx.codespace)
        }
        if (!sort.isNullOrEmpty() && sort.toLowerCase() == "asc") result.reversed()
        PagedResults((cacheService.transactionCount() / count) + 1, result)
    }

    fun getTransactionByHash(hash: String) = let {
        var tx = cacheService.getTransactionByHash(hash)
        if (tx == null) {
            logger.info("cache miss for transaction hash $hash")
            tx = pbClient.getTx(hash)
            cacheService.addTransactionToCache(tx)
        }
        hydrateTxDetails(tx)
    }

    fun getTransactionsByHeight(height: Int) = cacheService.getTransactionsAtHeight(height)?.map { hydrateTxDetails(it) }


    fun hydrateTxDetails(tx: PbTransaction) = let {
        TxDetails(tx.height.toInt(),
                tx.gasUsed.toInt(), tx.gasWanted.toInt(), tx.tx.value.fee.gas.toInt(),
                explorerProperties.minGasPrice(), tx.timestamp,
                if (tx.code != null) "failed" else "success", tx.code, tx.codespace,
                tx.fee(explorerProperties.minGasPrice()),
                tx.tx.value.fee.amount[0].denom,
                tx.tx.value.signatures[0].pubKey.value.pubKeyToBech32(explorerProperties.provenancePrefix()),
                tx.tx.value.memo, tx.type()!!,
                if (tx.type() == "send") tx.tx.value.msg[0].value.get("from_address").textValue() else "",
                if (tx.type() == "send") tx.tx.value.msg[0].value.get("amount").get(0).get("amount").asInt() else 0,
                if (tx.type() == "send") tx.tx.value.msg[0].value.get("amount").get(0).get("denom").textValue() else "",
                if (tx.type() == "send") tx.tx.value.msg[0].value.get("to_address").textValue() else "")
    }

    fun getTransactionHistory(fromDate: DateTime, toDate: DateTime, granularity: String) = cacheService.getTransactionCountsForDates(fromDate.toString("yyyy-MM-dd"), toDate.plusDays(1).toString("yyyy-MM-dd"), granularity)

    fun getAverageBlockCreationTime() = let {
        val laggedCreationInter = cacheService.getLatestBlockCreationIntervals(100).filter { it.second != null }.map { it.second }
        var sum = BigDecimal(0.00)
        laggedCreationInter.forEach { sum = sum.add(it!!) }
        sum.divide(BigDecimal(laggedCreationInter.size), 3, RoundingMode.CEILING)
    }

    fun getSpotlightStatistics() = let {
        var spotlight = cacheService.getSpotlight()
        if (spotlight == null) {
            logger.info("cache miss for spotlight")
            spotlight = Spotlight(getBlockAtHeight(null), getAverageBlockCreationTime())
            cacheService.addSpotlightToCache(spotlight)
        }
        spotlight
    }
}