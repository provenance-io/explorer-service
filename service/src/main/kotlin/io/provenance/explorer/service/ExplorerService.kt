package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.*
import kotlinx.coroutines.*
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode


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
            val blockMeta = blockService.getBlock(blockHeight)
            val validators = validatorService.getValidators(blockHeight)
            result.add(hydrateRecentBlock(blockMeta, validators))
            blockHeight = blockMeta.header.height.toInt()
            blockHeight--
        }
        if ("asc" == sort.toLowerCase()) result.reverse()
        PagedResults((currentHeight / count) + 1, result)
    }

    fun hydrateRecentBlock(blockMeta: BlockMeta, validators: PbValidatorsResponse) = RecentBlock(
            height = blockMeta.header.height.toInt(),
            txNum = blockMeta.numTxs.toInt(),
            time = blockMeta.header.time,
            proposerAddress = blockMeta.header.proposerAddress.addressToBech32(explorerProperties.provenanceValidatorConsensusPrefix()),
            votingPower = BigDecimal("100.0000000"), //TODO Pre-commit voting power / voting power
            validatorsNum = validators.validators.size,
            validatorsTotal = validators.validators.size
    )

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
        BlockDetail(
                height = blockResponse.header.height.toInt(),
                hash = blockResponse.blockId.hash,
                time = blockResponse.header.time,
                proposerAddress = validatorAddresses!!.operatorAddress,
                moniker = stakingValidator.description.moniker,
                icon = "", //TODO Add icon
                votingPower = validatorsResponse.validators.sumBy { v -> v.votingPower.toInt() },
                numValidators = validatorsResponse.validators.size,
                txNum = blockResponse.numTxs.toInt())
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
        val totalVotingPower = validators.sumBy { it.votingPower.toInt() }
        validators.filter { stakingPubKeys.contains(it.pubKey) }.map { validator ->
            val stakingValidator = stakingValidators.find { it.consensusPubkey == validator.pubKey }
            val signingInfo = signingInfos.result.find { it.address == validator.address }
            hydrateValidator(validator, stakingValidator!!, signingInfo!!, height.toInt(), totalVotingPower)
        }
    }

    private fun hydrateValidator(validator: PbValidator, stakingValidator: PbStakingValidator, signingInfo: SigningInfo, height: Int, totalVotingPower: Int) = let {
        val validatorDelegations = validatorService.getStakingValidatorDelegations(stakingValidator.operatorAddress)
        val distributions = validatorService.getValidatorDistribution(stakingValidator.operatorAddress)
        val selfBondedAmount = validatorDelegations.delegations.find { it.delegatorAddress == distributions.operatorAddress }!!.balance
        ValidatorSummary(
                moniker = stakingValidator.description.moniker,
                addressId = stakingValidator.operatorAddress,
                consensusAddress = validator.address,
                proposerPriority = validator.proposerPriority.toInt(),
                votingPower = validator.votingPower.toInt(),
                votingPowerPercent = validator.votingPower.toBigDecimal().divide(totalVotingPower.toBigDecimal(), 6, RoundingMode.HALF_UP).multiply(BigDecimal(100)),
                uptime = signingInfo.uptime(height),
                commission = BigDecimal(stakingValidator.commission.commissionRates.rate),
                bondedTokens = stakingValidator.tokens.toLong(),
                bondedTokensDenomination = "nhash",
                selfBonded = BigDecimal(selfBondedAmount.amount),
                selfBondedDenomination = selfBondedAmount.denom,
                delegators = validatorDelegations.delegations.size,
                bondHeight = if (stakingValidator.bondHeight == null) 0 else stakingValidator.bondHeight.toInt()
        )
    }

    fun getRecentTransactions(count: Int, page: Int, sort: String) = let {
        val result = cacheService.getTransactions(count, count * (page - 1)).map { tx ->
            RecentTx(
                    txHash = tx.txhash,
                    time = tx.timestamp,
                    fee = tx.fee(explorerProperties.minGasPrice()),
                    denomination = tx.tx.value.fee.amount[0].denom,
                    type = tx.type()!!,
                    blockHeight = tx.height.toInt(),
                    signer = tx.feePayer().pubKey.value.pubKeyToBech32(explorerProperties.provenanceAccountPrefix()),
                    status = if (tx.code != null) "failed" else "success",
                    errorCode = tx.code,
                    codespace = tx.codespace)
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
        TxDetails(
                height = tx.height.toInt(),
                gasUsed = tx.gasUsed.toInt(),
                gasWanted = tx.gasWanted.toInt(),
                gasLimit = tx.tx.value.fee.gas.toInt(),
                gasPrice = explorerProperties.minGasPrice(),
                time = tx.timestamp,
                status = if (tx.code != null) "failed" else "success",
                errorCode = tx.code,
                codespace = tx.codespace,
                fee = tx.fee(explorerProperties.minGasPrice()),
                feeDenomination = tx.tx.value.fee.amount[0].denom,
                signer = tx.tx.value.signatures[0].pubKey.value.pubKeyToBech32(explorerProperties.provenanceAccountPrefix()),
                memo = tx.tx.value.memo,
                txType = tx.type()!!,
                from = if (tx.type() == "send") tx.tx.value.msg[0].value.get("from_address").textValue() else "",
                amount = if (tx.type() == "send") tx.tx.value.msg[0].value.get("amount").get(0).get("amount").asInt() else 0,
                denomination = if (tx.type() == "send") tx.tx.value.msg[0].value.get("amount").get(0).get("denom").textValue() else "",
                to = if (tx.type() == "send") tx.tx.value.msg[0].value.get("to_address").textValue() else "")
    }

    fun getTransactionHistory(fromDate: DateTime, toDate: DateTime, granularity: String) = cacheService.getTransactionCountsForDates(fromDate.toString("yyyy-MM-dd"), toDate.plusDays(1).toString("yyyy-MM-dd"), granularity)

    private fun getAverageBlockCreationTime() = let {
        val laggedCreationInter = cacheService.getLatestBlockCreationIntervals(100).filter { it.second != null }.map { it.second }
        var sum = BigDecimal(0.00)
        laggedCreationInter.forEach { sum = sum.add(it!!) }
        sum.divide(laggedCreationInter.size.toBigDecimal(), 3, RoundingMode.CEILING)
    }

    fun getSpotlightStatistics() = let {
        var spotlight = cacheService.getSpotlight()
        if (spotlight == null) {
            logger.info("cache miss for spotlight")
            val bondedTokens = getBondedTokenRatio()
            spotlight = Spotlight(
                    latestBlock = getBlockAtHeight(null),
                    avgBlockTime = getAverageBlockCreationTime(),
                    bondedTokenPercent = BigDecimal(bondedTokens.first).divide(bondedTokens.second, 6, RoundingMode.HALF_UP),
                    bondedTokenAmount = bondedTokens.first,
                    bondedTokenTotal = bondedTokens.second
            )
            cacheService.addSpotlightToCache(spotlight)
        }
        spotlight
    }

    fun getBondedTokenRatio() = let {
        val limit = 10
        var page = 1
        var totalBondedTokens = 0L
        val response = blockService.getTotalSupply("nhash").result
        val totalBlockChainTokens = response.toBigDecimal()
        do {
            var result = validatorService.getStakingValidators("bonded", page, limit)
            totalBondedTokens += result.result.map { it.tokens.toLong() }.sum()
            page++
        } while (result.result.size == limit)
        Pair<Long, BigDecimal>(totalBondedTokens, totalBlockChainTokens)
    }

    fun getGasStatistics(fromDate: DateTime, toDate: DateTime, granularity: String) = cacheService.getGasStatistics(fromDate.toString("yyyy-MM-dd"), toDate.toString("yyyy-MM-dd"), granularity)

}
