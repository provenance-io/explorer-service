package io.provenance.explorer.service

import cosmos.base.tendermint.v1beta1.Query
import cosmos.staking.v1beta1.Staking
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.provenance.explorer.KTOR_CLIENT_JAVA
import io.provenance.explorer.config.ExplorerProperties.Companion.PROV_ACC_PREFIX
import io.provenance.explorer.config.ExplorerProperties.Companion.PROV_VAL_CONS_PREFIX
import io.provenance.explorer.config.ExplorerProperties.Companion.PROV_VAL_OPER_PREFIX
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AddressImageRecord
import io.provenance.explorer.domain.entities.MissedBlocksRecord
import io.provenance.explorer.domain.entities.SpotlightCacheRecord
import io.provenance.explorer.domain.entities.StakingValidatorCacheRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateStatsRecord
import io.provenance.explorer.domain.entities.ValidatorStateRecord
import io.provenance.explorer.domain.entities.ValidatorsCacheRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.exceptions.requireNotNullToMessage
import io.provenance.explorer.domain.extensions.average
import io.provenance.explorer.domain.extensions.avg
import io.provenance.explorer.domain.extensions.get24HrBlockHeight
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.sigToAddress
import io.provenance.explorer.domain.extensions.sigToBase64
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toDecimal
import io.provenance.explorer.domain.extensions.toDecimalStringOld
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toPercentage
import io.provenance.explorer.domain.extensions.toPercentageOld
import io.provenance.explorer.domain.extensions.translateAddress
import io.provenance.explorer.domain.extensions.translateByteArray
import io.provenance.explorer.domain.extensions.validatorMissedBlocks
import io.provenance.explorer.domain.extensions.validatorUptime
import io.provenance.explorer.domain.models.explorer.BlockProposer
import io.provenance.explorer.domain.models.explorer.CurrentValidatorState
import io.provenance.explorer.domain.models.explorer.hourlyBlockCount
import io.provenance.explorer.domain.models.explorer.zeroOutValidatorObj
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import io.provenance.explorer.grpc.v1.ValidatorGrpcClient
import io.provenance.explorer.model.CommissionList
import io.provenance.explorer.model.CommissionRate
import io.provenance.explorer.model.Delegation
import io.provenance.explorer.model.MarketRateAvg
import io.provenance.explorer.model.MissedBlockSet
import io.provenance.explorer.model.MissedBlocksTimeframe
import io.provenance.explorer.model.UnpaginatedDelegation
import io.provenance.explorer.model.UptimeDataSet
import io.provenance.explorer.model.ValidatorCommission
import io.provenance.explorer.model.ValidatorCommissionHistory
import io.provenance.explorer.model.ValidatorDetails
import io.provenance.explorer.model.ValidatorMissedBlocks
import io.provenance.explorer.model.ValidatorMoniker
import io.provenance.explorer.model.ValidatorState
import io.provenance.explorer.model.ValidatorState.ACTIVE
import io.provenance.explorer.model.ValidatorSummary
import io.provenance.explorer.model.ValidatorSummaryAbbrev
import io.provenance.explorer.model.ValidatorUptimeStats
import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.CountStrTotal
import io.provenance.explorer.model.base.CountTotal
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.model.base.Timeframe
import io.provenance.explorer.model.base.stringfy
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.json.JSONObject
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.BigInteger

