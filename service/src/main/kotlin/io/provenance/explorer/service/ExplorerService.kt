package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.extensions.addressToBech32
import io.provenance.explorer.domain.extensions.fee
import io.provenance.explorer.domain.extensions.feePayer
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.pubKeyToBech32
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toPubKey
import io.provenance.explorer.domain.extensions.type
import io.provenance.explorer.domain.extensions.uptime
import io.provenance.explorer.domain.models.clients.pb.PbStakingValidator
import io.provenance.explorer.domain.models.clients.pb.PbTransaction
import io.provenance.explorer.domain.models.clients.pb.PbValidator
import io.provenance.explorer.domain.models.clients.pb.PbValidatorsResponse
import io.provenance.explorer.domain.models.clients.pb.SigningInfo
import io.provenance.explorer.domain.models.clients.tendermint.BlockMeta
import io.provenance.explorer.domain.models.explorer.BlockDetail
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.RecentBlock
import io.provenance.explorer.domain.models.explorer.RecentTx
import io.provenance.explorer.domain.models.explorer.Spotlight
import io.provenance.explorer.domain.models.explorer.TxDetails
import io.provenance.explorer.domain.models.explorer.ValidatorSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode


@Service
class ExplorerService(
    private val explorerProperties: ExplorerProperties,
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
            result.add(hydrateRecentBlock(blockMeta!!, validators))
            blockHeight = blockMeta.height()
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
        val queryHeight = height ?: blockService.getLatestBlockHeightIndex()
        val blockResponse = async { blockService.getBlock(queryHeight) }
        val validatorsResponse = async { validatorService.getValidators(queryHeight) }
        hydrateBlock(blockResponse.await()!!, validatorsResponse.await())
    }

    private fun hydrateBlock(blockResponse: BlockMeta, validatorsResponse: PbValidatorsResponse) = let {
        val proposerConsAddress =
            blockResponse.header.proposerAddress.addressToBech32(explorerProperties.provenanceValidatorConsensusPrefix())
        val validatorAddresses = validatorService.findAddressByConsensus(proposerConsAddress)
        val stakingValidator = validatorService.getStakingValidator(validatorAddresses!!.operatorAddress)
        BlockDetail(
            height = blockResponse.header.height.toInt(),
            hash = blockResponse.blockId.hash,
            time = blockResponse.header.time,
            proposerAddress = validatorAddresses.operatorAddress,
            moniker = stakingValidator.description.moniker,
            icon = "", //TODO Add icon
            votingPower = validatorsResponse.validators.sumBy { v -> v.votingPower.toInt() },
            numValidators = validatorsResponse.validators.size,
            txNum = blockResponse.numTxs.toInt())
    }

    fun getRecentValidators(count: Int, page: Int, sort: String, status: String) =
        getValidatorsAtHeight(
            blockService.getLatestBlockHeightIndex().also { println("Block Height: $it") }, count,
            page.toOffset(count), sort, status)

    fun getValidatorsAtHeight(height: Int, count: Int, offset: Int, sort: String, status: String) =
        aggregateValidators(height, count, offset, status).let { vals ->
            if ("asc" == sort.toLowerCase()) vals.sortedBy { it.votingPower }
            else vals.sortedByDescending { it.votingPower }
        }.let {
            PagedResults<ValidatorSummary>(it.size / count, it)
        }

    private fun aggregateValidators(blockHeight: Int, count: Int, offset: Int, status: String) = let {
        val validators = validatorService.getValidators(blockHeight)
        val stakingValidators = validatorService.getStakingValidators(status, offset, count)
        hydrateValidators(validators.validators, stakingValidators.validators)
    }

    private fun hydrateValidators(validators: List<PbValidator>, stakingValidators: List<PbStakingValidator>) = let {
        val stakingPubKeys = stakingValidators.map { it.consensusPubkey.toPubKey().value }
        val signingInfos = validatorService.getSigningInfos()
        val height = signingInfos.info.first().indexOffset
        val totalVotingPower = validators.sumBy { it.votingPower.toInt() }
        validators.filter { stakingPubKeys.contains(it.pubKey.value) }
            .map { validator ->
                val stakingValidator = stakingValidators.find { it.consensusPubkey.toPubKey().value == validator.pubKey.value }
                val signingInfo = signingInfos.info.find { it.address == validator.address }
                hydrateValidator(validator, stakingValidator!!, signingInfo!!, height.toInt(), totalVotingPower)
            }
    }

    private fun hydrateValidator(
        validator: PbValidator,
        stakingValidator: PbStakingValidator,
        signingInfo: SigningInfo,
        height: Int,
        totalVotingPower: Int
    ) = let {
        val validatorDelegations = validatorService.getStakingValidatorDelegations(stakingValidator.operatorAddress)
        val distributions = validatorService.getValidatorDistribution(stakingValidator.operatorAddress)
        val selfBondedAmount = validatorDelegations.delegations
            .find { it.delegation.delegatorAddress == distributions.operatorAddress }!!
            .balance
        ValidatorSummary(
            moniker = stakingValidator.description.moniker,
            addressId = stakingValidator.operatorAddress,
            consensusAddress = validator.address,
            proposerPriority = validator.proposerPriority.toInt(),
            votingPower = validator.votingPower.toInt(),
            votingPowerPercent = validator.votingPower.toBigDecimal()
                .divide(totalVotingPower.toBigDecimal(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100)),
            uptime = signingInfo.uptime(height),
            commission = BigDecimal(stakingValidator.commission.commissionRates.rate),
            bondedTokens = stakingValidator.tokens.toLong(),
            bondedTokensDenomination = "nhash",
            selfBonded = BigDecimal(selfBondedAmount.amount),
            selfBondedDenomination = selfBondedAmount.denom,
            delegators = validatorDelegations.delegations.size,
            bondHeight = 0
        )
    }

    fun getRecentTransactions(count: Int, page: Int, sort: String) =
        cacheService.getTransactions(count, count * (page - 1)).map { (tx, type) ->
            RecentTx(
                txHash = tx.txhash,
                time = tx.timestamp,
                fee = tx.fee(explorerProperties.minGasPrice()),
                denomination = tx.tx.value.fee.amount[0].denom,
                type = type,
                blockHeight = tx.height.toInt(),
                signer = tx.feePayer().pubKey.value.pubKeyToBech32(explorerProperties.provenanceAccountPrefix()),
                status = if (tx.code != null) "failed" else "success",
                errorCode = tx.code,
                codespace = tx.codespace)
        }.let {
            if (sort.isNotEmpty() && sort.toLowerCase() == "asc") it.reversed()
            PagedResults((cacheService.transactionCount() / count) + 1, it)
        }

    fun getTransactionsByHeight(height: Int) =
        transactionService.getTransactionsAtHeight(height).map { hydrateTxDetails(it) }

    fun getTransactionByHash(hash: String) = hydrateTxDetails(transactionService.getTxByHash(hash))

    fun getValidator(address: String) = validatorService.getValidator(address)

    private fun hydrateTxDetails(tx: PbTransaction) =
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
            amount = if (tx.type() == "send") tx.tx.value.msg[0].value.get("amount")
                .get(0)
                .get("amount")
                .asInt() else 0,
            denomination = if (tx.type() == "send") tx.tx.value.msg[0].value.get("amount")
                .get(0)
                .get("denom")
                .textValue() else "",
            to = if (tx.type() == "send") tx.tx.value.msg[0].value.get("to_address").textValue() else "")

    fun getTransactionHistory(fromDate: DateTime, toDate: DateTime, granularity: String) =
        cacheService.getTransactionCountsForDates(
            fromDate.toString("yyyy-MM-dd"),
            toDate.plusDays(1).toString("yyyy-MM-dd"),
            granularity)

    private fun getAverageBlockCreationTime() = let {
        val laggedCreationInter = cacheService.getLatestBlockCreationIntervals(100)
            .filter { it.second != null }
            .map { it.second }
        laggedCreationInter.fold(BigDecimal.ZERO, BigDecimal::add)
            .divide(laggedCreationInter.size.toBigDecimal(), 3, RoundingMode.CEILING)
    }

    fun getSpotlightStatistics() =
        cacheService.getSpotlight() ?: getBondedTokenRatio().let {
            Spotlight(
                latestBlock = getBlockAtHeight(null),
                avgBlockTime = getAverageBlockCreationTime(),
                bondedTokenPercent = BigDecimal(it.first).divide(it.second, 6, RoundingMode.HALF_UP),
                bondedTokenAmount = it.first,
                bondedTokenTotal = it.second
            )
        }.let { cacheService.addSpotlightToCache(it) }

    fun getBondedTokenRatio() = let {
        val limit = 10
        var page = 1
        var totalBondedTokens = 0L
        val response = blockService.getTotalSupply("nhash").amount.amount
        val totalBlockChainTokens = response.toBigDecimal()
        do {
            val result = validatorService.getStakingValidators("BOND_STATUS_BONDED", page.toOffset(limit), limit)
            totalBondedTokens += result.validators.map { it.tokens.toLong() }.sum()
            page++
        } while (result.validators.size == limit)
        Pair<Long, BigDecimal>(totalBondedTokens, totalBlockChainTokens)
    }

    fun getGasStatistics(fromDate: DateTime, toDate: DateTime, granularity: String) =
        cacheService.getGasStatistics(fromDate.toString("yyyy-MM-dd"), toDate.toString("yyyy-MM-dd"), granularity)

    fun getTransactionJson(txnHash: String) = transactionService.getTxByHash(txnHash)

    fun getChainId() = blockService.getChainIdString()
}
