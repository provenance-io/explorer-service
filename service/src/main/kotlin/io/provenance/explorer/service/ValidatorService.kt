package io.provenance.explorer.service

import cosmos.base.tendermint.v1beta1.Query
import cosmos.slashing.v1beta1.Slashing
import cosmos.staking.v1beta1.Staking
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.StakingValidatorCacheRecord
import io.provenance.explorer.domain.entities.ValidatorGasFeeCacheRecord
import io.provenance.explorer.domain.entities.ValidatorsCacheRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.extensions.HASH
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.getStatusString
import io.provenance.explorer.domain.extensions.isActive
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.pageOfResults
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toDecCoin
import io.provenance.explorer.domain.extensions.toHash
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.translateAddress
import io.provenance.explorer.domain.extensions.translateByteArray
import io.provenance.explorer.domain.extensions.uptime
import io.provenance.explorer.domain.models.explorer.BondedTokens
import io.provenance.explorer.domain.models.explorer.CoinStr
import io.provenance.explorer.domain.models.explorer.CommissionRate
import io.provenance.explorer.domain.models.explorer.CountTotal
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.ValidatorCommission
import io.provenance.explorer.domain.models.explorer.ValidatorDelegation
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
    }!!

    fun saveValidatorsAtHeight(blockHeight: Int) =
        grpcClient.getValidatorsAtHeight(blockHeight).let { ValidatorsCacheRecord.insertIgnore(blockHeight, it) }

    fun updateValidatorsAtHeight() {
        logger.info("updating validator cache")
        val list = transaction { ValidatorsCacheRecord.getMissingBlocks() }
        list.forEach { height -> saveValidatorsAtHeight(height) }
    }

    // Gets a single staking validator from cache
    fun getStakingValidator(operatorAddress: String) =
        transaction { StakingValidatorCacheRecord.findByOperator(operatorAddress)?.stakingValidator }
            ?: saveValidator(operatorAddress).second

    fun saveValidator(address: String) = transaction {
        grpcClient.getStakingValidator(address)
            .let {
                StakingValidatorCacheRecord.insertIgnore(
                    address,
                    it.operatorAddress.translateAddress(props).accountAddr,
                    it.consensusPubkey.toSingleSigKeyValue()!!,
                    it.consensusPubkey.toAddress(props.provValConsPrefix())!!,
                    it) }
    }

    // Returns a validator detail object for the validator
    fun getValidator(address: String) =
        getValidatorOperatorAddress(address)!!.let { addr ->
            val currentHeight = blockService.getLatestBlockHeightIndex().toBigInteger()
            val signingInfo = getSigningInfos().firstOrNull { it.address == addr.consensusAddress }
            val validatorSet = grpcClient.getLatestValidators()
            val latestValidator = validatorSet.firstOrNull { it.address == addr.consensusAddress }
            val votingPowerTotal = validatorSet.sumOf { it.votingPower.toBigInteger() }
            val stakingValidator = validateStatus(addr.stakingValidator, latestValidator, addr.id.value)
            ValidatorDetails(
                if (latestValidator != null) CountTotal(latestValidator.votingPower.toBigInteger(), votingPowerTotal)
                    else null,
                stakingValidator.description.moniker,
                addr.operatorAddress,
                addr.accountAddress,
                grpcClient.getDelegatorWithdrawalAddress(addr.accountAddress),
                stakingValidator.consensusPubkey.toAddress(props.provValConsPrefix()) ?: "",
                CountTotal(
                    signingInfo!!.missedBlocksCounter.toBigInteger(),
                    currentHeight - signingInfo.startHeight.toBigInteger()),
                signingInfo.startHeight,
                signingInfo.uptime(currentHeight),
                null,  // TODO: Update when we can get images going
                stakingValidator.description.details,
                stakingValidator.description.website,
                stakingValidator.description.identity,
                BlockProposerRecord.findCurrentFeeForAddress(address)?.minGasFee,
                stakingValidator.getStatusString(),
                if (!stakingValidator.isActive()) stakingValidator.unbondingHeight else null,
                if (stakingValidator.jailed) signingInfo.jailedUntil.toDateTime() else null
            )
        }

    fun validateStatus(v: Staking.Validator, valSet: Query.Validator?, valId: Int) =
        if ((valSet != null && !v.isActive()) || (valSet == null && v.isActive())) {
            updateStakingValidators(setOf(valId))
            getStakingValidator(v.operatorAddress)
        } else v

    // Finds a validator address record from whatever address is passed in
    fun getValidatorOperatorAddress(address: String) = when {
        address.startsWith(props.provValOperPrefix()) -> findAddressByOperator(address)
        address.startsWith(props.provValConsPrefix()) -> findAddressByConsensus(address)
        address.startsWith(props.provAccPrefix()) -> findAddressByAccount(address)
        else -> null
    }

    fun getStakingValidators(status: String) = transaction {
        StakingValidatorCacheRecord.findByStatus(status).toList()
    }

    fun getSigningInfos() = grpcClient.getSigningInfos()

    fun findAddressByAccount(address: String) =
        StakingValidatorCacheRecord.findByAccount(address)
        ?: discoverAddresses().let { StakingValidatorCacheRecord.findByAccount(address) }

    fun findAddressByConsensus(address: String) =
        StakingValidatorCacheRecord.findByConsensusAddress(address)
        ?: discoverAddresses().let { StakingValidatorCacheRecord.findByConsensusAddress(address) }

    fun findAddressByOperator(address: String) =
        StakingValidatorCacheRecord.findByOperator(address)
        ?: discoverAddresses().let { StakingValidatorCacheRecord.findByOperator(address) }

    private fun discoverAddresses() = let {
        val stakingVals = transaction { StakingValidatorCacheRecord.all().map { it.operatorAddress } }
        grpcClient.getStakingValidators()
            .forEach { validator ->
                if (!stakingVals.contains(validator.operatorAddress))
                    StakingValidatorCacheRecord.insertIgnore(
                        validator.operatorAddress,
                        validator.operatorAddress.translateAddress(props).accountAddr,
                        validator.consensusPubkey.toSingleSigKeyValue()!!,
                        validator.consensusPubkey.toAddress(props.provValConsPrefix())!!,
                        validator)
            }
    }

    // Updates the staking validator cache
    fun updateStakingValidators(vals: Set<Int>) = transaction {
        logger.info("Updating validators")
        vals.forEach { v ->
            val record = StakingValidatorCacheRecord.findById(v)!!
            val data = grpcClient.getStakingValidator(record.operatorAddress)
            if (data != record.stakingValidator)
                record.apply {
                    this.moniker = data.description.moniker
                    this.jailed = data.jailed
                    this.status = data.status.name
                    this.stakingValidator = data
                }
        }
    }

    // In point to get most recent validators
    fun getRecentValidators(count: Int, page: Int, status: String) =
        aggregateValidators(grpcClient.getLatestValidators(), count, page, status)

    // In point to get validators at height
    fun getValidatorsAtHeight(height: Int, count: Int, page: Int) =
        aggregateValidators(getValidatorsByHeight(height).validatorsList, count, page, "all", true)

    private fun aggregateValidators(
        validatorSet: List<Query.Validator>,
        count: Int,
        page: Int,
        status: String,
        isAtHeight: Boolean = false
    ) =
        let {
            if (!isAtHeight)
                getStakingValidators(status).forEach { v ->
                    validateStatus(
                        v.stakingValidator,
                        validatorSet.firstOrNull { it.address == v.consensusAddress },
                        v.id.value)
                }
            val stakingValidators = getStakingValidators(status)
            hydrateValidators(validatorSet, stakingValidators, isAtHeight)
                .sortedByDescending { it.bondedTokens.count }
                .pageOfResults(page, count)
                .let { PagedResults(it.size.toLong().pageCountOfResults(count), it) }
        }

    private fun hydrateValidators(
        validators: List<Query.Validator>,
        stakingVals: List<StakingValidatorCacheRecord>,
        isAtHeight: Boolean = false
    ) = let {
        val signingInfos = getSigningInfos()
        val height = signingInfos.first().indexOffset
        val totalVotingPower = validators.sumOf { it.votingPower.toBigInteger() }
        stakingVals
            .filter { if (isAtHeight) validators.map { v -> v.address }.contains(it.consensusAddress)  else true }
            .map { stakingVal ->
                val validatorObj = stakingVal.stakingValidator
                val validator = validators.firstOrNull { it.address == stakingVal.consensusAddress }
                val signingInfo = signingInfos.find { it.address == stakingVal.consensusAddress }
                hydrateValidator(validator, validatorObj, signingInfo!!, height.toBigInteger(), totalVotingPower)
            }
    }

    private fun hydrateValidator(
        validator: Query.Validator?,
        stakingValidator: Staking.Validator,
        signingInfo: Slashing.ValidatorSigningInfo,
        height: BigInteger,
        totalVotingPower: BigInteger
    ) = let {
        val selfBondedAmount = grpcClient.getValidatorSelfDelegations(
            stakingValidator.operatorAddress,
            stakingValidator.operatorAddress.translateAddress(props).accountAddr
        ).delegationResponse.balance
        val delegatorCount =
            grpcClient.getStakingValidatorDelegations(stakingValidator.operatorAddress, 0, 10).pagination.total
        ValidatorSummary(
            moniker = stakingValidator.description.moniker,
            addressId = stakingValidator.operatorAddress,
            consensusAddress = signingInfo.address,
            proposerPriority = validator?.proposerPriority?.toInt(),
            votingPower = (if (validator != null) CountTotal(validator.votingPower.toBigInteger(), totalVotingPower) else null),
            uptime = if (stakingValidator.isActive()) signingInfo.uptime(height) else null,
            commission = stakingValidator.commission.commissionRates.rate.toDecCoin(),
            bondedTokens = stakingValidator.tokens.toHash(NHASH)
                .let { BondedTokens(it.first, null, it.second) },
            selfBonded = selfBondedAmount.amount.toHash(selfBondedAmount.denom)
                .let { BondedTokens(it.first, null, it.second) },
            delegators = delegatorCount,
            bondHeight = if (stakingValidator.isActive()) signingInfo.startHeight else null,
            status = stakingValidator.getStatusString(),
            currentGasFee = BlockProposerRecord.findCurrentFeeForAddress(stakingValidator.operatorAddress)?.minGasFee,
            unbondingHeight = if (!stakingValidator.isActive()) stakingValidator.unbondingHeight else null
        )
    }

    fun getBondedDelegations(address: String, page: Int, limit: Int) =
        grpcClient.getStakingValidatorDelegations(address, page.toOffset(limit), limit).let { res ->
            val list = res.delegationResponsesList.map {
                ValidatorDelegation(
                    it.delegation.delegatorAddress,
                    it.balance.amount.toHash(it.balance.denom).let { coin -> CoinStr(coin.first, coin.second, it.balance.denom) },
                    it.delegation.shares.toDecCoin(),
                    null,
                    null)
            }
            PagedResults(res.pagination.total.pageCountOfResults(limit), list)
        }

    fun getUnbondingDelegations(address: String) =
        grpcClient.getStakingValidatorUnbondingDels(address, 0, 100).let { res ->
            res.unbondingResponsesList.flatMap { list ->
                list.entriesList.map {
                    ValidatorDelegation(
                        list.delegatorAddress,
                        it.balance.toHash(NHASH).let { coin -> CoinStr(coin.first, coin.second, NHASH) },
                        null,
                        it.creationHeight.toInt(),
                        it.completionTime.toDateTime()
                    )
                }
            }
        }

    fun getCommissionInfo(address: String): ValidatorCommission {
        val validator = grpcClient.getStakingValidator(address)
        val selfBondedAmount = grpcClient.getValidatorSelfDelegations(
            validator.operatorAddress,
            validator.operatorAddress.translateAddress(props).accountAddr
        ).delegationResponse.balance
        val delegatorCount =
            grpcClient.getStakingValidatorDelegations(validator.operatorAddress, 0, 10).pagination.total
        val rewards = grpcClient.getValidatorCommission(address).commissionList.firstOrNull()
        return ValidatorCommission(
            validator.tokens.toHash(NHASH).let { BondedTokens(it.first, null, it.second) },
            selfBondedAmount.amount.toHash(selfBondedAmount.denom)
                .let { BondedTokens(it.first, null, it.second) },
            validator.tokens.toBigInteger().minus(selfBondedAmount.amount.toBigInteger()).toHash(NHASH)
                .let { BondedTokens(it.first, null, it.second) },
            delegatorCount,
            validator.delegatorShares.toDecCoin(),
            rewards?.amount?.toDecCoin()?.toHash(rewards.denom)?.let { CoinStr(it.first, it.second, rewards.denom) }
                ?: CoinStr("0", HASH, NHASH),
            CommissionRate(
                validator.commission.commissionRates.rate.toDecCoin(),
                validator.commission.commissionRates.maxRate.toDecCoin(),
                validator.commission.commissionRates.maxChangeRate.toDecCoin())
        )
    }

    fun getGasFeeStatistics(address: String, fromDate: DateTime?, toDate: DateTime?, count: Int) =
        ValidatorGasFeeCacheRecord.findByAddress(address, fromDate, toDate, count).reversed()

    fun getProposerConsensusAddr(blockMeta: Query.GetBlockByHeightResponse) =
        blockMeta.block.header.proposerAddress.translateByteArray(props).consensusAccountAddr

    fun saveProposerRecord(blockMeta: Query.GetBlockByHeightResponse, timestamp: DateTime, blockHeight: Int) =
        transaction {
            val consAddr = getProposerConsensusAddr(blockMeta)
            val proposer = findAddressByConsensus(consAddr)!!.operatorAddress
            BlockProposerRecord.save(blockHeight, null, timestamp, proposer)
        }
}
