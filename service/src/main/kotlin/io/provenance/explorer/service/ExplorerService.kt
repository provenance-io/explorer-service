package io.provenance.explorer.service

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.protobuf.util.JsonFormat
import cosmos.bank.v1beta1.params
import cosmos.base.tendermint.v1beta1.Query
import cosmos.gov.v1beta1.Gov
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.provenance.explorer.JSON_NODE_FACTORY
import io.provenance.explorer.KTOR_CLIENT_JAVA
import io.provenance.explorer.VANILLA_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ExplorerProperties.Companion.PROV_ACC_PREFIX
import io.provenance.explorer.config.ExplorerProperties.Companion.PROV_VAL_OPER_PREFIX
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheHourlyTxCountsRecord
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.CacheKeys
import io.provenance.explorer.domain.entities.CacheUpdateRecord
import io.provenance.explorer.domain.entities.ChainAumHourlyRecord
import io.provenance.explorer.domain.entities.ChainMarketRateStatsRecord
import io.provenance.explorer.domain.entities.GovProposalRecord
import io.provenance.explorer.domain.entities.TxGasCacheRecord
import io.provenance.explorer.domain.entities.TxSingleMessageCacheRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateRecord
import io.provenance.explorer.domain.extensions.average
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toHash
import io.provenance.explorer.domain.extensions.toObjectNodePrint
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.translateByteArray
import io.provenance.explorer.domain.models.explorer.GithubReleaseData
import io.provenance.explorer.domain.models.explorer.GovParamType
import io.provenance.explorer.grpc.extensions.toDto
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import io.provenance.explorer.grpc.v1.GovGrpcClient
import io.provenance.explorer.grpc.v1.IbcGrpcClient
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import io.provenance.explorer.grpc.v1.MetadataGrpcClient
import io.provenance.explorer.grpc.v1.MsgFeeGrpcClient
import io.provenance.explorer.grpc.v1.SmartContractGrpcClient
import io.provenance.explorer.grpc.v1.ValidatorGrpcClient
import io.provenance.explorer.model.BlockSummary
import io.provenance.explorer.model.ChainPrefix
import io.provenance.explorer.model.ChainUpgrade
import io.provenance.explorer.model.CosmosParams
import io.provenance.explorer.model.GovParams
import io.provenance.explorer.model.IBCParams
import io.provenance.explorer.model.MarketRateAvg
import io.provenance.explorer.model.Params
import io.provenance.explorer.model.PrefixType
import io.provenance.explorer.model.ProvParams
import io.provenance.explorer.model.Spotlight
import io.provenance.explorer.model.ValidatorAtHeight
import io.provenance.explorer.model.ValidatorState
import io.provenance.explorer.model.ValidatorState.ACTIVE
import io.provenance.explorer.model.base.CountStrTotal
import io.provenance.explorer.model.base.CountTotal
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.model.base.PREFIX_SCOPE
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.model.base.USD_UPPER
import io.provenance.explorer.service.async.BlockAndTxProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.json.JSONArray
import org.json.JSONObject
import org.springframework.stereotype.Service
import tendermint.types.ValidatorOuterClass
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class ExplorerService(
    private val props: ExplorerProperties,
    private val cacheService: CacheService,
    private val blockService: BlockService,
    private val validatorService: ValidatorService,
    private val assetService: AssetService,
    private val asyncV2: BlockAndTxProcessor,
    private val govClient: GovGrpcClient,
    private val accountClient: AccountGrpcClient,
    private val ibcClient: IbcGrpcClient,
    private val attrClient: AttributeGrpcClient,
    private val metadataClient: MetadataGrpcClient,
    private val markerClient: MarkerGrpcClient,
    private val msgFeeClient: MsgFeeGrpcClient,
    private val validatorClient: ValidatorGrpcClient,
    private val protoPrinter: JsonFormat.Printer,
    private val govService: GovService,
    private val pricingService: PricingService,
    private val scClient: SmartContractGrpcClient
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
        val votingVals = nextBlock?.getVotingSet(ValidatorOuterClass.BlockIDFlag.BLOCK_ID_FLAG_ABSENT_VALUE)?.keys
        BlockSummary(
            height = blockResponse.block.height(),
            hash = blockResponse.blockId.hash.toHash(),
            time = blockResponse.block.header.time.formattedString(),
            proposerAddress = proposer.proposerOperatorAddress,
            moniker = stakingValidator.moniker,
            icon = stakingValidator.imageUrl,
            votingPower = CountTotal(
                if (votingVals != null) {
                    validatorsResponse.validatorsList.filter { it.address in votingVals }
                        .sumOf { v -> v.votingPower.toBigInteger() }
                } else {
                    null
                },
                validatorsResponse.validatorsList.sumOf { v -> v.votingPower.toBigInteger() }
            ),
            validatorCount = CountTotal(
                if (votingVals != null) {
                    validatorsResponse.validatorsList.filter { it.address in votingVals }.size.toBigInteger()
                } else {
                    null
                },
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
            bondedTokens = CountStrTotal(it.first.toString(), it.second, UTILITY_TOKEN),
            totalTxCount = BlockCacheHourlyTxCountsRecord.getTotalTxCount().toBigInteger(),
            totalAum = pricingService.getTotalAum().toCoinStr(USD_UPPER)
        )
    }.let { cacheService.updateSpotlight(it) }

    fun getBondedTokenRatio() = let {
        val totalBlockChainTokens = assetService.getCurrentSupply(UTILITY_TOKEN)
        val totalBondedTokens = validatorService.getStakingValidators(ACTIVE).sumOf { it.tokenCount }
        Pair<BigDecimal, String>(totalBondedTokens, totalBlockChainTokens)
    }

    fun getGasStats(fromDate: LocalDateTime, toDate: LocalDateTime, granularity: DateTruncGranularity?, msgType: String?) =
        TxSingleMessageCacheRecord
            .getGasStats(fromDate, toDate, (granularity ?: DateTruncGranularity.DAY).name, msgType)

    fun getGasVolume(fromDate: LocalDateTime, toDate: LocalDateTime, granularity: DateTruncGranularity?) =
        TxGasCacheRecord.getGasVolume(fromDate, toDate, (granularity ?: DateTruncGranularity.DAY).name)

    fun getChainMarketRateStats(fromDate: LocalDateTime?, toDate: LocalDateTime?, count: Int) =
        ChainMarketRateStatsRecord.findForDates(fromDate, toDate, count)

    fun getChainMarketRateAvg(blockCount: Int) =
        ValidatorMarketRateRecord.getChainRateForBlockCount(blockCount)
            .map { it.marketRate }
            .let { list -> MarketRateAvg(list.size, list.minOrNull()!!, list.maxOrNull()!!, list.average()) }

    fun getChainId() = asyncV2.getChainIdString()

    fun getChainUpgrades(): List<ChainUpgrade> {
        val typeUrl = govService.getUpgradeProtoType()
        val scheduledName = runBlocking { govClient.getIfUpgradeScheduled()?.plan?.name }
        val proposals = GovProposalRecord.findByProposalType(typeUrl)
            .filter {
                it.status == Gov.ProposalStatus.PROPOSAL_STATUS_PASSED.name && it.getUpgradePlan() != null
            }
        val knownReleases =
            CacheUpdateRecord.fetchCacheByKey(CacheKeys.CHAIN_RELEASES.key)?.cacheValue?.let {
                VANILLA_MAPPER.readValue<List<GithubReleaseData>>(it)
            } ?: getAllChainReleases()
        val genesis = props.genesisVersionUrl.getLatestPatchVersion(
            knownReleases,
            props.upgradeVersionRegex,
            proposals.firstOrNull()?.getUpgradePlan()?.info
        ).let { (version, url) ->
            ChainUpgrade(
                0,
                "Genesis",
                props.genesisVersionUrl.getChainVersionFromUrl(props.upgradeVersionRegex),
                version,
                false,
                false,
                url
            )
        }
        val upgrades = proposals.windowed(2, 1, true) { chunk ->
            (chunk.first() to chunk.getOrNull(1)).let { (one, two) ->
                val nextUpgrade = two?.getUpgradePlan()?.info
                val (version, url) = one.getUpgradePlan()!!.info
                    .getLatestPatchVersion(knownReleases, props.upgradeVersionRegex, nextUpgrade)
                ChainUpgrade(
                    one.getUpgradePlan()!!.height.toInt(),
                    one.getUpgradePlan()!!.name,
                    one.getUpgradePlan()!!.info.getChainVersionFromUrl(props.upgradeVersionRegex),
                    version,
                    runBlocking { govClient.getIfUpgradeApplied(one.getUpgradePlan()!!.name) }.height.toInt() != one.getUpgradePlan()!!.height.toInt(),
                    scheduledName?.let { name -> name == one.getUpgradePlan()!!.name } ?: false,
                    url
                )
            }
        }
        return (listOf(genesis) + upgrades).sortedBy { it.upgradeHeight }
    }

    fun getAllChainReleases(): MutableList<GithubReleaseData> {
        var page = 1
        val pageCount = 100
        val records = getChainReleases(page, pageCount)
        while (records.size == pageCount * page) {
            page++
            records.addAll(getChainReleases(page, pageCount))
        }
        if (records.isNotEmpty()) {
            CacheUpdateRecord.updateCacheByKey(CacheKeys.CHAIN_RELEASES.key, VANILLA_MAPPER.writeValueAsString(records))
        }
        return records
    }

    // Fetches and saves the ordered list of releases
    fun getChainReleases(page: Int, count: Int): MutableList<GithubReleaseData> = runBlocking {
        val url = "https://api.github.com/repos/${props.upgradeGithubRepo}/releases?per_page=$count&page=$page"
        val res = try {
            KTOR_CLIENT_JAVA.get(url)
        } catch (e: ResponseException) {
            return@runBlocking mutableListOf<GithubReleaseData>().also { logger.error("Error: ${e.response}") }
        }

        if (res.status.value == 200) {
            try {
                JSONArray(res.body<String>()).mapNotNull { ele ->
                    if (ele is JSONObject) {
                        GithubReleaseData(
                            ele.getString("tag_name"),
                            ele.getString("created_at"),
                            ele.getString("html_url")
                        )
                    } else {
                        null
                    }
                }.sortedBy { it.createdAt.toDateTime() }.toMutableList()
            } catch (e: Exception) {
                mutableListOf<GithubReleaseData>().also { logger.error("Error: $e") }
            }
        } else {
            mutableListOf<GithubReleaseData>().also { logger.error("Error reaching Pricing Engine: ${res.status.value}") }
        }
    }

    fun String.getLatestPatchVersion(
        knownReleases: List<GithubReleaseData>,
        regex: String,
        nextUpgradeUrl: String?
    ): Pair<String, String> {
        val currMinor = this.getChainVersionFromUrl(regex).split(".").subList(0, 2).joinToString(".")
        val nextVersion = nextUpgradeUrl?.getChainVersionFromUrl(regex)
        return if (nextVersion == null) {
            knownReleases.last { it.releaseVersion.startsWith(currMinor) }.let { it.releaseVersion to it.releaseUrl }
        } else {
            val next = knownReleases.first { it.releaseVersion == nextVersion }
            knownReleases.last { it.releaseVersion.startsWith(currMinor) && it.createdAt.toDateTime() < next.createdAt.toDateTime() }
                .let { it.releaseVersion to it.releaseUrl }
        }
    }

    fun getChainPrefixes() = listOf(
        ChainPrefix(PrefixType.VALIDATOR, PROV_VAL_OPER_PREFIX),
        ChainPrefix(PrefixType.ACCOUNT, PROV_ACC_PREFIX),
        ChainPrefix(PrefixType.SCOPE, PREFIX_SCOPE)
    )

    fun getParams(): Params = runBlocking {
        val authParams = async { accountClient.getAuthParams().params }
        val bankParams = async { accountClient.getBankParams().params }
        val distParams = validatorClient.getDistParams().params
        val votingParams = async { govClient.getParams(GovParamType.voting).votingParams }
        val tallyParams = async { govClient.getParams(GovParamType.tallying).tallyParams }
        val depositParams = async { govClient.getParams(GovParamType.deposit).depositParams }
        val mintParams = async { accountClient.getMintParams().params }
        val slashingParams = validatorClient.getSlashingParams().params
        val stakingParams = validatorClient.getStakingParams().params
        val transferParams = async { ibcClient.getTransferParams().params }
        val clientParams = async { ibcClient.getClientParams().params }
//        val icaControllerParams = async {ibcClient.getIcaControllerParams().params }
        val icaHostParams = async { ibcClient.getIcaHostParams().params }
        val wasmParams = async { scClient.getWasmParams() }
        val attrParams = async { attrClient.getAttrParams().params }
        val markerParams = async { markerClient.getMarkerParams().params }
        val metadataParams = async { metadataClient.getMetadataParams().params }
        val nameParams = async { attrClient.getNameParams().params }
        val msgParams = async { msgFeeClient.getMsgFeeParams().params }

        Params(
            CosmosParams(
                authParams.await().toObjectNodePrint(protoPrinter),
                bankParams.await().let { params { this.defaultSendEnabled = it.defaultSendEnabled } }
                    .toObjectNodePrint(protoPrinter),
                distParams.toDto(),
                GovParams(
                    votingParams.await().toObjectNodePrint(protoPrinter),
                    tallyParams.await().toDto(),
                    depositParams.await().toObjectNodePrint(protoPrinter)
                ),
                mintParams.await().toDto(),
                slashingParams.toDto(),
                stakingParams.toObjectNodePrint(protoPrinter),
                IBCParams(
                    transferParams.await().toObjectNodePrint(protoPrinter),
                    clientParams.await().toObjectNodePrint(protoPrinter),
//                    icaControllerParams.await().toObjectNodePrint(protoPrinter),
                    JSON_NODE_FACTORY.objectNode(),
                    icaHostParams.await().toObjectNodePrint(protoPrinter)
                ),
                wasmParams.await().toObjectNodePrint(protoPrinter)
            ),
            ProvParams(
                attrParams.await().toObjectNodePrint(protoPrinter),
                markerParams.await().toObjectNodePrint(protoPrinter),
                metadataParams.await().toObjectNodePrint(protoPrinter),
                nameParams.await().toObjectNodePrint(protoPrinter),
                msgParams.await().toObjectNodePrint(protoPrinter)
            )
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
        val status = ValidatorState.ALL
        val valFilter = validatorSet.map { it.address }
        val stakingValidators = validatorService.getStakingValidators(status, valFilter, page.toOffset(count), count)
        val votingSet = asyncV2.getBlock(height + 1)!!
            .getVotingSet(ValidatorOuterClass.BlockIDFlag.BLOCK_ID_FLAG_ABSENT_VALUE).keys
        val proposer = transaction { BlockProposerRecord.findById(height)!! }
        val results =
            validatorService.hydrateValidators(validatorSet, listOf(), stakingValidators, height.toLong()).map {
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
            valFilter.size.toLong().pageCountOfResults(count),
            results,
            valFilter.size.toLong()
        )
    }

    fun getMsgBasedFeeList() = msgFeeClient.getMsgFees().msgFeesList.map { it.toDto() }

    fun saveChainAum() = transaction {
        val date = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
        val amount = pricingService.getTotalAum()
        ChainAumHourlyRecord.insertIgnore(date, amount, USD_UPPER)
    }

    fun getChainAumRecords(from: LocalDateTime?, to: LocalDateTime?, dayCount: Int) = transaction {
        val fromDate = from ?: LocalDateTime.now().startOfDay().minusDays(dayCount.toLong())
        val toDate = to ?: LocalDateTime.now().startOfDay()

        ChainAumHourlyRecord.getAumForPeriod(fromDate, toDate).map { it.toDto() }
    }
}

fun Query.GetBlockByHeightResponse.getVotingSet(filter: Int? = null) =
    this.block.lastCommit.signaturesList
        .filter { if (filter != null) it.blockIdFlagValue != filter else true }
        .associate { it.validatorAddress.translateByteArray().consensusAccountAddr to it.blockIdFlag }

fun String.getChainVersionFromUrl(regex: String) = Regex(regex).find(this)?.value!!
