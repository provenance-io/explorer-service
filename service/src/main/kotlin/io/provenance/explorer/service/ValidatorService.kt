package io.provenance.explorer.service

import cosmos.base.tendermint.v1beta1.Query
import cosmos.staking.v1beta1.Staking
import io.ktor.client.call.receive
import io.ktor.client.features.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.provenance.explorer.KTOR_CLIENT_JAVA
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.MissedBlocksRecord
import io.provenance.explorer.domain.entities.SpotlightCacheRecord
import io.provenance.explorer.domain.entities.StakingValidatorCacheRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateStatsRecord
import io.provenance.explorer.domain.entities.ValidatorState
import io.provenance.explorer.domain.entities.ValidatorState.ACTIVE
import io.provenance.explorer.domain.entities.ValidatorStateRecord
import io.provenance.explorer.domain.entities.ValidatorsCacheRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.average
import io.provenance.explorer.domain.extensions.avg
import io.provenance.explorer.domain.extensions.get24HrBlockHeight
import io.provenance.explorer.domain.extensions.getStatusString
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.stringfy
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toDecCoin
import io.provenance.explorer.domain.extensions.toDecimal
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toPercentage
import io.provenance.explorer.domain.extensions.toSingleSigKeyValue
import io.provenance.explorer.domain.extensions.translateAddress
import io.provenance.explorer.domain.extensions.translateByteArray
import io.provenance.explorer.domain.extensions.validatorUptime
import io.provenance.explorer.domain.models.explorer.BlockLatencyData
import io.provenance.explorer.domain.models.explorer.BlockProposer
import io.provenance.explorer.domain.models.explorer.CoinStr
import io.provenance.explorer.domain.models.explorer.CommissionList
import io.provenance.explorer.domain.models.explorer.CommissionRate
import io.provenance.explorer.domain.models.explorer.CountStrTotal
import io.provenance.explorer.domain.models.explorer.CountTotal
import io.provenance.explorer.domain.models.explorer.CurrentValidatorState
import io.provenance.explorer.domain.models.explorer.Delegation
import io.provenance.explorer.domain.models.explorer.MarketRateAvg
import io.provenance.explorer.domain.models.explorer.MissedBlockSet
import io.provenance.explorer.domain.models.explorer.MissedBlocksTimeframe
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.Timeframe
import io.provenance.explorer.domain.models.explorer.UnpaginatedDelegation
import io.provenance.explorer.domain.models.explorer.UptimeDataSet
import io.provenance.explorer.domain.models.explorer.ValidatorCommission
import io.provenance.explorer.domain.models.explorer.ValidatorCommissionHistory
import io.provenance.explorer.domain.models.explorer.ValidatorDetails
import io.provenance.explorer.domain.models.explorer.ValidatorMissedBlocks
import io.provenance.explorer.domain.models.explorer.ValidatorMoniker
import io.provenance.explorer.domain.models.explorer.ValidatorSummary
import io.provenance.explorer.domain.models.explorer.ValidatorSummaryAbbrev
import io.provenance.explorer.domain.models.explorer.ValidatorUptimeStats
import io.provenance.explorer.domain.models.explorer.hourlyBlockCount
import io.provenance.explorer.grpc.extensions.toAddress
import io.provenance.explorer.grpc.v1.ValidatorGrpcClient
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.json.JSONObject
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.BigInteger

