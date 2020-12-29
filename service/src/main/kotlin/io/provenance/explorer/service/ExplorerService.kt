package io.provenance.explorer.service

import io.provenance.core.extensions.logger
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.*
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.lang.Exception
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.SocketTimeoutException
import kotlin.system.measureTimeMillis


@Service
class ExplorerService(private val explorerProperties: ExplorerProperties,
                      private val cacheService: CacheService,
                      private val blockService: BlockService,
                      private val transactionService: TransactionService,
                      private val validatorService: ValidatorService
) {

    protected val logger = logger(ExplorerService::class)

    fun getRecentBlocks(count: Int, page: Int, sort: String) = let {
        val currentHeight = blockService.getLatestBlockHeightIndex()
        var blockHeight = if (page < 0) currentHeight else currentHeight - (count * page)
        val result = mutableListOf<RecentBlock>()
        while (result.size < count) {
            var blockMeta = blockService.getBlock(blockHeight)
            var validators = validatorService.getValidators(blockHeight)
            result.add(RecentBlock(blockMeta.header.height.toInt(),
                    blockMeta.numTxs.toInt(),
                    blockMeta.header.time,
                    blockMeta.header.proposerAddress.addressToBech32(explorerProperties.provenanceValidatorConsensusPrefix()),
                    BigDecimal("100.0000000"), //TODO Pre-commit voting power / voting power
                    validators.validators.size,
                    validators.validators.size
            ))
            blockHeight = blockMeta.header.height.toInt()
            blockHeight--
        }
        if ("asc" == sort.toLowerCase()) result.reverse()
        PagedResults((currentHeight / count) + 1, result)
    }

    fun getBlockAtHeight(height: Int?) = runBlocking(Dispatchers.IO) {
        val queryHeight = if (height == null) blockService.getLatestBlockHeightIndex() else height
        val blockResponse = async { blockService.getBlock(queryHeight) }
        val validatorsResponse = async { validatorService.getValidators(queryHeight) }
        hydrateBlock(blockResponse.await(), validatorsResponse.await())
    }

    private fun hydrateBlock(blockResponse: BlockMeta, validatorsResponse: PbValidatorsResponse) = let {
        val proposerConsAddress = blockResponse.header.proposerAddress.addressToBech32(explorerProperties.provenanceValidatorConsensusPrefix())
        val validatorAddresses = validatorService.findAddressesByConsensusAddress(proposerConsAddress)
        val stakingValidator = validatorService.getStakingValidator(validatorAddresses!!.operatorAddress)
        BlockDetail(blockResponse.header.height.toInt(),
                blockResponse.blockId.hash,
                blockResponse.header.time,
                validatorAddresses!!.operatorAddress,
                stakingValidator.description.moniker,
                "", //TODO Add icon
                validatorsResponse.validators.sumBy { v -> v.votingPower.toInt() },
                validatorsResponse.validators.size,
                blockResponse.numTxs.toInt(),
                0,
                0,
                0)
    }

    fun getRecentValidators(count: Int, page: Int, sort: String, status: String) =
            getValidatorsAtHeight(blockService.getLatestBlockHeightIndex(), count, page, sort, status)

    fun getValidatorsAtHeight(height: Int, count: Int, page: Int, sort: String, status: String) = let {
        var validators = aggregateValidators(height, count, page, status)
        validators = if ("asc" == sort.toLowerCase()) validators.sortedBy { it.votingPower }
        else validators.sortedByDescending { it.votingPower }
        PagedResults<ValidatorSummary>(validators.size / count, validators)
    }

    private fun aggregateValidators(blockHeight: Int, count: Int, page: Int, status: String) = let {
        val validators = validatorService.getValidators(blockHeight)
        val stakingValidators = validatorService.getStakingValidators(status, page, count)
        hydrateValidators(validators.validators, stakingValidators.result)
    }

    private fun hydrateValidators(validators: List<PbValidator>, stakingValidators: List<PbStakingValidator>) = let {
        val stakingPubKeys = stakingValidators.map { it.consensusPubkey }
        val signingInfos = validatorService.getSigningInfos()
        val height = signingInfos.height
        validators.filter { stakingPubKeys.contains(it.pubKey) }.map { validator ->
            val stakingValidator = stakingValidators.find { it.consensusPubkey == validator.pubKey }
            val signingInfo = signingInfos.result.find { it.address == validator.address }
            hydrateValidator(validator, stakingValidator!!, signingInfo!!, height.toInt())
        }
    }

    private fun hydrateValidator(validator: PbValidator, stakingValidator: PbStakingValidator, signingInfo: SigningInfo, height: Int) =
            ValidatorSummary(
                    moniker = stakingValidator.description.moniker,
                    addressId = stakingValidator.operatorAddress,
                    consensusAddress = validator.address,
                    proposerPriority = validator.proposerPriority.toInt(),
                    votingPower = validator.votingPower.toInt(),
                    uptime = signingInfo.uptime(height)
            )


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

    fun getTransactionsByHeight(height: Int) = transactionService.getTransactionsAtHeight(height).map { hydrateTxDetails(it) }

    fun getTransactionByHash(hash: String) = let {
        var tx = transactionService.getTxByHash(hash)
        if (tx != null) hydrateTxDetails(tx!!) else null
    }

    fun getValidator(address: String) = validatorService.getValidator(address)

    private fun hydrateTxDetails(tx: PbTransaction) = let {
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

    private fun getAverageBlockCreationTime() = let {
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