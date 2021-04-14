package io.provenance.explorer.service

import cosmos.base.tendermint.v1beta1.Query
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.ChainGasFeeCacheRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.toHash
import io.provenance.explorer.domain.extensions.translateByteArray
import io.provenance.explorer.domain.models.explorer.BlockSummary
import io.provenance.explorer.domain.models.explorer.BondedTokens
import io.provenance.explorer.domain.models.explorer.CountTotal
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.Spotlight
import io.provenance.explorer.service.async.AsyncCaching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode


@Service
class ExplorerService(
    private val props: ExplorerProperties,
    private val cacheService: CacheService,
    private val blockService: BlockService,
    private val accountService: AccountService,
    private val validatorService: ValidatorService,
    private val asyncCaching: AsyncCaching
) {

    protected val logger = logger(ExplorerService::class)

    fun getBlockAtHeight(height: Int?) = runBlocking(Dispatchers.IO) {
        val queryHeight = height ?: blockService.getLatestBlockHeightIndex()
        val blockResponse = async { asyncCaching.getBlock(queryHeight) }
        val validatorsResponse = async { validatorService.getValidatorsByHeight(queryHeight) }
        hydrateBlock(blockResponse.await(), validatorsResponse.await())
    }

    fun getRecentBlocks(count: Int, page: Int) = let {
        val currentHeight = blockService.getLatestBlockHeightIndex()
        var blockHeight = if (page < 0) currentHeight else currentHeight - (count * page)
        val result = mutableListOf<BlockSummary>()
        while (result.size < count) {
            val block = asyncCaching.getBlock(blockHeight)
            val validators = validatorService.getValidatorsByHeight(blockHeight)
            result.add(hydrateBlock(block, validators))
            blockHeight = block.block.height()
            blockHeight--
        }
        PagedResults((currentHeight / count) + 1, result)
    }

    private fun hydrateBlock(
        blockResponse: Query.GetBlockByHeightResponse,
        validatorsResponse: Query.GetValidatorSetByHeightResponse
    ) = let {
        val proposerConsAddress = validatorService.getProposerConsensusAddr(blockResponse)
        val validatorAddresses = validatorService.findAddressByConsensus(proposerConsAddress)
        val stakingValidator = validatorService.getStakingValidator(validatorAddresses!!.operatorAddress)
        val votingVals = blockResponse.block.lastCommit.signaturesList
            .filter { it.blockIdFlagValue == 2 }
            .map { it.validatorAddress.translateByteArray(props).consensusAccountAddr }
        BlockSummary(
            height = blockResponse.block.height(),
            hash = blockResponse.blockId.hash.toHash(),
            time = blockResponse.block.header.time.formattedString(),
            proposerAddress = validatorAddresses.operatorAddress,
            moniker = stakingValidator.description.moniker,
            icon = "", //TODO Add icon
            votingPower = CountTotal(
                validatorsResponse.validatorsList.filter { it.address in votingVals }.sumOf { v -> v.votingPower.toBigInteger() },
                validatorsResponse.validatorsList.sumOf { v -> v.votingPower.toBigInteger() }),
            validatorCount = CountTotal(
                validatorsResponse.validatorsList.filter { it.address in votingVals }.size.toBigInteger(),
                validatorsResponse.validatorsCount.toBigInteger()),
            txNum = blockResponse.block.data.txsCount)
    }

    private fun getAverageBlockCreationTime() = let {
        val laggedCreationInter = BlockCacheRecord.getBlockCreationInterval(100)
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
                bondedTokens = it.first.toBigInteger().toHash(NHASH)
                    .let { coin -> BondedTokens(coin.first, it.second, coin.second) },
                totalTxCount = TxCacheRecord.getTotalTxCount()
            )
        }.let { cacheService.addSpotlightToCache(it) }

    fun getBondedTokenRatio() = let {
        val totalBlockChainTokens = accountService.getCurrentSupply(NHASH).toHash(NHASH).first
        val totalBondedTokens = validatorService.getStakingValidators("active").map { it.tokens.toLong() }.sum()
        Pair<Long, String>(totalBondedTokens, totalBlockChainTokens)
    }

    fun getGasStatistics(fromDate: DateTime, toDate: DateTime, granularity: DateTruncGranularity?) =
        TxCacheRecord.getGasStats(fromDate, toDate, (granularity ?: DateTruncGranularity.DAY).name)

    fun getGasFeeStatistics(fromDate: DateTime?, toDate: DateTime?, count: Int) =
        ChainGasFeeCacheRecord.findForDates(fromDate, toDate, count).reversed()

    fun getChainId() = asyncCaching.getChainIdString()
}
