package io.provenance.explorer.service

import cosmos.base.tendermint.v1beta1.Query
import cosmos.slashing.v1beta1.Slashing
import cosmos.staking.v1beta1.Staking
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.StakingValidatorCacheRecord
import io.provenance.explorer.domain.entities.ValidatorAddressesRecord
import io.provenance.explorer.domain.entities.ValidatorsCacheRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.extensions.getStatusString
import io.provenance.explorer.domain.extensions.isPastDue
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.pageOfResults
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.extensions.toScaledDecimal
import io.provenance.explorer.domain.extensions.translateAddress
import io.provenance.explorer.domain.extensions.uptime
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.ValidatorCommission
import io.provenance.explorer.domain.models.explorer.ValidatorDelegation
import io.provenance.explorer.domain.models.explorer.ValidatorDetails
import io.provenance.explorer.domain.models.explorer.ValidatorSummary
import io.provenance.explorer.grpc.toAddress
import io.provenance.explorer.grpc.toSingleSigKeyValue
import io.provenance.explorer.grpc.v1.ValidatorGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

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
    } ?: grpcClient.getValidatorsAtHeight(blockHeight)
        .let { ValidatorsCacheRecord.insertIgnore(blockHeight, it) }

    // Gets a single staking validator from cache
    fun getStakingValidator(operatorAddress: String) =
        transaction { StakingValidatorCacheRecord.findById(operatorAddress)?.stakingValidator }
            ?: grpcClient.getStakingValidator(operatorAddress)
                .let { StakingValidatorCacheRecord.insertIgnore(operatorAddress, it) }

    // Returns a validator detail object for the validator
    fun getValidator(address: String) =
        getValidatorOperatorAddress(address)!!.let { addr ->
            val currentHeight = blockService.getLatestBlockHeightIndex()
            //TODO make async and add caching
            val stakingValidator = getStakingValidator(addr.operatorAddress)
            val signingInfo = getSigningInfos().firstOrNull { it.address == addr.consensusAddress }
            val validatorSet = grpcClient.getLatestValidators()
            val latestValidator = validatorSet.firstOrNull { it.address == addr.consensusAddress }!!
            val votingPowerPercent = BigDecimal(validatorSet.sumBy { it.votingPower.toInt() })
                .divide(latestValidator.votingPower.toBigDecimal(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
            ValidatorDetails(
                latestValidator.votingPower.toInt(),
                votingPowerPercent,
                stakingValidator.description.moniker,
                addr.operatorAddress,
                addr.accountAddress,
                grpcClient.getDelegatorWithdrawalAddress(addr.accountAddress),
                stakingValidator.consensusPubkey.toAddress(props.provValConsPrefix()) ?: "",
                signingInfo!!.missedBlocksCounter.toInt(),
                currentHeight - signingInfo.startHeight.toInt(),
                0,
                signingInfo.uptime(currentHeight),
                null,  // TODO: Update when we can get images going
                stakingValidator.description.details,
                stakingValidator.description.website,
                stakingValidator.description.identity)
        }

    // Finds a validator address record from whatever address is passed in
    fun getValidatorOperatorAddress(address: String) = when {
        address.startsWith(props.provValOperPrefix()) -> findAddressByOperator(address)
        address.startsWith(props.provValConsPrefix()) -> findAddressByConsensus(address)
        address.startsWith(props.provAccPrefix()) -> findAddressByAccount(address)
        else -> null
    }

    fun getStakingValidators(status: String) = transaction {
        StakingValidatorCacheRecord.findByStatus(status).map { it.stakingValidator }
    }

    fun getSigningInfos() = grpcClient.getSigningInfos()

    fun findAddressByAccount(address: String) =
        ValidatorAddressesRecord.findByAccount(address)
        ?: discoverAddresses().let { ValidatorAddressesRecord.findByAccount(address) }

    fun findAddressByConsensus(address: String) =
        ValidatorAddressesRecord.findByConsensusAddress(address)
        ?: discoverAddresses().let { ValidatorAddressesRecord.findByConsensusAddress(address) }

    fun findAddressByOperator(address: String) =
        ValidatorAddressesRecord.findByOperator(address)
        ?: discoverAddresses().let { ValidatorAddressesRecord.findByOperator(address) }

    private fun findAllConsensusPubkeys() = transaction { ValidatorAddressesRecord.all().map { it.consensusPubkey } }

    private fun discoverAddresses() = let {
        val currentValidatorsKeys = findAllConsensusPubkeys()
        val latestValidators = grpcClient.getLatestValidators()
        val stakingVals = transaction { StakingValidatorCacheRecord.all().map { it.stakingValidator } }
        val pairedAddresses =
            stakingVals.map { Pair<String, String>(it.consensusPubkey.toSingleSigKeyValue()!!, it.operatorAddress) }
        latestValidators
            .filter { !currentValidatorsKeys.contains(it.pubKey.toSingleSigKeyValue()) }
            .forEach { validator ->
                pairedAddresses.firstOrNull { validator.pubKey.toSingleSigKeyValue()!! == it.first }
                    ?.let {
                        ValidatorAddressesRecord.insertIgnore(
                            it.second.translateAddress(props).accountAddr,
                            it.second,
                            validator.pubKey.toSingleSigKeyValue()!!,
                            validator.pubKey.toAddress(props.provValConsPrefix())!!)
                    }
            }
    }

    // Updates the staking validator cache
    fun updateStakingValidators() = transaction {
        val toBeUpdated = StakingValidatorCacheRecord.all()
            .filter { it.lastHit.millis.isPastDue(props.stakingValidatorTtlMs()) }
        val updateAddresses = toBeUpdated.map { it.id.value }
        grpcClient.getStakingValidators()
            .filter { it.operatorAddress in updateAddresses }
            .forEach { stake ->
                toBeUpdated.first { stake.operatorAddress == it.id.value }.delete()
                StakingValidatorCacheRecord.insertIgnore(stake.operatorAddress, stake)
            }
    }

    // In point to get most recent validators
    fun getRecentValidators(count: Int, page: Int, status: String) =
        aggregateValidators(grpcClient.getLatestValidators(), count, page, status)

    // In point to get validators at height
    fun getValidatorsAtHeight(height: Int, count: Int, page: Int) =
        aggregateValidators(getValidatorsByHeight(height).validatorsList, count, page, "active")

    private fun aggregateValidators(validatorSet: List<Query.Validator>, count: Int, page: Int, status: String) = let {
        val stakingValidators = getStakingValidators(status)
        hydrateValidators(validatorSet, stakingValidators)
            .sortedByDescending { it.votingPower }
            .pageOfResults(page, count)
            .let { PagedResults(it.size.pageCountOfResults(count), it) }
    }

    private fun hydrateValidators(validators: List<Query.Validator>, stakingValidators: List<Staking.Validator>) = let {
        val stakingPubKeys = stakingValidators.map { it.consensusPubkey.toSingleSigKeyValue() }
        val signingInfos = getSigningInfos()
        val height = signingInfos.first().indexOffset
        val totalVotingPower = validators.sumBy { it.votingPower.toInt() }
        validators.filter { stakingPubKeys.contains(it.pubKey.toSingleSigKeyValue()) }
            .map { validator ->
                val stakingValidator = stakingValidators
                    .find { it.consensusPubkey.toSingleSigKeyValue() == validator.pubKey.toSingleSigKeyValue() }
                val signingInfo = signingInfos.find { it.address == validator.address }
                hydrateValidator(validator, stakingValidator!!, signingInfo!!, height.toInt(), totalVotingPower)
            }
    }

    private fun hydrateValidator(
        validator: Query.Validator,
        stakingValidator: Staking.Validator,
        signingInfo: Slashing.ValidatorSigningInfo,
        height: Int,
        totalVotingPower: Int
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
            consensusAddress = validator.address,
            proposerPriority = validator.proposerPriority.toInt(),
            votingPower = validator.votingPower.toInt(),
            votingPowerPercent = validator.votingPower.toBigDecimal()
                .divide(totalVotingPower.toBigDecimal(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100)),
            uptime = signingInfo.uptime(height),
            commission = stakingValidator.commission.commissionRates.rate.toScaledDecimal(18),
            bondedTokens = stakingValidator.tokens.toLong(),
            bondedTokensDenomination = "nhash",
            selfBonded = selfBondedAmount.amount.toBigInteger(),
            selfBondedDenomination = selfBondedAmount.denom,
            delegators = delegatorCount,
            bondHeight = 0,
            status = stakingValidator.getStatusString()
        )
    }

    fun getBondedDelegations(address: String, page: Int, limit: Int) =
        grpcClient.getStakingValidatorDelegations(address, page.toOffset(limit), limit).let { res ->
            val list = res.delegationResponsesList.map { ValidatorDelegation(
                it.delegation.delegatorAddress,
                it.balance.amount.toBigInteger(),
                it.balance.denom,
                it.delegation.shares.toScaledDecimal(18),
                null,
                null) }
            PagedResults(res.pagination.total.toInt().pageCountOfResults(limit), list)
        }

    fun getUnbondingDelegations(address: String) =
        grpcClient.getStakingValidatorUnbondingDels(address, 0, 100).let { res ->
            res.unbondingResponsesList.flatMap { list ->
                list.entriesList.map {
                    ValidatorDelegation(
                        list.delegatorAddress,
                        it.balance.toBigInteger(),
                        "nhash",
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
        val rewards = grpcClient.getValidatorCommission(address).commissionList[0]
        return ValidatorCommission(
            validator.tokens.toBigInteger(),
            "nhash",
            selfBondedAmount.amount.toBigInteger(),
            selfBondedAmount.denom,
            validator.tokens.toBigInteger() - selfBondedAmount.amount.toBigInteger(),
            "nhash",
            delegatorCount,
            validator.delegatorShares.toScaledDecimal(18),
            rewards.amount.toScaledDecimal(18),
            rewards.denom,
            validator.commission.commissionRates.rate.toScaledDecimal(18),
            validator.commission.commissionRates.maxRate.toScaledDecimal(18),
            validator.commission.commissionRates.maxChangeRate.toScaledDecimal(18)
        )
    }


}
