package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import cosmos.base.tendermint.v1beta1.Query
import cosmos.slashing.v1beta1.Slashing
import cosmos.staking.v1beta1.Staking
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.TransactionCacheRecord
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.getStatusString
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.sendMsg
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toSigObj
import io.provenance.explorer.domain.extensions.toValue
import io.provenance.explorer.domain.extensions.translateAddress
import io.provenance.explorer.domain.extensions.translateByteArray
import io.provenance.explorer.domain.extensions.type
import io.provenance.explorer.domain.extensions.uptime
import io.provenance.explorer.domain.models.explorer.BlockDetail
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.RecentBlock
import io.provenance.explorer.domain.models.explorer.RecentTx
import io.provenance.explorer.domain.models.explorer.Signatures
import io.provenance.explorer.domain.models.explorer.Spotlight
import io.provenance.explorer.domain.models.explorer.TxDetails
import io.provenance.explorer.domain.models.explorer.ValidatorSummary
import io.provenance.explorer.grpc.toMultiSig
import io.provenance.explorer.grpc.toSingleSigKeyValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import tendermint.types.BlockOuterClass
import java.math.BigDecimal
import java.math.RoundingMode


@Service
class ExplorerService(
    private val props: ExplorerProperties,
    private val cacheService: CacheService,
    private val blockService: BlockService,
    private val accountService: AccountService,
    private val transactionService: TransactionService,
    private val validatorService: ValidatorService,
    private val protoPrinter: JsonFormat.Printer
) {

    protected val logger = logger(ExplorerService::class)

    fun getRecentBlocks(count: Int, page: Int, sort: String) = let {
        val currentHeight = blockService.getLatestBlockHeightIndex()
        var blockHeight = if (page < 0) currentHeight else currentHeight - (count * page)
        val result = mutableListOf<RecentBlock>()
        while (result.size < count) {
            val blockMeta = blockService.getBlock(blockHeight)
            val validators = validatorService.getValidators(blockHeight)
            result.add(hydrateRecentBlock(blockMeta!!.block, validators))
            blockHeight = blockMeta.block.height()
            blockHeight--
        }
        if ("asc" == sort.toLowerCase()) result.reverse()
        PagedResults((currentHeight / count) + 1, result)
    }

    fun hydrateRecentBlock(blockMeta: BlockOuterClass.Block, validators: Query.GetValidatorSetByHeightResponse) = RecentBlock(
        height = blockMeta.height(),
        txNum = blockMeta.data.txsCount,
        time = blockMeta.header.time.formattedString(),
        proposerAddress = blockMeta.header.proposerAddress.translateByteArray(props).consensusAccountAddr,
        votingPower = BigDecimal("100.0000000"), //TODO Pre-commit voting power / voting power
        validatorsNum = validators.validatorsCount,
        validatorsTotal = validators.validatorsCount
    )

    fun getBlockAtHeight(height: Int?) = runBlocking(Dispatchers.IO) {
        val queryHeight = height ?: blockService.getLatestBlockHeightIndex()
        val blockResponse = async {
            blockService.getBlock(queryHeight).also {
                if (it?.block?.data?.txsCount!! > 0)
                    transactionService.addTxsToCache(queryHeight, it.block?.data?.txsCount!!)
            }
        }
        val validatorsResponse = async { validatorService.getValidators(queryHeight) }
        hydrateBlock(blockResponse.await()!!, validatorsResponse.await())
    }

    private fun hydrateBlock(
        blockResponse: Query.GetBlockByHeightResponse,
        validatorsResponse: Query.GetValidatorSetByHeightResponse
    ) = let {
        val proposerConsAddress =
            blockResponse.block.header.proposerAddress.translateByteArray(props).consensusAccountAddr
        logger.info("proposerAddr : $proposerConsAddress")
        val validatorAddresses = validatorService.findAddressByConsensus(proposerConsAddress)
        logger.info("validatorAddressObj : $validatorAddresses")
        val stakingValidator = validatorService.getStakingValidator(validatorAddresses!!.operatorAddress)
        BlockDetail(
            height = blockResponse.block.height(),
            hash = blockResponse.blockId.hash.toValue(),
            time = blockResponse.block.header.time.formattedString(),
            proposerAddress = validatorAddresses.operatorAddress,
            moniker = stakingValidator.description.moniker,
            icon = "", //TODO Add icon
            votingPower = validatorsResponse.validatorsList.sumBy { v -> v.votingPower.toInt() },
            numValidators = validatorsResponse.validatorsCount,
            txNum = blockResponse.block.data.txsCount)
    }

    fun getRecentValidators(count: Int, page: Int, sort: String, status: String) =
        getValidatorsAtHeight(blockService.getLatestBlockHeightIndex(), count, page.toOffset(count), sort, status)

    fun getValidatorsAtHeight(height: Int, count: Int, offset: Int, sort: String, status: String) =
        aggregateValidators(height, count, offset, status).let { vals ->
            if ("asc" == sort.toLowerCase()) vals.sortedBy { it.votingPower }
            else vals.sortedByDescending { it.votingPower }
        }.let { PagedResults<ValidatorSummary>(it.size / count, it) }

    private fun aggregateValidators(blockHeight: Int, count: Int, offset: Int, status: String) = let {
        val validators = validatorService.getValidators(blockHeight)
        val stakingValidators = validatorService.getStakingValidators(status, offset, count)
        hydrateValidators(validators.validatorsList, stakingValidators)
    }

    private fun hydrateValidators(validators: List<Query.Validator>, stakingValidators: List<Staking.Validator>) = let {
        val stakingPubKeys = stakingValidators.map { it.consensusPubkey.toSingleSigKeyValue() }
        val signingInfos = validatorService.getSigningInfos()
        val height = signingInfos.first().indexOffset
        val totalVotingPower = validators.sumBy { it.votingPower.toInt() }
        validators.filter { stakingPubKeys.contains(it.pubKey.toSingleSigKeyValue()) }
            .map { validator ->
                val stakingValidator = stakingValidators.find { it.consensusPubkey.toSingleSigKeyValue() == validator.pubKey.toSingleSigKeyValue() }
                val signingInfo = signingInfos.find { it.address == validator.address }
                hydrateValidator(validator, stakingValidator!!, signingInfo!!, height.toInt(), totalVotingPower)
            }
    }

    private fun hydrateValidator(
        validator: Query.Validator,
        stakingValidator: Staking.Validator,
        signingInfo: Slashing.ValidatorSigningInfo,
        height: Int,
        totalVotingPower: Int
    ) = let {
        val validatorDelegations = validatorService.getStakingValidatorDelegations(stakingValidator.operatorAddress)
        val selfBondedAmount = validatorDelegations.delegationResponsesList
            .find { it.delegation.delegatorAddress == stakingValidator.operatorAddress.translateAddress(props).accountAddr }!!
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
            commission = BigDecimal(stakingValidator.commission.commissionRates.rate).divide(BigDecimal.TEN),
            bondedTokens = stakingValidator.tokens.toLong(),
            bondedTokensDenomination = "nhash",
            selfBonded = BigDecimal(selfBondedAmount.amount),
            selfBondedDenomination = selfBondedAmount.denom,
            delegators = validatorDelegations.delegationResponsesCount,
            bondHeight = 0,
            status = stakingValidator.getStatusString()
        )
    }

    fun getRecentTransactions(count: Int, page: Int, sort: String) =
        transactionService.getTxs(count, page.toOffset(count)).map { data ->
            RecentTx(
                txHash = data.tx.txResponse.txhash,
                time = data.timestamp.toString(),
                fee = data.tx.tx.authInfo.fee.amountList[0].amount.toBigDecimal(),
                denomination = data.tx.tx.authInfo.fee.amountList[0].denom,
                type = data.type,
                blockHeight = data.tx.txResponse.height.toInt(),
                signers = TransactionCacheRecord.findSigsByHash(data.tx.txResponse.txhash).toSigObj(),
                status = if (data.tx.txResponse.code > 0) "failed" else "success",
                errorCode = data.tx.txResponse.code,
                codespace = data.tx.txResponse.codespace)
        }.let {
            if (sort.isNotEmpty() && sort.toLowerCase() == "asc") it.reversed()
            PagedResults((transactionService.txCount() / count) + 1, it)
        }

    fun getTransactionsByHeight(height: Int) =
        transactionService.getTxsAtHeight(height).map { hydrateTxDetails(it) }

    fun getTransactionByHash(hash: String) = hydrateTxDetails(transactionService.getTxByHash(hash))

    fun getValidator(address: String) = validatorService.getValidator(address)

    private fun hydrateTxDetails(tx: ServiceOuterClass.GetTxResponse) = TxDetails(
        height = tx.txResponse.height.toInt(),
        gasUsed = tx.txResponse.gasUsed.toInt(),
        gasWanted = tx.txResponse.gasWanted.toInt(),
        gasLimit = tx.tx.authInfo.fee.gasLimit.toInt(),
        gasPrice = props.minGasPrice(),
        time = blockService.getBlock(tx.txResponse.height.toInt())!!.block.header.time.formattedString(),
        status = if (tx.txResponse.code > 0) "failed" else "success",
        errorCode = tx.txResponse.code,
        codespace = tx.txResponse.codespace,
        fee = tx.tx.authInfo.fee.amountList[0].amount.toBigDecimal(),
        feeDenomination = tx.tx.authInfo.fee.amountList[0].denom,
        signers = TransactionCacheRecord.findSigsByHash(tx.txResponse.txhash).toSigObj(),
        memo = tx.tx.body.memo,
        txType = tx.txResponse.type()!!,
        from = if (tx.txResponse.type() == "send") tx.sendMsg().fromAddress else "",
        amount = if (tx.txResponse.type() == "send") tx.sendMsg().amountList[0].amount.toInt() else 0,
        denomination = if (tx.txResponse.type() == "send") tx.sendMsg().amountList[0].denom else "",
        to = if (tx.txResponse.type() == "send") tx.sendMsg().toAddress else "")

    fun getTransactionHistory(fromDate: DateTime, toDate: DateTime, granularity: String) =
        blockService.getTransactionCountsForDates(
            fromDate.toString("yyyy-MM-dd"),
            toDate.plusDays(1).toString("yyyy-MM-dd"),
            granularity)

    private fun getAverageBlockCreationTime() = let {
        val laggedCreationInter = blockService.getLatestBlockCreationIntervals(100)
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
        val totalBlockChainTokens = accountService.getTotalSupply("nhash")
        do {
            val result = validatorService.getStakingValidators(Staking.BondStatus.BOND_STATUS_BONDED.toString(), page.toOffset(limit), limit)
            totalBondedTokens += result.map { it.tokens.toLong() }.sum()
            page++
        } while (result.size == limit)
        Pair<Long, BigDecimal>(totalBondedTokens, totalBlockChainTokens)
    }

    fun getGasStatistics(fromDate: DateTime, toDate: DateTime, granularity: String) =
        cacheService.getGasStatistics(fromDate.toString("yyyy-MM-dd"), toDate.toString("yyyy-MM-dd"), granularity)

    fun getTransactionJson(txnHash: String) = protoPrinter.print(transactionService.getTxByHash(txnHash))

    fun getChainId() = blockService.getChainIdString()
}
