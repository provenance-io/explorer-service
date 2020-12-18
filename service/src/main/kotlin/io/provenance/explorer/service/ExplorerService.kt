package io.provenance.explorer.service

import io.provenance.core.extensions.logger
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.*
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
                      private val validatorAddressService: ValidatorAddressService,
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
            while (days.count() <= explorerProperties.initialHistoricalDays() || indexHeight < 0) {
                getBlockchain(indexHeight).result.blockMetas.forEach { blockMeta ->
                    cacheService.addBlockToCache(blockMeta.height(), blockMeta.numTxs.toInt(), DateTime.parse(blockMeta.header.time), blockMeta)
                    days.add(blockMeta.day())
                    if (blockMeta.numTxs.toInt() > 0) {
                        addTransactionsToCache(blockMeta.height(), blockMeta.numTxs.toInt())
                    }
                    indexHeight = blockMeta.height() - 1
                }
                cacheService.updateBlockMinHeightIndex(indexHeight + 1)
            }
        } else {
            while (indexHeight > index[BlockIndexTable.maxHeightRead]) {
                getBlockchain(indexHeight).result.blockMetas.forEach { blockMeta ->
                    if (blockMeta.height() == index[BlockIndexTable.maxHeightRead]) return@forEach
                    cacheService.addBlockToCache(blockMeta.height(), blockMeta.numTxs.toInt(), DateTime.parse(blockMeta.header.time), blockMeta)
                    indexHeight = blockMeta.height() - 1
                    if (blockMeta.numTxs.toInt() > 0) {
                        addTransactionsToCache(blockMeta.height(), blockMeta.numTxs.toInt())
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

    fun getStatus() = tendermintClient.getStatus().result

    @Scheduled(initialDelay = 0L, fixedDelay = 1000L)
    fun updateLatestBlockHeightJob() = updateCache()

    fun getRecentBlocks(count: Int, page: Int, sort: String) = let {
        val currentHeight = getLatestBlockHeightIndex()
        var blockHeight = if (page < 0) currentHeight else currentHeight - (count * page)
        val result = mutableListOf<RecentBlock>()
        while (result.size < count) {
            var blockMeta = getBlock(blockHeight)
            var validators = getValidators(blockHeight)
            result.add(RecentBlock(blockMeta.header.height.toInt(),
                    blockMeta.numTxs.toInt(),
                    blockMeta.header.time,
                    blockMeta.header.proposerAddress.addressToBech32(explorerProperties.provenanceValidatorConsensusPrefix()),
                    BigDecimal("100.0000000"), //TODO WIP need to figure out how to calc this
                    validators.validators.size,
                    validators.validators.size
            ))
            blockHeight = blockMeta.header.height.toInt()
            blockHeight--
        }
        if ("asc" == sort.toLowerCase()) result.reverse()
        PagedResults((currentHeight / count) + 1, result)
    }

    private fun getBlockchain(maxHeight: Int) = tendermintClient.getBlockchain(maxHeight)

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
                blockResponse.header.proposerAddress.addressToBech32(explorerProperties.provenanceValidatorConsensusPrefix()),
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
            getBlockchain(blockHeight).result.blockMetas.filter {
                it.header.height.toInt() == blockHeight
            }.map {
                cacheService.addBlockToCache(it.height(), it.numTxs.toInt(), DateTime.parse(it.header.time), it)
                block
            }
        }
        block!!
    }

    fun getValidator(address: String) = let {
        var validatorAddresses: ValidatorAddresses? = getValidatorOperatorAddress(address)
        var validatorDetails: ValidatorDetails? = null
        if (validatorAddresses != null) {
            val currentHeight = getLatestBlockHeightIndex()
            //TODO make async and add caching
            val stakingValidator = pbClient.getStakingValidator(validatorAddresses.operatorAddress)
            val signingInfo = pbClient.getSlashingSigningInfo().result.firstOrNull { it.address == validatorAddresses.consensusAddress }
            val latestValidator = pbClient.getLatestValidators().result.validators.firstOrNull { it.address == validatorAddresses.consensusAddress }
            validatorDetails = ValidatorDetails(latestValidator!!.votingPower.toInt(), stakingValidator.result.description.moniker, validatorAddresses.operatorAddress, validatorAddresses.operatorAddress,
                    validatorAddresses.consensusPubKeyAddress, signingInfo!!.missedBlocksCounter.toInt(), currentHeight - signingInfo!!.startHeight.toInt(),
                    if (stakingValidator.result.bondHeight != null) stakingValidator.result.bondHeight.toInt() else 0, signingInfo.uptime(getLatestBlockHeightIndex()))
        }
        validatorDetails
    }

    fun getValidatorOperatorAddress(address: String) = if (address.startsWith(explorerProperties.provenanceValidatorConsensusPubKeyPrefix())) {
        validatorAddressService.findAddressesByConsensusPubKeyAddress(address)
    } else if (address.startsWith(explorerProperties.provenanceValidatorConsensusPrefix())) {
        validatorAddressService.findAddressesByConsensusAddress(address)
    } else if (address.startsWith(explorerProperties.provenanceValidatorOperatorPrefix())) {
        validatorAddressService.findAddressesByOperatorAddress(address)
    } else null

    fun getRecentValidators(count: Int, page: Int, sort: String, status: String) =
            getValidatorsAtHeight(getLatestBlockHeightIndex(), count, page, sort, status)


    fun getValidatorsAtHeight(height: Int, count: Int, page: Int, sort: String, status: String) = let {
        var validators = aggregateValidators(height, count, page, status)
        validators = if ("asc" == sort.toLowerCase()) validators.sortedBy { it.votingPower }
        else validators.sortedByDescending { it.votingPower }
        PagedResults<ValidatorSummary>(validators.size / count, validators)
    }

    fun aggregateValidators(blockHeight: Int, count: Int, page: Int, status: String) = let {
        val validators = getValidators(blockHeight)
        val stakingValidators = pbClient.getStakingValidators(status, page, count)
        hydrateValidators(validators.validators, stakingValidators.result)
    }

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
            ValidatorSummary(
                    moniker = stakingValidator.description.moniker,
                    addressId = stakingValidator.operatorAddress,
                    consensusAddress = validator.address,
                    proposerPriority = validator.proposerPriority.toInt(),
                    votingPower = validator.votingPower.toInt(),
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
                    tx.feePayer().pubKey.value.pubKeyToBech32(explorerProperties.provenanceAccountPrefix()),
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
                tx.tx.value.signatures[0].pubKey.value.pubKeyToBech32(explorerProperties.provenanceAccountPrefix()),
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