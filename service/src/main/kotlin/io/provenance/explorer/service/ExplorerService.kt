package io.provenance.explorer.service

import cosmos.base.tendermint.v1beta1.Query
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.ChainGasFeeCacheRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxSingleMessageCacheRecord
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.toDecCoin
import io.provenance.explorer.domain.extensions.toHash
import io.provenance.explorer.domain.extensions.translateByteArray
import io.provenance.explorer.domain.models.explorer.AttributeParams
import io.provenance.explorer.domain.models.explorer.AuthParams
import io.provenance.explorer.domain.models.explorer.BankParams
import io.provenance.explorer.domain.models.explorer.BlockSummary
import io.provenance.explorer.domain.models.explorer.ClientParams
import io.provenance.explorer.domain.models.explorer.CosmosParams
import io.provenance.explorer.domain.models.explorer.CountStrTotal
import io.provenance.explorer.domain.models.explorer.CountTotal
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import io.provenance.explorer.domain.models.explorer.DepositParams
import io.provenance.explorer.domain.models.explorer.DistParams
import io.provenance.explorer.domain.models.explorer.GovParamType
import io.provenance.explorer.domain.models.explorer.GovParams
import io.provenance.explorer.domain.models.explorer.IBCParams
import io.provenance.explorer.domain.models.explorer.MarkerParams
import io.provenance.explorer.domain.models.explorer.MinDeposit
import io.provenance.explorer.domain.models.explorer.MintParams
import io.provenance.explorer.domain.models.explorer.NameParams
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.Params
import io.provenance.explorer.domain.models.explorer.ProvParams
import io.provenance.explorer.domain.models.explorer.SlashingParams
import io.provenance.explorer.domain.models.explorer.Spotlight
import io.provenance.explorer.domain.models.explorer.StakingParams
import io.provenance.explorer.domain.models.explorer.TallyingParams
import io.provenance.explorer.domain.models.explorer.TransferParams
import io.provenance.explorer.domain.models.explorer.VotingParams
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import io.provenance.explorer.grpc.v1.GovGrpcClient
import io.provenance.explorer.grpc.v1.IbcGrpcClient
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import io.provenance.explorer.grpc.v1.MetadataGrpcClient
import io.provenance.explorer.grpc.v1.ValidatorGrpcClient
import io.provenance.explorer.service.async.AsyncCaching
import kotlinx.coroutines.Dispatchers
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
    private val asyncCaching: AsyncCaching,
    private val govClient: GovGrpcClient,
    private val accountClient: AccountGrpcClient,
    private val ibcClient: IbcGrpcClient,
    private val attrClient: AttributeGrpcClient,
    private val metadataClient: MetadataGrpcClient,
    private val markerClient: MarkerGrpcClient,
    private val validatorClient: ValidatorGrpcClient
) {

    protected val logger = logger(ExplorerService::class)

    fun getBlockAtHeight(height: Int?, checkTxs: Boolean = false) = runBlocking(Dispatchers.IO) {
        val queryHeight = height ?: blockService.getLatestBlockHeightIndex()
        val blockResponse = asyncCaching.getBlock(queryHeight, checkTxs)
        val validatorsResponse = validatorService.getValidatorsByHeight(queryHeight)
        hydrateBlock(blockResponse, validatorsResponse)
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
        PagedResults((currentHeight / count) + 1, result, count.toLong())
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
            icon = "", // TODO Add icon
            votingPower = CountTotal(
                validatorsResponse.validatorsList.filter { it.address in votingVals }.sumOf { v -> v.votingPower.toBigInteger() },
                validatorsResponse.validatorsList.sumOf { v -> v.votingPower.toBigInteger() }
            ),
            validatorCount = CountTotal(
                validatorsResponse.validatorsList.filter { it.address in votingVals }.size.toBigInteger(),
                validatorsResponse.validatorsCount.toBigInteger()
            ),
            txNum = blockResponse.block.data.txsCount
        )
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
                bondedTokens = CountStrTotal(it.first.toString(), it.second, NHASH),
                totalTxCount = TxCacheRecord.getTotalTxCount()
            )
        }.let { cacheService.addSpotlightToCache(it) }

    fun getBondedTokenRatio() = let {
        val totalBlockChainTokens = accountService.getCurrentSupply(NHASH)
        val totalBondedTokens = validatorService.getStakingValidators("active").sumOf { it.tokenCount }
        Pair<BigDecimal, String>(totalBondedTokens, totalBlockChainTokens)
    }

    @Deprecated(
        "Use getGasStats()",
        ReplaceWith(
            "TxCacheRecord.getGasStats(fromDate, toDate, (granularity ?: DateTruncGranularity.DAY).name)",
            "io.provenance.explorer.domain.entities.TxCacheRecord",
            "io.provenance.explorer.domain.models.explorer.DateTruncGranularity"
        )
    )
    fun getGasStatistics(fromDate: DateTime, toDate: DateTime, granularity: DateTruncGranularity?) =
        TxCacheRecord.getGasStats(fromDate, toDate, (granularity ?: DateTruncGranularity.DAY).name)

    fun getGasStats(fromDate: DateTime, toDate: DateTime, granularity: DateTruncGranularity?) =
        TxSingleMessageCacheRecord.getGasStats(fromDate, toDate, (granularity ?: DateTruncGranularity.DAY).name)

    fun getGasFeeStatistics(fromDate: DateTime?, toDate: DateTime?, count: Int) =
        ChainGasFeeCacheRecord.findForDates(fromDate, toDate, count).reversed()

    fun getChainId() = asyncCaching.getChainIdString()

    fun getParams(): Params {
        val authParams = accountClient.getAuthParams().params
        val bankParams = accountClient.getBankParams().params
        val distParams = validatorClient.getDistParams().params
        val votingParams = govClient.getParams(GovParamType.voting).votingParams
        val tallyParams = govClient.getParams(GovParamType.tallying).tallyParams
        val depositParams = govClient.getParams(GovParamType.deposit).depositParams
        val mintParams = accountClient.getMintParams().params
        val slashingParams = validatorClient.getSlashingParams().params
        val stakingParams = validatorClient.getStakingParams().params
        val transferParams = ibcClient.getTransferParams().params
        val clientParams = ibcClient.getClientParams().params
        val attrParams = attrClient.getAttrParams().params
        val markerParams = markerClient.getMarkerParams().params
        val metadataParams = metadataClient.getMetadataParams().params
        val nameParams = attrClient.getNameParams().params

        return Params(
            CosmosParams(
                AuthParams(
                    authParams.maxMemoCharacters,
                    authParams.txSigLimit,
                    authParams.txSigLimit,
                    authParams.sigVerifyCostEd25519,
                    authParams.sigVerifyCostSecp256K1,
                ),
                BankParams(
                    bankParams.defaultSendEnabled,
                ),
                DistParams(
                    distParams.communityTax.toDecCoin(),
                    distParams.baseProposerReward.toDecCoin(),
                    distParams.bonusProposerReward.toDecCoin(),
                    distParams.withdrawAddrEnabled,
                ),
                GovParams(
                    VotingParams(
                        votingParams.votingPeriod.seconds,
                    ),
                    TallyingParams(
                        tallyParams.quorum.toString(Charsets.UTF_8).toDecCoin(),
                        tallyParams.threshold.toString(Charsets.UTF_8).toDecCoin(),
                        tallyParams.vetoThreshold.toString(Charsets.UTF_8).toDecCoin(),
                    ),
                    DepositParams(
                        MinDeposit(
                            depositParams.getMinDeposit(0).denom,
                            depositParams.getMinDeposit(0).amount,
                        ),
                        depositParams.maxDepositPeriod.seconds,
                    ),
                ),
                MintParams(
                    mintParams.mintDenom,
                    mintParams.inflationRateChange.toDecCoin(),
                    mintParams.inflationMax,
                    mintParams.inflationMin,
                    mintParams.goalBonded.toDecCoin(),
                    mintParams.blocksPerYear,
                ),
                SlashingParams(
                    slashingParams.signedBlocksWindow,
                    slashingParams.minSignedPerWindow.toString(Charsets.UTF_8).toDecCoin(),
                    slashingParams.downtimeJailDuration.seconds,
                    slashingParams.slashFractionDoubleSign.toString(Charsets.UTF_8).toDecCoin(),
                    slashingParams.slashFractionDowntime.toString(Charsets.UTF_8).toDecCoin(),
                ),
                StakingParams(
                    stakingParams.unbondingTime.seconds,
                    stakingParams.maxValidators,
                    stakingParams.maxEntries,
                    stakingParams.bondDenom,
                ),
                IBCParams(
                    TransferParams(
                        transferParams.sendEnabled,
                        transferParams.receiveEnabled,
                    ),
                    ClientParams(
                        clientParams.allowedClientsList,
                    ),
                ),
            ),
            ProvParams(
                AttributeParams(
                    attrParams.maxValueLength,
                ),
                MarkerParams(
                    markerParams.maxTotalSupply,
                    markerParams.enableGovernance,
                    markerParams.unrestrictedDenomRegex,
                ),
                metadataParams.toString(),
                NameParams(
                    nameParams.maxSegmentLength,
                    nameParams.minSegmentLength,
                    nameParams.maxNameLevels,
                    nameParams.allowUnrestrictedNames,
                ),
            ),
        )
    }
}