@Service
class ValidatorService(
    private val props: ExplorerProperties,
    private val blockService: BlockService,
    private val grpcClient: ValidatorGrpcClient,
    private val cacheService: CacheService
) {

    protected val logger = logger(ValidatorService::class)

    fun getActiveSet() = grpcClient.getStakingParams().params.maxValidators

    // Assumes that the returned validators are active at that height
    fun getValidatorsByHeight(blockHeight: Int) = transaction {
        ValidatorsCacheRecord.findById(blockHeight)?.also {
            ValidatorsCacheRecord.updateHitCount(blockHeight)
        }?.validators
    } ?: throw ResourceNotFoundException("Invalid height: '$blockHeight'")

    fun buildValidatorsAtHeight(blockHeight: Int) =
        grpcClient.getValidatorsAtHeight(blockHeight).let { ValidatorsCacheRecord.buildInsert(blockHeight, it) }

    // Gets a single staking validator from cache
    fun getStakingValidator(operatorAddress: String) =
        transaction { ValidatorStateRecord.findByOperator(getActiveSet(), operatorAddress) }
            ?: saveValidator(operatorAddress)

    fun saveValidator(address: String) = transaction {
        grpcClient.getStakingValidator(address)
            .let {
                StakingValidatorCacheRecord.insertIgnore(
                    address,
                    it.operatorAddress.translateAddress(props).accountAddr,
                    it.consensusPubkey.toSingleSigKeyValue()!!,
                    it.consensusPubkey.toAddress(props.provValConsPrefix())!!
                ).also { record ->
                    ValidatorStateRecord.insertIgnore(
                        blockService.getLatestBlockHeightIndex(),
                        record.id.value,
                        it.operatorAddress,
                        it
                    )
                }
            }.also { ValidatorStateRecord.refreshCurrentStateView() }
            .let { ValidatorStateRecord.findByOperator(getActiveSet(), address)!! }
    }

    fun getMissedBlocks(valConsAddr: String) = MissedBlocksRecord.findLatestForVal(valConsAddr)

    // Returns a validator detail object for the validator
    fun getValidator(address: String) =
        getValidatorOperatorAddress(address)?.let { addr ->
            val currentHeight = blockService.getLatestBlockHeight().toBigInteger()
            val signingInfo = getSigningInfos().firstOrNull { it.address == addr.consensusAddr }
            val validatorSet = grpcClient.getLatestValidators().validatorsList
            val latestValidator = validatorSet.firstOrNull { it.address == addr.consensusAddr }
            val votingPowerTotal = validatorSet.sumOf { it.votingPower.toBigInteger() }
            validateStatus(addr, latestValidator, addr.operatorAddrId)
                .also { if (it) ValidatorStateRecord.refreshCurrentStateView() }
            val stakingValidator = getStakingValidator(addr.operatorAddress)
            ValidatorDetails(
                if (latestValidator != null) CountTotal(latestValidator.votingPower.toBigInteger(), votingPowerTotal)
                else null,
                stakingValidator.json.description.moniker,
                addr.operatorAddress,
                addr.accountAddr,
                grpcClient.getDelegatorWithdrawalAddress(addr.accountAddr),
                addr.consensusAddr,
                CountTotal(
                    (getMissedBlocks(addr.consensusAddr)?.totalCount ?: 0).toBigInteger(),
                    currentHeight - (signingInfo?.startHeight?.toBigInteger() ?: BigInteger.ZERO)
                ),
                signingInfo?.startHeight ?: currentHeight.toLong(),
                addr.consensusAddr.validatorUptime(
                    grpcClient.getSlashingParams().params.signedBlocksWindow.toBigInteger(),
                    currentHeight
                ),
                getImgUrl(stakingValidator.json.description.identity),
                stakingValidator.json.description.details,
                stakingValidator.json.description.website,
                stakingValidator.json.description.identity,
                stakingValidator.json.getStatusString(),
                if (stakingValidator.currentState != ACTIVE) stakingValidator.json.unbondingHeight else null,
                if (stakingValidator.jailed) signingInfo?.jailedUntil?.toDateTime() else null
            )
        } ?: throw ResourceNotFoundException("Invalid validator address: '$address'")

    fun validateStatus(v: CurrentValidatorState, valSet: Query.Validator?, valId: Int): Boolean =
        if ((valSet != null && v.currentState != ACTIVE) || (valSet == null && v.currentState == ACTIVE)) {
            updateStakingValidators(setOf(valId))
        } else false

    // Finds a validator address record from whatever address is passed in
    fun getValidatorOperatorAddress(address: String) = when {
        address.startsWith(props.provValOperPrefix()) -> findAddressByOperator(address)
        address.startsWith(props.provValConsPrefix()) -> findAddressByConsensus(address)
        address.startsWith(props.provAccPrefix()) -> findAddressByAccount(address)
        else -> null
    }

    fun getStakingValidators(
        status: ValidatorState,
        valSet: List<String>? = null,
        offset: Int? = null,
        limit: Int? = null
    ) =
        transaction {
            ValidatorStateRecord.findByStatus(getActiveSet(), status, valSet, offset, limit)
        }

    fun getStakingValidatorsCount(status: ValidatorState, valSet: List<String>? = null) = transaction {
        ValidatorStateRecord.findByStatusCount(getActiveSet(), status, valSet)
    }

    fun getSigningInfos() = grpcClient.getSigningInfos()

    fun findAddressByAccount(address: String) =
        ValidatorStateRecord.findByAccount(getActiveSet(), address)
            ?: discoverAddresses().let { ValidatorStateRecord.findByAccount(getActiveSet(), address) }

    fun findAddressByConsensus(address: String) =
        ValidatorStateRecord.findByConsensusAddress(getActiveSet(), address)
            ?: discoverAddresses().let { ValidatorStateRecord.findByConsensusAddress(getActiveSet(), address) }

    fun findAddressByOperator(address: String) =
        ValidatorStateRecord.findByOperator(getActiveSet(), address)
            ?: discoverAddresses().let { ValidatorStateRecord.findByOperator(getActiveSet(), address) }

    private fun discoverAddresses() {
        val stakingVals = transaction { ValidatorStateRecord.findAll(getActiveSet()).map { it.operatorAddress } }
        grpcClient.getStakingValidators()
            .map { validator ->
                if (!stakingVals.contains(validator.operatorAddress))
                    StakingValidatorCacheRecord.insertIgnore(
                        validator.operatorAddress,
                        validator.operatorAddress.translateAddress(props).accountAddr,
                        validator.consensusPubkey.toSingleSigKeyValue()!!,
                        validator.consensusPubkey.toAddress(props.provValConsPrefix())!!
                    ).also {
                        ValidatorStateRecord.insertIgnore(
                            blockService.getLatestBlockHeightIndex(),
                            it.id.value,
                            validator.operatorAddress,
                            validator
                        )
                    }.let { true }
                else false
            }.also { map -> if (map.contains(true)) ValidatorStateRecord.refreshCurrentStateView() }
    }

    // Updates the staking validator cache
    fun updateStakingValidators(vals: Set<Int>, blockHeight: Int? = null): Boolean {
        logger.info("Updating validators")
        val height = blockHeight ?: blockService.getLatestBlockHeightIndex()
        var updated = false
        vals.forEach { v ->
            val record = ValidatorStateRecord.findByValId(getActiveSet(), v)!!
            val data = grpcClient.getStakingValidator(record.operatorAddress)
            if (record.blockHeight < height && data != record.json)
                ValidatorStateRecord.insertIgnore(height, v, record.operatorAddress, data)
                    .also { if (!updated) updated = true }
        }
        return updated
    }

    // Abbreviated data used for specific cases
    fun getAllValidatorsAbbrev() = transaction {
        val recs = getStakingValidators(ValidatorState.ALL, null, null, null).map { currVal ->
            ValidatorSummaryAbbrev(
                currVal.json.description.moniker,
                currVal.operatorAddress,
                currVal.json.commission.commissionRates.rate.toDecCoin(),
                getImgUrl(currVal.json.description.identity)
            )
        }
        PagedResults(recs.size.toLong().pageCountOfResults(recs.size), recs, recs.size.toLong())
    }

    // In point to get most recent validators
    fun getRecentValidators(count: Int, page: Int, status: String) = aggregateValidatorsRecent(count, page, status)

    private fun aggregateValidatorsRecent(
        count: Int,
        page: Int,
        status: String
    ): PagedResults<ValidatorSummary> {
        val statusEnum = ValidatorState.valueOf(status.uppercase())
        val (height, validatorSet) = grpcClient.getLatestValidators().let { it.blockHeight to it.validatorsList }
        val hr24ChangeSet = grpcClient.getValidatorsAtHeight(
            height.get24HrBlockHeight(cacheService.getSpotlight().avgBlockTime).toInt()
        ).validatorsList
        getStakingValidators(statusEnum).map { v ->
            validateStatus(v, validatorSet.firstOrNull { it.address == v.consensusAddr }, v.operatorAddrId)
        }.also { map -> if (map.contains(true)) ValidatorStateRecord.refreshCurrentStateView() }
        val stakingValidators = getStakingValidators(statusEnum, null, page.toOffset(count), count)
        val results = hydrateValidators(validatorSet, hr24ChangeSet, stakingValidators, height)
        val totalCount = getStakingValidatorsCount(statusEnum, null)
        return PagedResults(totalCount.pageCountOfResults(count), results, totalCount)
    }

    fun hydrateValidators(
        validatorSet: List<Query.Validator>,
        hr24ChangeSet: List<Query.Validator>,
        stakingVals: List<CurrentValidatorState>,
        height: Long
    ) = let {
        val totalVotingPower = validatorSet.sumOf { it.votingPower.toBigInteger() }
        stakingVals
            .map { stakingVal ->
                val validator = validatorSet.firstOrNull { it.address == stakingVal.consensusAddr }
                val hr24Validator = hr24ChangeSet.firstOrNull { it.address == stakingVal.consensusAddr }
                hydrateValidator(validator, hr24Validator, stakingVal, totalVotingPower, height)
            }
    }

    private fun hydrateValidator(
        validator: Query.Validator?,
        hr24Validator: Query.Validator?,
        stakingVal: CurrentValidatorState,
        totalVotingPower: BigInteger,
        height: Long
    ) = let {
        val delegatorCount =
            grpcClient.getStakingValidatorDelegations(stakingVal.operatorAddress, 0, 1).pagination.total
        ValidatorSummary(
            moniker = stakingVal.json.description.moniker,
            addressId = stakingVal.operatorAddress,
            consensusAddress = stakingVal.consensusAddr,
            proposerPriority = validator?.proposerPriority?.toInt(),
            votingPower = if (validator != null) CountTotal(
                validator.votingPower.toBigInteger(),
                totalVotingPower
            ) else null,
            commission = stakingVal.json.commission.commissionRates.rate.toDecCoin(),
            bondedTokens = CountStrTotal(stakingVal.json.tokens, null, NHASH),
            delegators = delegatorCount,
            status = stakingVal.json.getStatusString(),
            unbondingHeight = if (stakingVal.currentState != ACTIVE) stakingVal.json.unbondingHeight else null,
            imgUrl = getImgUrl(stakingVal.json.description.identity),
            hr24Change = get24HrBondedChange(validator, hr24Validator),
            uptime = stakingVal.consensusAddr.validatorUptime(
                grpcClient.getSlashingParams().params.signedBlocksWindow.toBigInteger(), height.toBigInteger()
            )
        )
    }

    private fun get24HrBondedChange(latestVal: Query.Validator?, hr24Val: Query.Validator?) =
        ((latestVal?.votingPower ?: 0L) - (hr24Val?.votingPower ?: 0L)).let { if (it == 0L) null else it.toString() }

    private fun getValSelfBonded(stakingVal: Staking.Validator) = transaction {
        try {
            grpcClient.getValidatorSelfDelegations(
                stakingVal.operatorAddress,
                stakingVal.operatorAddress.translateAddress(props).accountAddr
            ).delegationResponse.balance.let { it.amount to it.denom }
        } catch (e: Exception) {
            "0" to ""
        }
    }

    private fun getDelegationTotal(address: String): CoinStr {
        var offset = 0
        val limit = 100

        val results = grpcClient.getStakingValidatorDelegations(address, offset, limit)
        val total = results.pagination?.total ?: results.delegationResponsesCount.toLong()
        val delegations = results.delegationResponsesList.toMutableList()

        while (delegations.count() < total) {
            offset += limit
            grpcClient.getStakingValidatorDelegations(address, offset, limit)
                .let { delegations.addAll(it.delegationResponsesList) }
        }
        return delegations.sumOf { it.balance.amount.toBigDecimal() }
            .toCoinStr(delegations.firstOrNull()?.balance?.denom ?: NHASH)
    }

    fun getBondedDelegations(address: String, page: Int, limit: Int) =
        grpcClient.getStakingValidatorDelegations(address, page.toOffset(limit), limit).let { res ->
            val list = res.delegationResponsesList.map {
                Delegation(
                    it.delegation.delegatorAddress,
                    it.delegation.validatorAddress,
                    null,
                    CoinStr(it.balance.amount, it.balance.denom),
                    null,
                    it.delegation.shares.toDecCoin(),
                    null,
                    null
                )
            }
            val rollup = mapOf("bondedTotal" to getDelegationTotal(address))
            PagedResults(res.pagination.total.pageCountOfResults(limit), list, res.pagination.total, rollup)
        }

    fun getUnbondingDelegations(address: String) =
        grpcClient.getStakingValidatorUnbondingDels(address, 0, 100).let { res ->
            res.unbondingResponsesList.flatMap { list ->
                list.entriesList.map {
                    Delegation(
                        list.delegatorAddress,
                        list.validatorAddress,
                        null,
                        CoinStr(it.balance, NHASH),
                        CoinStr(it.initialBalance, NHASH),
                        null,
                        it.creationHeight.toInt(),
                        it.completionTime.toDateTime()
                    )
                }
            }
        }.let { recs ->
            val total = recs.sumOf { it.amount.amount.toBigDecimal() }.toCoinStr(NHASH)
            UnpaginatedDelegation(recs, mapOf(Pair("unbondingTotal", total)))
        }

    fun getCommissionInfo(address: String): ValidatorCommission {
        val validator = ValidatorStateRecord.findByOperator(getActiveSet(), address)?.json
            ?: throw ResourceNotFoundException("Invalid validator address: '$address'")

        val selfBonded = getValSelfBonded(validator)
        val delegatorCount =
            grpcClient.getStakingValidatorDelegations(validator.operatorAddress, 0, 10).pagination.total
        val rewards = grpcClient.getValidatorCommission(address).commissionList.firstOrNull()
        return ValidatorCommission(
            CountStrTotal(validator.tokens, null, NHASH),
            CountStrTotal(selfBonded.first, null, selfBonded.second),
            CountStrTotal(
                validator.tokens.toBigInteger().minus(selfBonded.first.toBigInteger()).toString(),
                null,
                NHASH
            ),
            delegatorCount,
            validator.delegatorShares.toDecCoin(),
            rewards?.amount?.toDecCoin()?.let { CoinStr(it, rewards.denom) } ?: CoinStr("0", NHASH),
            CommissionRate(
                validator.commission.commissionRates.rate.toDecCoin(),
                validator.commission.commissionRates.maxRate.toDecCoin(),
                validator.commission.commissionRates.maxChangeRate.toDecCoin()
            )
        )
    }

    fun getCommissionRateHistory(address: String) =
        ValidatorStateRecord.getCommissionHistory(address)
            .map { CommissionList(it.commissionRate.stringfy(), it.blockHeight) }
            .let { ValidatorCommissionHistory(address, it) }

    fun getValidatorMarketRateAvg(address: String, txCount: Int) =
        ValidatorMarketRateRecord.getValidatorRateForBlockCount(address, txCount)
            .map { it.marketRate }
            .let { list -> MarketRateAvg(list.size, list.minOrNull()!!, list.maxOrNull()!!, list.average()) }

    fun getValidatorMarketRateStats(address: String, fromDate: DateTime?, toDate: DateTime?, count: Int) = transaction {
        ValidatorMarketRateStatsRecord.findByAddress(address, fromDate, toDate, count)
    }

    fun getProposerConsensusAddr(blockMeta: Query.GetBlockByHeightResponse) =
        blockMeta.block.header.proposerAddress.translateByteArray(props).consensusAccountAddr

    fun buildProposerInsert(
        blockMeta: Query.GetBlockByHeightResponse,
        timestamp: DateTime,
        blockHeight: Int
    ): BlockProposer {
        val consAddr = getProposerConsensusAddr(blockMeta)
        val proposer = findAddressByConsensus(consAddr)!!.operatorAddress
        return BlockProposer(blockHeight, proposer, timestamp)
    }

    fun saveMissedBlocks(blockMeta: Query.GetBlockByHeightResponse) = transaction {
        val lastBlock = blockMeta.block.lastCommit
        if (lastBlock.height.toInt() > 0) {
            val signatures = lastBlock.signaturesList
                .map { it.validatorAddress.translateByteArray(props).consensusAccountAddr }
            val currentVals = ValidatorsCacheRecord.findById(lastBlock.height.toInt())?.validators
                ?: grpcClient.getValidatorsAtHeight(lastBlock.height.toInt())

            currentVals.validatorsList.forEach { vali ->
                if (!signatures.contains(vali.address))
                    MissedBlocksRecord.insert(lastBlock.height.toInt(), vali.address)
            }
        }
    }

    fun getImgUrl(identityStr: String) = runBlocking {
        if (identityStr.isNotBlank()) {
            val res = try {
                KTOR_CLIENT_JAVA.get<HttpResponse>("https://keybase.io/_/api/1.0/user/lookup.json") {
                    parameter("key_suffix", identityStr)
                    parameter("fields", "pictures")
                }
            } catch (e: ResponseException) {
                return@runBlocking null.also { logger.error("Error reaching Keybase: ${e.response}") }
            }

            if (res.status.value in 200..299) {
                try {
                    JSONObject(res.receive<String>()).getJSONArray("them").let {
                        if (it.length() > 0) {
                            val them = it.getJSONObject(0)
                            if (them.has("pictures"))
                                them.getJSONObject("pictures")?.getJSONObject("primary")?.getString("url")
                            else null
                        } else null
                    }
                } catch (e: Exception) {
                    null
                }
            } else null.also { logger.error("Error reaching Keybase: ${res.status}") }
        } else null
    }

    fun getBlockLatencyData(address: String, blockCount: Int) =
        BlockProposerRecord.getRecordsForProposer(address, blockCount).let { res ->
            val average = res.map { it.blockLatency!! }.average()
            val data = res.associate { it.blockHeight to it.blockLatency!! }
            BlockLatencyData(address, data, average)
        }

    fun getDistinctValidatorsWithMissedBlocksInTimeframe(timeframe: Timeframe) = transaction {
        val currentHeight = SpotlightCacheRecord.getSpotlight().latestBlock.height
        val frame = when (timeframe) {
            Timeframe.WEEK -> hourlyBlockCount * 24 * 7
            Timeframe.DAY -> hourlyBlockCount * 24
            Timeframe.HOUR -> hourlyBlockCount
            Timeframe.FOREVER -> currentHeight - 1
        }

        val validators = MissedBlocksRecord
            .findDistinctValidatorsWithMissedBlocksForPeriod(currentHeight - frame, currentHeight)
            .let { ValidatorStateRecord.findByConsensusAddressIn(getActiveSet(), it.toList()) }
            .map {
                ValidatorMissedBlocks(
                    ValidatorMoniker(
                        it.consensusAddr,
                        it.operatorAddress,
                        it.moniker,
                        it.currentState
                    )
                )
            }

        MissedBlocksTimeframe(currentHeight - frame, currentHeight, validators)
    }

    fun getMissedBlocksForValidatorInTimeframe(timeframe: Timeframe, validatorAddr: String?): MissedBlocksTimeframe {
        if (timeframe == Timeframe.FOREVER && validatorAddr == null)
            throw IllegalArgumentException("If timeframe is FOREVER, you must have a validator operator address specified.")
        if (validatorAddr != null && !validatorAddr.startsWith(props.provValOperPrefix()))
            throw IllegalArgumentException("'validatorAddr' must begin with the validator operator address prefix : ${props.provValOperPrefix()}")

        val currentHeight = SpotlightCacheRecord.getSpotlight().latestBlock.height
        val frame = when (timeframe) {
            Timeframe.WEEK -> hourlyBlockCount * 24 * 7
            Timeframe.DAY -> hourlyBlockCount * 24
            Timeframe.HOUR -> hourlyBlockCount
            Timeframe.FOREVER -> currentHeight - 1
        }

        val valConsAddr = if (validatorAddr != null)
            StakingValidatorCacheRecord.findByOperAddr(validatorAddr)?.consensusAddress
        else
            null

        return getMissedBlocksForInput(currentHeight, frame, valConsAddr)
    }

    private fun getMissedBlocksForInput(currentHeight: Int, blockCount: Int, valConsAddr: String?) = transaction {
        val results = MissedBlocksRecord
            .findValidatorsWithMissedBlocksForPeriod(currentHeight - blockCount, currentHeight, valConsAddr)
        val vals =
            ValidatorStateRecord.findByConsensusAddressIn(getActiveSet(), results.map { it.validator.valConsAddress })
                .associateBy { it.consensusAddr }

        val list = results.groupBy(
            { it.validator },
            { MissedBlockSet(it.blocks.minOrNull()!!, it.blocks.maxOrNull()!!, it.blocks.size) }
        )
            .map { (k, v) -> ValidatorMissedBlocks(k, v) }
            .onEach { res ->
                vals[res.validator.valConsAddress].let { match ->
                    res.validator.operatorAddr = match?.operatorAddress
                    res.validator.moniker = match?.moniker
                    res.validator.currentState = match?.currentState
                }
            }

        MissedBlocksTimeframe(currentHeight - blockCount, currentHeight, list)
    }

    fun activeValidatorUptimeStats() = transaction {
        // Get Parameters
        val (blockCount, slashedAtPercent) =
            grpcClient.getSlashingParams().params.let { it.signedBlocksWindow to it.minSignedPerWindow }
        val slashedCount =
            slashedAtPercent.toString(Charsets.UTF_8).toDecimal().multiply(BigDecimal(blockCount)).toLong()
        // Height to count from
        val currentHeight = SpotlightCacheRecord.getSpotlight().latestBlock.height

        // validators with missed blocks
        val withMissed = getMissedBlocksForInput(currentHeight, blockCount.toInt(), null)

        // calcs
        val missed = withMissed.addresses
            .filter { it.validator.currentState!! == ACTIVE }
            .map {
                val missedBlockCount = it.missedBlocks.sumOf { set -> set.count }
                val missedBlockPercent = (missedBlockCount / blockCount.toDouble()).toPercentage()
                val uptimeCount = blockCount.toInt() - missedBlockCount
                val uptimePercentage = (uptimeCount / blockCount.toDouble()).toPercentage()
                ValidatorUptimeStats(it.validator, uptimeCount, uptimePercentage, missedBlockCount, missedBlockPercent)
            }

        // all active validators with calcs
        val activeVals = ValidatorStateRecord.findByStatus(getActiveSet(), ACTIVE)
            .filterNot { active ->
                withMissed.addresses.map { it.validator.valConsAddress }.contains(active.consensusAddr)
            }.map { active ->
                ValidatorUptimeStats(
                    ValidatorMoniker(active.consensusAddr, active.operatorAddress, active.moniker, active.currentState),
                    blockCount.toInt(),
                    (blockCount / blockCount.toDouble()).toPercentage(),
                    0,
                    (0 / blockCount.toDouble()).toPercentage()
                )
            }

        // all records
        val records = missed + activeVals

        // avg uptime across active validators
        val avgUptime = records.map { it.uptimeCount }.avg()

        UptimeDataSet(
            currentHeight - blockCount,
            currentHeight.toLong(),
            blockCount,
            slashedCount,
            slashedAtPercent.toString(Charsets.UTF_8).toPercentage(),
            avgUptime,
            (avgUptime / blockCount.toDouble()).toPercentage(),
            records
        )
    }
}
