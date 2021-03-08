package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import cosmos.base.tendermint.v1beta1.Query
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.sendMsg
import io.provenance.explorer.domain.extensions.toSigObj
import io.provenance.explorer.domain.extensions.toValue
import io.provenance.explorer.domain.extensions.translateByteArray
import io.provenance.explorer.domain.extensions.type
import io.provenance.explorer.domain.models.explorer.BlockDetail
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.RecentBlock
import io.provenance.explorer.domain.models.explorer.Spotlight
import io.provenance.explorer.domain.models.explorer.TxDetails
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
            val validators = validatorService.getValidatorsByHeight(blockHeight)
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
        val validatorsResponse = async { validatorService.getValidatorsByHeight(queryHeight) }
        hydrateBlock(blockResponse.await()!!, validatorsResponse.await())
    }

    private fun hydrateBlock(
        blockResponse: Query.GetBlockByHeightResponse,
        validatorsResponse: Query.GetValidatorSetByHeightResponse
    ) = let {
        val proposerConsAddress =
            blockResponse.block.header.proposerAddress.translateByteArray(props).consensusAccountAddr
        val validatorAddresses = validatorService.findAddressByConsensus(proposerConsAddress)
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

    fun getTransactionsByHeight(height: Int) =
        transactionService.getTxsAtHeight(height).map { hydrateTxDetails(it) }

    fun getTransactionByHash(hash: String) = hydrateTxDetails(transactionService.getTxByHash(hash))

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
        fee = tx.tx.authInfo.fee.amountList[0].amount.toBigInteger(),
        feeDenomination = tx.tx.authInfo.fee.amountList[0].denom,
        signers = TxCacheRecord.findSigsByHash(tx.txResponse.txhash).toSigObj(props.provAccPrefix()),
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
        val totalBlockChainTokens = accountService.getTotalSupply("nhash")
        val totalBondedTokens = validatorService.getStakingValidators("active").map { it.tokens.toLong() }.sum()
        Pair<Long, BigDecimal>(totalBondedTokens, totalBlockChainTokens)
    }

    fun getGasStatistics(fromDate: DateTime, toDate: DateTime, granularity: String) =
        cacheService.getGasStatistics(fromDate.toString("yyyy-MM-dd"), toDate.toString("yyyy-MM-dd"), granularity)

    fun getTransactionJson(txnHash: String) = protoPrinter.print(transactionService.getTxByHash(txnHash))

    fun getChainId() = blockService.getChainIdString()
}
