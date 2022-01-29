package io.provenance.explorer.service

import com.google.protobuf.Any
import com.google.protobuf.util.JsonFormat
import cosmos.bank.v1beta1.params
import cosmos.base.tendermint.v1beta1.Query
import cosmos.upgrade.v1beta1.Upgrade
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.PREFIX_SCOPE
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheHourlyTxCountsRecord
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.ChainGasFeeCacheRecord
import io.provenance.explorer.domain.entities.GovProposalRecord
import io.provenance.explorer.domain.entities.TxGasCacheRecord
import io.provenance.explorer.domain.entities.TxSingleMessageCacheRecord
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.pageOfResults
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toHash
import io.provenance.explorer.domain.extensions.toObjectNodePrint
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.translateByteArray
import io.provenance.explorer.domain.models.explorer.BlockSummary
import io.provenance.explorer.domain.models.explorer.ChainPrefix
import io.provenance.explorer.domain.models.explorer.ChainUpgrade
import io.provenance.explorer.domain.models.explorer.CosmosParams
import io.provenance.explorer.domain.models.explorer.CountStrTotal
import io.provenance.explorer.domain.models.explorer.CountTotal
import io.provenance.explorer.domain.models.explorer.DateTruncGranularity
import io.provenance.explorer.domain.models.explorer.GovParamType
import io.provenance.explorer.domain.models.explorer.GovParams
import io.provenance.explorer.domain.models.explorer.IBCParams
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.Params
import io.provenance.explorer.domain.models.explorer.PrefixType
import io.provenance.explorer.domain.models.explorer.ProvParams
import io.provenance.explorer.domain.models.explorer.Spotlight
import io.provenance.explorer.domain.models.explorer.ValidatorAtHeight
import io.provenance.explorer.grpc.extensions.toDto
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import io.provenance.explorer.grpc.v1.GovGrpcClient
import io.provenance.explorer.grpc.v1.IbcGrpcClient
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import io.provenance.explorer.grpc.v1.MetadataGrpcClient
import io.provenance.explorer.grpc.v1.ValidatorGrpcClient
import io.provenance.explorer.service.async.AsyncCachingV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.client.LaxRedirectStrategy
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
    private val validatorService: ValidatorService,
    private val assetService: AssetService,
    private val asyncV2: AsyncCachingV2,
    private val govClient: GovGrpcClient,
    private val accountClient: AccountGrpcClient,
    private val ibcClient: IbcGrpcClient,
    private val attrClient: AttributeGrpcClient,
    private val metadataClient: MetadataGrpcClient,
    private val markerClient: MarkerGrpcClient,
    private val validatorClient: ValidatorGrpcClient,
    private val protoPrinter: JsonFormat.Printer
) {

    protected val logger = logger(ExplorerService::class)

    fun getBlockAtHeight(height: Int?) = runBlocking(Dispatchers.IO) {
        val queryHeight = height ?: (blockService.getMaxBlockCacheHeight() - 1)
        val blockResponse = asyncV2.getBlock(queryHeight)!!
        val nextBlock = asyncV2.getBlock(queryHeight + 1)
        val validatorsResponse = validatorService.getValidatorsByHeight(queryHeight)
        hydrateBlock(blockResponse, nextBlock, validatorsResponse)
    }

    fun getRecentBlocks(count: Int, page: Int) = let {
        val currentHeight = blockService.getLatestBlockHeightIndex() - 1
        var blockHeight = if (page < 0) currentHeight else currentHeight - (count * page)
        val result = mutableListOf<BlockSummary>()
        while (result.size < count) {
            val block = asyncV2.getBlock(blockHeight)!!
            val nextBlock = asyncV2.getBlock(blockHeight + 1)
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
                    validatorsResponse.validatorsList.filter { it.address in votingVals }
                        .sumOf { v -> v.votingPower.toBigInteger() }
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
            totalTxCount = BlockCacheHourlyTxCountsRecord.getTotalTxCount().toBigInteger(),
            totalAum = assetService.getTotalAum().toCoinStr("USD")
        )
    }.let { cacheService.addSpotlightToCache(it) }

    fun getBondedTokenRatio() = let {
        val totalBlockChainTokens = assetService.getCurrentSupply(NHASH)
        val totalBondedTokens = validatorService.getStakingValidators("active").sumOf { it.tokenCount }
        Pair<BigDecimal, String>(totalBondedTokens, totalBlockChainTokens)
    }

    fun getGasStats(fromDate: DateTime, toDate: DateTime, granularity: DateTruncGranularity?) =
        TxSingleMessageCacheRecord.getGasStats(fromDate, toDate, (granularity ?: DateTruncGranularity.DAY).name)

    fun getGasVolume(fromDate: DateTime, toDate: DateTime, granularity: DateTruncGranularity?) =
        TxGasCacheRecord.getGasVolume(fromDate, toDate, (granularity ?: DateTruncGranularity.DAY).name)

    fun getGasFeeStatistics(fromDate: DateTime?, toDate: DateTime?, count: Int) =
        ChainGasFeeCacheRecord.findForDates(fromDate, toDate, count)

    fun getChainId() = asyncV2.getChainIdString()

    fun getChainUpgrades(): List<ChainUpgrade> {
        val typeUrl = Any.pack(Upgrade.SoftwareUpgradeProposal.getDefaultInstance()).typeUrl
        val scheduledName = govClient.getIfUpgradeScheduled()?.plan?.name
        val genesis = ChainUpgrade(
            0,
            "Genesis",
            props.genesisVersionUrl.getChainVersionFromUrl(props.upgradeVersionRegex),
            props.genesisVersionUrl.getLatestPatchVersion(props.upgradeVersionRegex, null),
            false,
            false
        )
        val upgrades = GovProposalRecord.findByProposalType(typeUrl)
            .windowed(2, 1, true) { chunk ->
                (chunk.first() to chunk.getOrNull(1)).let { (one, two) ->
                    val nextUpgrade = two?.content?.get("plan")?.get("info")?.asText()
                    ChainUpgrade(
                        one.content.get("plan").get("height").asInt(),
                        one.content.get("plan").get("name").asText(),
                        one.content.get("plan").get("info").asText().getChainVersionFromUrl(props.upgradeVersionRegex),
                        one.content.get("plan").get("info").asText()
                            .getLatestPatchVersion(props.upgradeVersionRegex, nextUpgrade),
                        govClient.getIfUpgradeApplied(one.content.get("plan").get("name").asText())
                            ?.let { it.height.toInt() != one.content.get("plan").get("height").asInt() }
                            ?: true,
                        scheduledName?.let { name -> name == one.content.get("plan").get("name").asText() } ?: false
                    )
                }
            }
        return (listOf(genesis) + upgrades).sortedBy { it.upgradeHeight }
    }

    fun getChainPrefixes() = listOf(
        ChainPrefix(PrefixType.VALIDATOR, props.provValOperPrefix()),
        ChainPrefix(PrefixType.ACCOUNT, props.provAccPrefix()),
        ChainPrefix(PrefixType.SCOPE, PREFIX_SCOPE)
    )

    fun getParams(): Params = runBlocking {
        val authParams = async { accountClient.getAuthParams().params }
        val bankParams = async { accountClient.getBankParams().params }
        val distParams = validatorClient.getDistParams().params
        val votingParams = govClient.getParams(GovParamType.voting).votingParams
        val tallyParams = govClient.getParams(GovParamType.tallying).tallyParams
        val depositParams = govClient.getParams(GovParamType.deposit).depositParams
        val mintParams = async { accountClient.getMintParams().params }
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
                authParams.await().toObjectNodePrint(protoPrinter),
                bankParams.await().let { params { this.defaultSendEnabled = it.defaultSendEnabled } }
                    .toObjectNodePrint(protoPrinter),
                distParams.toDto(),
                GovParams(
                    votingParams.toObjectNodePrint(protoPrinter),
                    tallyParams.toDto(),
                    depositParams.toObjectNodePrint(protoPrinter),
                ),
                mintParams.await().toDto(),
                slashingParams.toDto(),
                stakingParams.toObjectNodePrint(protoPrinter),
                IBCParams(
                    transferParams.toObjectNodePrint(protoPrinter),
                    clientParams.toObjectNodePrint(protoPrinter),
                ),
            ),
            ProvParams(
                attrParams.toObjectNodePrint(protoPrinter),
                markerParams.toObjectNodePrint(protoPrinter),
                metadataParams.toObjectNodePrint(protoPrinter),
                nameParams.toObjectNodePrint(protoPrinter),
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
        val votingSet = asyncV2.getBlock(height + 1)!!
            .getVotingSet(props, Types.BlockIDFlag.BLOCK_ID_FLAG_ABSENT_VALUE).keys
        val proposer = transaction { BlockProposerRecord.findById(height)!! }
        val results = validatorService.hydrateValidators(validatorSet, listOf(), stakingValidators).map {
            ValidatorAtHeight(
                it.moniker,
                it.addressId,
                it.consensusAddress,
                it.proposerPriority,
                it.votingPower,
                it.imgUrl,
                it.addressId == proposer.proposerOperatorAddress,
                votingSet.contains(it.consensusAddress)
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

fun String.getChainVersionFromUrl(regex: String) = Regex(regex).find(this)?.value!!

fun String.getLatestPatchVersion(regex: String, nextUpgradeUrl: String?): String {
    val currVersion = this.getChainVersionFromUrl(regex)
    val nextVersion = nextUpgradeUrl?.getChainVersionFromUrl(regex)
    val versionList = currVersion.split(".").toTypedArray()
    var noMore = false
    var latestPatch = versionList.last().toInt()
    while (!noMore) {
        versionList[versionList.size - 1] = (latestPatch + 1).toString()
        Regex(regex).replace(this, versionList.joinToString("."))
            .checkForResponse().let {
                if (it && versionList.joinToString(".") != nextVersion)
                    latestPatch += 1
                else noMore = true
            }
    }

    versionList[versionList.size - 1] = latestPatch.toString()
    return versionList.joinToString(".")
}

// this == the URL to check for a response
fun String.checkForResponse(): Boolean {
    return HttpClients.custom().setRedirectStrategy(LaxRedirectStrategy()).build()
        .use { client ->
            client.execute(HttpGet(this)) { response ->
                !(response == null || response.statusLine.statusCode != 200)
            }
        }
}