@Service
class ValidatorService(
    private val blockService: BlockService,
    private val grpcClient: ValidatorGrpcClient,
    private val cacheService: CacheService,
    private val attrClient: AttributeGrpcClient,
    private val nameService: NameService
) {

    protected val logger = logger(ValidatorService::class)

    fun getActiveSet() = grpcClient.getStakingParams().params.maxValidators

    fun getSlashingParams() = grpcClient.getSlashingParams().params

    fun isVerified(address: String) =
        runBlocking {
            val atts = async { attrClient.getAllAttributesForAddress(address) }.await().map { it.name }
            val kycAtts = nameService.getVerifiedKycAttributes()
            atts.intersect(kycAtts).isNotEmpty()
        }

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

    fun saveValidator(address: String, height: Int? = null) = runBlocking {
        grpcClient.getStakingValidator(address, height)
            .let {
                StakingValidatorCacheRecord.insertIgnore(
                    address,
                    it.operatorAddress.translateAddress().accountAddr,
                    it.consensusPubkey.sigToBase64(),
                    it.consensusPubkey.sigToAddress(PROV_VAL_CONS_PREFIX)!!
                ).also { record ->
                    ValidatorStateRecord.insertIgnore(
                        blockService.getLatestBlockHeightIndex(),
                        record.id.value,
                        it.operatorAddress,
                        it
                    )
                    getImgUrl(it.description.identity)
                        ?.let { img -> AddressImageRecord.upsert(it.operatorAddress, img) }
                }
            }.also { ValidatorStateRecord.refreshCurrentStateView() }
            .let { ValidatorStateRecord.findByOperator(getActiveSet(), address)!! }
    }

    fun validateValidator(validator: String) =
        requireNotNullToMessage(StakingValidatorCacheRecord.findByOperAddr(validator)) { "Validator $validator does not exist." }

    // Returns a validator detail object for the validator
    fun getValidator(address: String) =
        getValidatorOperatorAddress(address)?.let { addr ->
            val currentHeight = blockService.getLatestBlockHeight().toBigInteger()
            val signingInfo = getSigningInfos().firstOrNull { it.address == addr.consensusAddr }
            val validatorSet = grpcClient.getLatestValidators().validatorsList
            val latestValidator = validatorSet.firstOrNull { it.address == addr.consensusAddr }
            val votingPowerTotal = validatorSet.sumOf { it.votingPower.toBigInteger() }
            val slashingParams = getSlashingParams()
            validateStatus(addr, latestValidator, addr.operatorAddrId)
                .also { if (it) ValidatorStateRecord.refreshCurrentStateView() }
            val stakingValidator = getStakingValidator(addr.operatorAddress)
            ValidatorDetails(
                if (latestValidator != null) {
                    CountTotal(latestValidator.votingPower.toBigInteger(), votingPowerTotal)
                } else {
                    null
                },
                stakingValidator.json.description.moniker,
                addr.operatorAddress,
                addr.accountAddr,
                grpcClient.getDelegatorWithdrawalAddress(addr.accountAddr),
                addr.consensusAddr,
                addr.consensusAddr.validatorMissedBlocks(slashingParams.signedBlocksWindow.toBigInteger(), currentHeight)
                    .let { (mbCount, window) -> CountTotal(mbCount.toBigInteger(), window) },
                signingInfo?.startHeight ?: currentHeight.toLong(),
                addr.consensusAddr.validatorUptime(slashingParams.signedBlocksWindow.toBigInteger(), currentHeight),
                stakingValidator.imageUrl,
                stakingValidator.json.description.details,
                stakingValidator.json.description.website,
                stakingValidator.json.description.identity,
                stakingValidator.currentState.toString().lowercase(),
                if (stakingValidator.currentState != ACTIVE) stakingValidator.json.unbondingHeight else null,
                if (stakingValidator.jailed) signingInfo?.jailedUntil?.toDateTime() else null,
                stakingValidator.removed,
                isVerified(addr.accountAddr)
            )
        } ?: throw ResourceNotFoundException("Invalid validator address: '$address'")

    fun validateStatus(v: CurrentValidatorState, valSet: Query.Validator?, valId: Int): Boolean =
        if ((valSet != null && v.currentState != ACTIVE) || (valSet == null && v.currentState == ACTIVE)) {
            updateStakingValidators(setOf(valId))
        } else {
            false
        }

    // Finds a validator address record from whatever address is passed in
    fun getValidatorOperatorAddress(address: String) = when {
        address.startsWith(PROV_VAL_OPER_PREFIX) -> findAddressByOperator(address)
        address.startsWith(PROV_VAL_CONS_PREFIX) -> findAddressByConsensus(address)
        address.startsWith(PROV_ACC_PREFIX) -> findAddressByAccount(address)
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
        val blockHeight = blockService.getLatestBlockHeightIndexOrFromChain()
        grpcClient.getStakingValidators()
            .map { validator ->
                if (!stakingVals.contains(validator.operatorAddress)) {
                    StakingValidatorCacheRecord.insertIgnore(
                        validator.operatorAddress,
                        validator.operatorAddress.translateAddress().accountAddr,
                        validator.consensusPubkey.sigToBase64(),
                        validator.consensusPubkey.sigToAddress(PROV_VAL_CONS_PREFIX)!!
                    ).also {
                        ValidatorStateRecord.insertIgnore(
                            blockHeight,
                            it.id.value,
                            validator.operatorAddress,
                            validator
                        )
                        getImgUrl(validator.description.identity)
                            ?.let { img -> AddressImageRecord.upsert(validator.operatorAddress, img) }
                    }.let { true }
                } else {
                    false
                }
            }.also { map -> if (map.contains(true)) ValidatorStateRecord.refreshCurrentStateView() }
    }

    // Updates the staking validator cache
    fun updateStakingValidators(vals: Set<Int>, blockHeight: Int? = null): Boolean {
        logger.info("Updating validators")
        val height = blockHeight ?: blockService.getLatestBlockHeightIndex()
        var updated = false
        vals.forEach { v ->
            val record = ValidatorStateRecord.findByValId(getActiveSet(), v)!!
            val data = grpcClient.getStakingValidatorOrNull(record.operatorAddress, height) ?: record.json.zeroOutValidatorObj()
            if (record.blockHeight < height && data != record.json) {
                ValidatorStateRecord.insertIgnore(height, v, record.operatorAddress, data)
                    .also {
                        getImgUrl(data.description.identity)
                            ?.let { img -> AddressImageRecord.upsert(record.operatorAddress, img) }
                    }.also { if (!updated) updated = true }
            }
        }
        return updated
    }

    // Abbreviated data used for specific cases
    fun getAllValidatorsAbbrev() = transaction {
        val recs = getStakingValidators(ValidatorState.ALL, null, null, null).map { currVal ->
            ValidatorSummaryAbbrev(
                currVal.json.description.moniker,
                currVal.operatorAddress,
                currVal.json.commission.commissionRates.rate.toDecimalStringOld(),
                currVal.imageUrl
            )
        }
        PagedResults(recs.size.toLong().pageCountOfResults(recs.size), recs, recs.size.toLong())
    }

    // In point to get most recent validators
    fun getRecentValidators(count: Int, page: Int, status: ValidatorState) = aggregateValidatorsRecent(count, page, status)

    private fun aggregateValidatorsRecent(
        count: Int,
        page: Int,
        status: ValidatorState
    ): PagedResults<ValidatorSummary> {
        val (height, validatorSet) = grpcClient.getLatestValidators().let { it.blockHeight to it.validatorsList }
        val hr24ChangeSet = grpcClient.getValidatorsAtHeight(
            height.get24HrBlockHeight(cacheService.getAvgBlockTime()).toInt()
        ).validatorsList
        getStakingValidators(status).map { v ->
            validateStatus(v, validatorSet.firstOrNull { it.address == v.consensusAddr }, v.operatorAddrId)
        }.also { map -> if (map.contains(true)) ValidatorStateRecord.refreshCurrentStateView() }
        val stakingValidators = getStakingValidators(status, null, page.toOffset(count), count)
        val results = hydrateValidators(validatorSet, hr24ChangeSet, stakingValidators, height)
        val totalCount = getStakingValidatorsCount(status, null)
        return PagedResults(totalCount.pageCountOfResults(count), results, totalCount)
    }

    fun hydrateValidators(
        validatorSet: List<Query.Validator>,
        hr24ChangeSet: List<Query.Validator>,
        stakingVals: List<CurrentValidatorState>,
        height: Long
    ) = let {
        val totalVotingPower = validatorSet.sumOf { it.votingPower.toBigInteger() }
        val slashWindow = grpcClient.getSlashingParams().params.signedBlocksWindow.toBigInteger()
        stakingVals
            .map { stakingVal ->
                val validator = validatorSet.firstOrNull { it.address == stakingVal.consensusAddr }
                val hr24Validator = hr24ChangeSet.firstOrNull { it.address == stakingVal.consensusAddr }
                hydrateValidator(validator, hr24Validator, stakingVal, totalVotingPower, height, slashWindow)
            }
    }

    private fun hydrateValidator(
        validator: Query.Validator?,
        hr24Validator: Query.Validator?,
        stakingVal: CurrentValidatorState,
        totalVotingPower: BigInteger,
        height: Long,
        slashingWindow: BigInteger
    ) = let {
        val delegatorCount =
            grpcClient.getStakingValidatorDelegations(stakingVal.operatorAddress, 0, 1).pagination.total
        ValidatorSummary(
            moniker = stakingVal.json.description.moniker,
            addressId = stakingVal.operatorAddress,
            consensusAddress = stakingVal.consensusAddr,
            proposerPriority = validator?.proposerPriority,
            votingPower = if (validator != null) {
                CountTotal(
                    validator.votingPower.toBigInteger(),
                    totalVotingPower
                )
            } else {
                null
            },
            commission = stakingVal.json.commission.commissionRates.rate.toDecimalStringOld(),
            bondedTokens = CountStrTotal(stakingVal.json.tokens, null, UTILITY_TOKEN),
            delegators = delegatorCount,
            status = stakingVal.currentState.toString().lowercase(),
            unbondingHeight = if (stakingVal.currentState != ACTIVE) stakingVal.json.unbondingHeight else null,
            imgUrl = stakingVal.imageUrl,
            hr24Change = get24HrBondedChange(validator, hr24Validator),
            uptime = stakingVal.consensusAddr.validatorUptime(slashingWindow, height.toBigInteger())
        )
    }

    private fun get24HrBondedChange(latestVal: Query.Validator?, hr24Val: Query.Validator?) =
        ((latestVal?.votingPower ?: 0L) - (hr24Val?.votingPower ?: 0L)).let { if (it == 0L) null else it.toString() }

    private fun getValSelfBonded(stakingVal: Staking.Validator) = transaction {
        try {
            grpcClient.getValidatorSelfDelegations(
                stakingVal.operatorAddress,
                stakingVal.operatorAddress.translateAddress().accountAddr
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
            .toCoinStr(delegations.firstOrNull()?.balance?.denom ?: UTILITY_TOKEN)
    }

    fun getBondedDelegations(address: String, page: Int, limit: Int) =
        getValidatorOperatorAddress(address)?.let { addr ->
            grpcClient.getStakingValidatorDelegations(addr.operatorAddress, page.toOffset(limit), limit).let { res ->
                val list = res.delegationResponsesList.map {
                    Delegation(
                        it.delegation.delegatorAddress,
                        it.delegation.validatorAddress,
                        null,
                        CoinStr(it.balance.amount, it.balance.denom),
                        null,
                        it.delegation.shares.toDecimalStringOld(),
                        null,
                        null
                    )
                }
                val rollup = mapOf("bondedTotal" to getDelegationTotal(addr.operatorAddress))
                PagedResults(res.pagination.total.pageCountOfResults(limit), list, res.pagination.total, rollup)
            }
        } ?: throw ResourceNotFoundException("Invalid validator address: '$address'")

    fun getUnbondingDelegations(address: String) =
        getValidatorOperatorAddress(address)?.let { addr ->
            grpcClient.getStakingValidatorUnbondingDels(addr.operatorAddress, 0, 100).let { res ->
                res.unbondingResponsesList.flatMap { list ->
                    list.entriesList.map {
                        Delegation(
                            list.delegatorAddress,
                            list.validatorAddress,
                            null,
                            CoinStr(it.balance, UTILITY_TOKEN),
                            CoinStr(it.initialBalance, UTILITY_TOKEN),
                            null,
                            it.creationHeight.toInt(),
                            it.completionTime.toDateTime()
                        )
                    }
                }
            }.let { recs ->
                val total = recs.sumOf { it.amount.amount.toBigDecimal() }.toCoinStr(UTILITY_TOKEN)
                UnpaginatedDelegation(recs, mapOf(Pair("unbondingTotal", total)))
            }
        } ?: throw ResourceNotFoundException("Invalid validator address: '$address'")

    fun getValidatorCommission(address: String) =
        getValidatorOperatorAddress(address)?.let { addr ->
            grpcClient.getValidatorCommission(addr.operatorAddress).commissionList
        } ?: throw ResourceNotFoundException("Invalid validator address: '$address'")

    fun getCommissionInfo(address: String): ValidatorCommission {
        val validator = getValidatorOperatorAddress(address)?.json
            ?: throw ResourceNotFoundException("Invalid validator address: '$address'")

        val selfBonded = getValSelfBonded(validator)
        val delegatorCount =
            grpcClient.getStakingValidatorDelegations(validator.operatorAddress, 0, 10).pagination.total
        val rewards = getValidatorCommission(address).firstOrNull()
        return ValidatorCommission(
            CountStrTotal(validator.tokens, null, UTILITY_TOKEN),
            CountStrTotal(selfBonded.first, null, selfBonded.second),
            CountStrTotal(
                validator.tokens.toBigInteger().minus(selfBonded.first.toBigInteger()).toString(),
                null,
                UTILITY_TOKEN
            ),
            delegatorCount,
            validator.delegatorShares.toDecimalStringOld(),
            rewards?.amount?.toDecimalStringOld()?.let { CoinStr(it, rewards.denom) } ?: CoinStr("0", UTILITY_TOKEN),
            CommissionRate(
                validator.commission.commissionRates.rate.toDecimalStringOld(),
                validator.commission.commissionRates.maxRate.toDecimalStringOld(),
                validator.commission.commissionRates.maxChangeRate.toDecimalStringOld()
            )
        )
    }

    fun getCommissionRateHistory(address: String) =
        getValidatorOperatorAddress(address)?.let { addr ->
            ValidatorStateRecord.getCommissionHistory(addr.operatorAddress)
                .map { CommissionList(it.commissionRate.stringfy(), it.blockHeight) }
                .let { ValidatorCommissionHistory(addr.operatorAddress, it) }
        } ?: throw ResourceNotFoundException("Invalid validator address: '$address'")

    fun getValidatorMarketRateAvg(address: String, txCount: Int) =
        getValidatorOperatorAddress(address)?.let { addr ->
            ValidatorMarketRateRecord.getValidatorRateForBlockCount(addr.operatorAddress, txCount)
                .map { it.marketRate }
                .let { list -> MarketRateAvg(list.size, list.minOrNull()!!, list.maxOrNull()!!, list.average()) }
        } ?: throw ResourceNotFoundException("Invalid validator address: '$address'")

    fun getValidatorMarketRateStats(address: String, fromDate: DateTime?, toDate: DateTime?, count: Int) =
        getValidatorOperatorAddress(address)?.let { addr ->
            ValidatorMarketRateStatsRecord.findByAddress(addr.operatorAddress, fromDate, toDate, count)
        } ?: throw ResourceNotFoundException("Invalid validator address: '$address'")

    fun getProposerConsensusAddr(blockMeta: Query.GetBlockByHeightResponse) =
        blockMeta.block.header.proposerAddress.translateByteArray().consensusAccountAddr

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
                .map { it.validatorAddress.translateByteArray().consensusAccountAddr }
            val currentVals = ValidatorsCacheRecord.findById(lastBlock.height.toInt())?.validators
                ?: grpcClient.getValidatorsAtHeight(lastBlock.height.toInt())

            currentVals.validatorsList.forEach { vali ->
                if (!signatures.contains(vali.address)) {
                    MissedBlocksRecord.insert(lastBlock.height.toInt(), vali.address)
                }
            }
        }
    }

    fun getImgUrl(identityStr: String) = runBlocking {
        if (identityStr.isNotBlank()) {
            val res = try {
                KTOR_CLIENT_JAVA.get("https://keybase.io/_/api/1.0/user/lookup.json") {
                    parameter("key_suffix", identityStr)
                    parameter("fields", "pictures")
                }
            } catch (e: Exception) {
                return@runBlocking null.also { logger.error("Error reaching Keybase: ${e.message}") }
            }

            if (res.status.value in 200..299) {
                try {
                    JSONObject(res.body<String>()).getJSONArray("them").let {
                        if (it.length() > 0) {
                            val them = it.getJSONObject(0)
                            if (them.has("pictures")) {
                                them.getJSONObject("pictures")?.getJSONObject("primary")?.getString("url")
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            } else {
                null.also { logger.error("Error reaching Keybase: ${res.status}") }
            }
        } else {
            null
        }
    }

    private fun getLatestHeight() = SpotlightCacheRecord.getSpotlight()?.latestBlock?.height ?: blockService.getMaxBlockCacheHeight()

    fun getDistinctValidatorsWithMissedBlocksInTimeframe(timeframe: Timeframe) = transaction {
        val currentHeight = getLatestHeight()
        val frame = when (timeframe) {
            Timeframe.WEEK -> hourlyBlockCount * 24 * 7
            Timeframe.DAY -> hourlyBlockCount * 24
            Timeframe.HOUR -> hourlyBlockCount
            Timeframe.FOREVER -> currentHeight - 1
            Timeframe.QUARTER -> hourlyBlockCount * 24 * (365 / 4)
            Timeframe.MONTH -> hourlyBlockCount * 24 * (365 / 12)
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
        if (timeframe == Timeframe.FOREVER && validatorAddr == null) {
            throw IllegalArgumentException("If timeframe is FOREVER, you must have a validator address specified.")
        }

        val currentHeight = getLatestHeight()
        val frame = when (timeframe) {
            Timeframe.WEEK -> hourlyBlockCount * 24 * 7
            Timeframe.DAY -> hourlyBlockCount * 24
            Timeframe.HOUR -> hourlyBlockCount
            Timeframe.FOREVER -> currentHeight - 1
            Timeframe.QUARTER -> hourlyBlockCount * 24 * (365 / 4)
            Timeframe.MONTH -> hourlyBlockCount * 24 * (365 / 12)
        }

        val valConsAddr =
            if (validatorAddr != null) {
                getValidatorOperatorAddress(validatorAddr)?.consensusAddr
                    ?: throw ResourceNotFoundException("Invalid validator address: '$validatorAddr'")
            } else {
                null
            }

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
        val (window, slashedAtPercent) = getSlashingParams().let { it.signedBlocksWindow to it.minSignedPerWindow }
        val slashedCount =
            slashedAtPercent.toString(Charsets.UTF_8).toDecimal().multiply(BigDecimal(window)).toLong()
        // Height to count from
        val currentHeight = getLatestHeight()

        // validators with missed blocks
        val withMissed = getMissedBlocksForInput(currentHeight, window.toInt(), null)

        // calcs
        val missed = withMissed.addresses
            .filter { it.validator.currentState!! == ACTIVE }
            .map {
                val missedBlockCount = it.missedBlocks.sumOf { set -> set.count }
                val missedBlockPercent = (missedBlockCount / window.toDouble()).toPercentage()
                val uptimeCount = window.toInt() - missedBlockCount
                val uptimePercentage = (uptimeCount / window.toDouble()).toPercentage()
                ValidatorUptimeStats(it.validator, uptimeCount, uptimePercentage, missedBlockCount, missedBlockPercent)
            }

        // all active validators with calcs
        val activeVals = ValidatorStateRecord.findByStatus(getActiveSet(), ACTIVE)
            .filterNot { active ->
                withMissed.addresses.map { it.validator.valConsAddress }.contains(active.consensusAddr)
            }.map { active ->
                ValidatorUptimeStats(
                    ValidatorMoniker(active.consensusAddr, active.operatorAddress, active.moniker, active.currentState),
                    window.toInt(),
                    (window / window.toDouble()).toPercentage(),
                    0,
                    (0 / window.toDouble()).toPercentage()
                )
            }

        // all records
        val records = missed + activeVals

        // avg uptime across active validators
        val avgUptime = records.map { it.uptimeCount }.avg()

        UptimeDataSet(
            currentHeight - window,
            currentHeight.toLong(),
            window,
            slashedCount,
            slashedAtPercent.toString(Charsets.UTF_8).toPercentageOld(),
            avgUptime,
            (avgUptime / window.toDouble()).toPercentage(),
            records.sortedByDescending { it.missedCount }
        )
    }
}
