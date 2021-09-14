package io.provenance.explorer.service

import cosmos.base.tendermint.v1beta1.Query
import cosmos.slashing.v1beta1.Slashing
import cosmos.staking.v1beta1.Staking
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.MissedBlocksRecord
import io.provenance.explorer.domain.entities.StakingValidatorCacheRecord
import io.provenance.explorer.domain.entities.ValidatorGasFeeCacheRecord
import io.provenance.explorer.domain.entities.ValidatorStateRecord
import io.provenance.explorer.domain.entities.ValidatorsCacheRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.average
import io.provenance.explorer.domain.extensions.getStatusString
import io.provenance.explorer.domain.extensions.isActive
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toDecCoin
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.translateAddress
import io.provenance.explorer.domain.extensions.translateByteArray
import io.provenance.explorer.domain.extensions.validatorUptime
import io.provenance.explorer.domain.models.explorer.BlockLatencyData
import io.provenance.explorer.domain.models.explorer.CoinStr
import io.provenance.explorer.domain.models.explorer.CommissionRate
import io.provenance.explorer.domain.models.explorer.CountStrTotal
import io.provenance.explorer.domain.models.explorer.CountTotal
import io.provenance.explorer.domain.models.explorer.CurrentValidatorState
import io.provenance.explorer.domain.models.explorer.Delegation
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.ValidatorCommission
import io.provenance.explorer.domain.models.explorer.ValidatorDetails
import io.provenance.explorer.domain.models.explorer.ValidatorSummary
import io.provenance.explorer.grpc.extensions.toAddress
import io.provenance.explorer.grpc.extensions.toSingleSigKeyValue
import io.provenance.explorer.grpc.v1.ValidatorGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import java.math.BigInteger

