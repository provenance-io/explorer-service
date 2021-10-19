package io.provenance.explorer.service

import cosmos.base.tendermint.v1beta1.Query
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheHourlyTxCountsRecord
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.ChainGasFeeCacheRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxGasCacheRecord
import io.provenance.explorer.domain.entities.TxSingleMessageCacheRecord
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.pageOfResults
import io.provenance.explorer.domain.extensions.toDecCoin
import io.provenance.explorer.domain.extensions.toHash
import io.provenance.explorer.domain.extensions.toOffset
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
import io.provenance.explorer.domain.models.explorer.ValidatorAtHeight
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
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import tendermint.types.Types
import java.math.BigDecimal

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
        val queryHeight = height ?: (blockService.getLatestBlockHeightIndex() - 1)
        val blockResponse = asyncCaching.getBlock(queryHeight, checkTxs)!!
        val nextBlock = asyncCaching.getBlock(queryHeight + 1, checkTxs)
        val validatorsResponse = validatorService.getValidatorsByHeight(queryHeight)
        hydrateBlock(blockResponse, nextBlock, validatorsResponse)
    }

    fun getRecentBlocks(count: Int, page: Int) = let {
        val currentHeight = blockService.getLatestBlockHeightIndex() - 1
        var blockHeight = if (page < 0) currentHeight else currentHeight - (count * page)
        val result = mutableListOf<BlockSummary>()
        while (result.size < count) {
            val block = asyncCaching.getBlock(blockHeight)!!
            val nextBlock = asyncCaching.getBlock(blockHeight + 1)
            val validators = validatorService.getValidatorsByHeight(blockHeight)
            result.add(hydrateBlock(block, nextBlock, validators))
            blockHeight = block.block.height()
            blockHeight--
        }
        PagedResults((currentHeight / count) + 1, result, count.toLong())
    }

    private fun hydrateBlock(
        blockResponse: Query.GetBlockByHeightResponse,
        nextBlock: Query.GetBlockByHeightResponse?,
        validatorsResponse: Query.GetValidatorSetByHeightResponse
    ) = let {
        val proposer = transaction { BlockProposerRecord.findById(blockResponse.block.height())!! }
        val stakingValidator = validatorService.getStakingValidator(proposer.proposerOperatorAddress)
        val votingVals = nextBlock?.getVotingSet(props, Types.BlockIDFlag.BLOCK_ID_FLAG_ABSENT_VALUE)?.keys
        BlockSummary(
            height = blockResponse.block.height(),
            hash = blockResponse.blockId.hash.toHash(),
            time = blockResponse.block.header.time.formattedString(),
            proposerAddress = proposer.proposerOperatorAddress,
            moniker = stakingValidator.description.moniker,
            icon = validatorService.getImgUrl(stakingValidator.description.identity),
            votingPower = CountTotal(
                if (votingVals != null)
                    validatorsResponse.validatorsList.filter { it.address in votingVals }.sumOf { v -> v.votingPower.toBigInteger() }
                else null,
                validatorsResponse.validatorsList.sumOf { v -> v.votingPower.toBigInteger() }
            ),
            validatorCount = CountTotal(
                if (votingVals != null)
                    validatorsResponse.validatorsList.filter { it.address in votingVals }.size.toBigInteger()
                else null,
                validatorsResponse.validatorsCount.toBigInteger()
            ),
            txNum = blockResponse.block.data.txsCount
        )
    }

    fun getSpotlightStatistics() = cacheService.getSpotlight()

    fun createSpotlight() = getBondedTokenRatio().let {
        Spotlight(
            latestBlock = getBlockAtHeight(blockService.getMaxBlockCacheHeight() - 1),
            avgBlockTime = BlockProposerRecord.findAvgBlockCreation(100),
            bondedTokens = CountStrTotal(it.first.toString(), it.second, NHASH),
            totalTxCount = BlockCacheHourlyTxCountsRecord.getTotalTxCount().toBigInteger()
        )
    }.let { cacheService.addSpotlightToCache(it) }

    fun getBondedTokenRatio() = let {
        val totalBlockChainTokens = accountService.getCurrentSupply(NHASH)
        val totalBondedTokens = validatorService.getStakingValidators("active").sumOf { it.tokenCount }
        Pair<BigDecimal, String>(totalBondedTokens, totalBlockChainTokens)
    }

    @Deprecated(
        "Use getGasStats(DateTime, DateTime, DateTruncGranularity?)",
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

    fun getGasVolume(fromDate: DateTime, toDate: DateTime, granularity: DateTruncGranularity?) =
        TxGasCacheRecord.getGasVolume(fromDate, toDate, (granularity ?: DateTruncGranularity.DAY).name)

    fun getGasFeeStatistics(fromDate: DateTime?, toDate: DateTime?, count: Int) =
        ChainGasFeeCacheRecord.findForDates(fromDate, toDate, count).reversed()

    fun getChainId() = asyncCaching.getChainIdString()

    fun getParams(): Params = runBlocking {
        val authParams = async { accountClient.getAuthParams().params }.await()
        val bankParams = async { accountClient.getBankParams().params }.await()
        val distParams = validatorClient.getDistParams().params
        val votingParams = govClient.getParams(GovParamType.voting).votingParams
        val tallyParams = govClient.getParams(GovParamType.tallying).tallyParams
        val depositParams = govClient.getParams(GovParamType.deposit).depositParams
        val mintParams = async { accountClient.getMintParams().params }.await()
        val slashingParams = validatorClient.getSlashingParams().params
        val stakingParams = validatorClient.getStakingParams().params
        val transferParams = ibcClient.getTransferParams().params
        val clientParams = ibcClient.getClientParams().params
        val attrParams = attrClient.getAttrParams().params
        val markerParams = markerClient.getMarkerParams().params
        val metadataParams = metadataClient.getMetadataParams().params
        val nameParams = attrClient.getNameParams().params

        Params(
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

    // In point to get validators at height
    // Moved here to get block info
    fun getValidatorsAtHeight(height: Int, count: Int, page: Int) =
        aggregateValidatorsHeight(validatorService.getValidatorsByHeight(height).validatorsList, count, page, height)

    private fun aggregateValidatorsHeight(
        validatorSet: List<Query.Validator>,
        count: Int,
        page: Int,
        height: Int
    ): PagedResults<ValidatorAtHeight> {
        val status = "all"
        val valFilter = validatorSet.map { it.address }
        val stakingValidators = validatorService.getStakingValidators(status, valFilter, page.toOffset(count), count)
        val votingSet = asyncCaching.getBlock(height + 1)!!.getVotingSet(props)
        val proposer = transaction { BlockProposerRecord.findById(height)!! }
        val results = validatorService.hydrateValidators(validatorSet, stakingValidators).map {
            ValidatorAtHeight(
                it.moniker,
                it.addressId,
                it.consensusAddress,
                it.proposerPriority,
                it.votingPower,
                it.imgUrl,
                it.addressId == proposer.proposerOperatorAddress,
                votingSet[it.consensusAddress] != Types.BlockIDFlag.BLOCK_ID_FLAG_ABSENT
            )
        }
        return PagedResults(
            results.size.toLong().pageCountOfResults(count),
            results.pageOfResults(page, count),
            results.size.toLong()
        )
    }
}

fun Query.GetBlockByHeightResponse.getVotingSet(props: ExplorerProperties, filter: Int? = null) =
    this.block.lastCommit.signaturesList
        .filter { if (filter != null) it.blockIdFlagValue != filter else true }
        .associate { it.validatorAddress.translateByteArray(props).consensusAccountAddr to it.blockIdFlag }