@Service
class ValidatorService(
    private val props: ExplorerProperties,
    private val blockService: BlockService,
    private val grpcClient: ValidatorGrpcClient
) {

    protected val logger = logger(ValidatorService::class)

    // Assumes that the returned validators are active at that height
    fun getValidatorsByHeight(blockHeight: Int) = transaction {
        ValidatorsCacheRecord.findById(blockHeight)?.also {
            ValidatorsCacheRecord.updateHitCount(blockHeight)
        }?.validators
    } ?: throw ResourceNotFoundException("Invalid height: '$blockHeight'")

    fun saveValidatorsAtHeight(blockHeight: Int) =
        grpcClient.getValidatorsAtHeight(blockHeight).let { ValidatorsCacheRecord.insertIgnore(blockHeight, it) }

    fun updateValidatorsAtHeight() {
        logger.info("updating validator cache")
        val list = transaction { ValidatorsCacheRecord.getMissingBlocks() }
        list.forEach { height -> saveValidatorsAtHeight(height) }
    }

    // Gets a single staking validator from cache
    fun getStakingValidator(operatorAddress: String) =
        transaction { ValidatorStateRecord.findByOperator(operatorAddress)?.json }
            ?: saveValidator(operatorAddress).second

    fun saveValidator(address: String) = transaction {
        grpcClient.getStakingValidator(address)
            .let {
                StakingValidatorCacheRecord.insertIgnore(
                    address,
                    it.operatorAddress.translateAddress(props).accountAddr,
                    it.consensusPubkey.toSingleSigKeyValue()!!,
                    it.consensusPubkey.toAddress(props.provValConsPrefix())!!
                ).also { id ->
                    ValidatorStateRecord.insertIgnore(
                        blockService.getLatestBlockHeightIndex(),
                        id.value,
                        it.operatorAddress,
                        it
                    )
                }.let { id -> Pair(id, it) }
            }.also { ValidatorStateRecord.refreshCurrentStateView() }
    }

    fun getMissedBlocks(valConsAddr: String) = MissedBlocksRecord.findLatestForVal(valConsAddr)

    // Returns a validator detail object for the validator
    fun getValidator(address: String) =
        getValidatorOperatorAddress(address)?.let { addr ->
            val currentHeight = blockService.getLatestBlockHeight().toBigInteger()
            val signingInfo = getSigningInfos().firstOrNull { it.address == addr.consensusAddr }
            val validatorSet = grpcClient.getLatestValidators()
            val latestValidator = validatorSet.firstOrNull { it.address == addr.consensusAddr }
            val votingPowerTotal = validatorSet.sumOf { it.votingPower.toBigInteger() }
            validateStatus(addr.json, latestValidator, addr.operatorAddrId).also { if (it) ValidatorStateRecord.refreshCurrentStateView() }
            val stakingValidator = getStakingValidator(addr.operatorAddress)
            ValidatorDetails(
                if (latestValidator != null) CountTotal(latestValidator.votingPower.toBigInteger(), votingPowerTotal)
                else null,
                stakingValidator.description.moniker,
                addr.operatorAddress,
                addr.accountAddr,
                grpcClient.getDelegatorWithdrawalAddress(addr.accountAddr),
                stakingValidator.consensusPubkey.toAddress(props.provValConsPrefix()) ?: "",
                CountTotal(
                    (getMissedBlocks(addr.consensusAddr)?.totalCount ?: 0).toBigInteger(),
                    currentHeight - (signingInfo?.startHeight?.toBigInteger() ?: BigInteger.ZERO)
                ),
                signingInfo?.startHeight ?: currentHeight.toLong(),
                addr.consensusAddr.validatorUptime(
                    (signingInfo?.startHeight?.toBigInteger() ?: currentHeight.minus(BigInteger.ONE)),
                    currentHeight
                ),
                getImgUrl(stakingValidator.description.identity),
                stakingValidator.description.details,
                stakingValidator.description.website,
                stakingValidator.description.identity,
                BlockProposerRecord.findCurrentFeeForAddress(address)?.minGasFee,
                stakingValidator.getStatusString(),
                if (!stakingValidator.isActive()) stakingValidator.unbondingHeight else null,
                if (stakingValidator.jailed) signingInfo?.jailedUntil?.toDateTime() else null
            )
        } ?: throw ResourceNotFoundException("Invalid validator address: '$address'")

    fun validateStatus(v: Staking.Validator, valSet: Query.Validator?, valId: Int): Boolean =
        if ((valSet != null && !v.isActive()) || (valSet == null && v.isActive())) {
            updateStakingValidators(setOf(valId))
        } else false

    // Finds a validator address record from whatever address is passed in
    fun getValidatorOperatorAddress(address: String) = when {
        address.startsWith(props.provValOperPrefix()) -> findAddressByOperator(address)
        address.startsWith(props.provValConsPrefix()) -> findAddressByConsensus(address)
        address.startsWith(props.provAccPrefix()) -> findAddressByAccount(address)
        else -> null
    }

    fun getStakingValidators(status: String, valSet: List<String>? = null, offset: Int? = null, limit: Int? = null) =
        transaction {
            ValidatorStateRecord.findByStatus(status, valSet, offset, limit)
        }

    fun getStakingValidatorsCount(status: String, valSet: List<String>? = null) = transaction {
        ValidatorStateRecord.findByStatusCount(status, valSet)
    }

    fun getSigningInfos() = grpcClient.getSigningInfos()

    fun findAddressByAccount(address: String) =
        ValidatorStateRecord.findByAccount(address)
            ?: discoverAddresses().let { ValidatorStateRecord.findByAccount(address) }

    fun findAddressByConsensus(address: String) =
        ValidatorStateRecord.findByConsensusAddress(address)
            ?: discoverAddresses().let { ValidatorStateRecord.findByConsensusAddress(address) }

    fun findAddressByOperator(address: String) =
        ValidatorStateRecord.findByOperator(address)
            ?: discoverAddresses().let { ValidatorStateRecord.findByOperator(address) }

    private fun discoverAddresses() {
        val stakingVals = transaction { ValidatorStateRecord.findAll().map { it.operatorAddress } }
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
                            it.value,
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
            val record = ValidatorStateRecord.findByValId(v)!!
            val data = grpcClient.getStakingValidator(record.operatorAddress)
            if (record.blockHeight < height && data != record.json)
                ValidatorStateRecord.insertIgnore(
                    height,
                    v,
                    record.operatorAddress,
                    data
                ).also { if (!updated) updated = true }
        }
        return updated
    }

    // In point to get most recent validators
    fun getRecentValidators(count: Int, page: Int, status: String) =
        aggregateValidatorsRecent(grpcClient.getLatestValidators(), count, page, status)

    private fun aggregateValidatorsRecent(
        validatorSet: List<Query.Validator>,
        count: Int,
        page: Int,
        status: String
    ) =
        let {
            getStakingValidators(status).map { v ->
                validateStatus(
                    v.json,
                    validatorSet.firstOrNull { it.address == v.consensusAddr },
                    v.operatorAddrId
                )
            }.also { map -> if (map.contains(true)) ValidatorStateRecord.refreshCurrentStateView() }
            val stakingValidators = getStakingValidators(status, null, page.toOffset(count), count)
            val results = hydrateValidators(validatorSet, stakingValidators)
            val totalCount = getStakingValidatorsCount(status, null)
            PagedResults(totalCount.pageCountOfResults(count), results, totalCount)
        }

    fun hydrateValidators(
        validatorSet: List<Query.Validator>,
        stakingVals: List<CurrentValidatorState>
    ) = let {
        val signingInfos = getSigningInfos()
        val height = blockService.getLatestBlockHeight()
        val totalVotingPower = validatorSet.sumOf { it.votingPower.toBigInteger() }
        stakingVals
            .map { stakingVal ->
                val validator = validatorSet.firstOrNull { it.address == stakingVal.consensusAddr }
                val signingInfo = signingInfos.find { it.address == stakingVal.consensusAddr }
                    ?: Slashing.ValidatorSigningInfo.getDefaultInstance()
                hydrateValidator(validator, stakingVal, signingInfo, height.toBigInteger(), totalVotingPower)
            }
    }

    private fun hydrateValidator(
        validator: Query.Validator?,
        stakingVal: CurrentValidatorState,
        signingInfo: Slashing.ValidatorSigningInfo,
        height: BigInteger,
        totalVotingPower: BigInteger
    ) = let {
        val selfBonded = getValSelfBonded(stakingVal.json)
        val delegatorCount =
            grpcClient.getStakingValidatorDelegations(stakingVal.operatorAddress, 0, 1).pagination.total
        ValidatorSummary(
            moniker = stakingVal.json.description.moniker,
            addressId = stakingVal.operatorAddress,
            consensusAddress = stakingVal.consensusAddr,
            proposerPriority = validator?.proposerPriority?.toInt(),
            votingPower = (if (validator != null) CountTotal(validator.votingPower.toBigInteger(), totalVotingPower) else null),
            uptime = if (stakingVal.json.isActive()) signingInfo.address.validatorUptime(signingInfo.startHeight.toBigInteger(), height) else null,
            commission = stakingVal.json.commission.commissionRates.rate.toDecCoin(),
            bondedTokens = CountStrTotal(stakingVal.json.tokens, null, NHASH),
            selfBonded = CountStrTotal(selfBonded.first, null, selfBonded.second),
            delegators = delegatorCount,
            bondHeight = if (stakingVal.json.isActive()) signingInfo.startHeight else null,
            status = stakingVal.json.getStatusString(),
            currentGasFee = BlockProposerRecord.findCurrentFeeForAddress(stakingVal.operatorAddress)?.minGasFee,
            unbondingHeight = if (!stakingVal.json.isActive()) stakingVal.json.unbondingHeight else null,
            imgUrl = getImgUrl(stakingVal.json.description.identity)
        )
    }

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
            PagedResults(res.pagination.total.pageCountOfResults(limit), list, res.pagination.total)
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
        }

    fun getCommissionInfo(address: String): ValidatorCommission {
        val validator = ValidatorStateRecord.findByOperator(address)?.json
            ?: throw ResourceNotFoundException("Invalid validator address: '$address'")

        val selfBonded = getValSelfBonded(validator)
        val delegatorCount =
            grpcClient.getStakingValidatorDelegations(validator.operatorAddress, 0, 10).pagination.total
        val rewards = grpcClient.getValidatorCommission(address).commissionList.firstOrNull()
        return ValidatorCommission(
            CountStrTotal(validator.tokens, null, NHASH),
            CountStrTotal(selfBonded.first, null, selfBonded.second),
            CountStrTotal(validator.tokens.toBigInteger().minus(selfBonded.first.toBigInteger()).toString(), null, NHASH),
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

    fun getGasFeeStatistics(address: String, fromDate: DateTime?, toDate: DateTime?, count: Int) = transaction {
        ValidatorGasFeeCacheRecord.findByAddress(address, fromDate, toDate, count).reversed()
    }

    fun getProposerConsensusAddr(blockMeta: Query.GetBlockByHeightResponse) =
        blockMeta.block.header.proposerAddress.translateByteArray(props).consensusAccountAddr

    fun saveProposerRecord(blockMeta: Query.GetBlockByHeightResponse, timestamp: DateTime, blockHeight: Int) =
        transaction {
            val consAddr = getProposerConsensusAddr(blockMeta)
            val proposer = findAddressByConsensus(consAddr)!!.operatorAddress
            BlockProposerRecord.save(blockHeight, null, timestamp, proposer)
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

    fun getImgUrl(identityStr: String) = transaction {
        if (identityStr.isNotBlank()) {
            val res = khttp.get(
                url = "https://keybase.io/_/api/1.0/user/lookup.json",
                params = mapOf("key_suffix" to identityStr, "fields" to "pictures")
            )

            if (res.statusCode == 200) {
                try {
                    res.jsonObject.getJSONArray("them").let {
                        if (it.length() > 0) {
                            val them = it.getJSONObject(0)
                            if (them.has("pictures")) {
                                them.getJSONObject("pictures")
                                    ?.getJSONObject("primary")
                                    ?.getString("url")
                            } else null
                        } else null
                    }
                } catch (e: Exception) {
                    null
                }
            } else null.also { logger.error("Error reaching Keybase: ${res.jsonObject}") }
        } else null
    }

    fun getBlockLatencyData(address: String, blockCount: Int) =
        BlockProposerRecord.getRecordsForProposer(address, blockCount).let { res ->
            val average = res.map { it.blockLatency!! }.average()
            val data = res.associate { it.blockHeight to it.blockLatency!! }
            BlockLatencyData(address, data, average)
        }
}
