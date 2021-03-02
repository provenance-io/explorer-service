package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.StakingValidatorCacheRecord
import io.provenance.explorer.domain.entities.ValidatorAddressesRecord
import io.provenance.explorer.domain.entities.ValidatorDelegationCacheRecord
import io.provenance.explorer.domain.entities.ValidatorsCacheRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.extensions.isPastDue
import io.provenance.explorer.domain.extensions.translateAddress
import io.provenance.explorer.domain.extensions.uptime
import io.provenance.explorer.domain.models.explorer.ValidatorDetails
import io.provenance.explorer.grpc.toConsAddress
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

    fun getValidatorsByHeightFromCache(blockHeight: Int) = transaction {
        ValidatorsCacheRecord.findById(blockHeight)?.also {
            ValidatorsCacheRecord.updateHitCount(blockHeight)
        }?.validators
    }

    fun getStakingValidatorFromCache(operatorAddress: String) = transaction {
        StakingValidatorCacheRecord.findById(operatorAddress)?.let {
            if (it.lastHit.millis.isPastDue(props.stakingValidatorTtlMs())) {
                it.delete()
                null
            } else it.stakingValidator
        }
    }

    fun getStakingValidatorDelegationsFromCache(operatorAddress: String) = transaction {
        ValidatorDelegationCacheRecord.findById(operatorAddress)?.let {
            if (it.lastHit.millis.isPastDue(props.stakingValidatorDelegationsTtlMs())) {
                it.delete()
                null
            } else it.validatorDelegations
        }
    }

    fun getValidators(blockHeight: Int) =
        getValidatorsByHeightFromCache(blockHeight)
        ?: grpcClient.getValidatorsAtHeight(blockService.getLatestBlockHeightIndex())
            .let { ValidatorsCacheRecord.insertIgnore(blockHeight, it) }

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
                stakingValidator.consensusPubkey.toConsAddress(props.provValConsPrefix()) ?: "",
                signingInfo!!.missedBlocksCounter.toInt(),
                currentHeight - signingInfo.startHeight.toInt(),
                0,
                signingInfo.uptime(currentHeight),
                null,  // TODO: Update when we can get images going
                stakingValidator.description.details,
                stakingValidator.description.website,
                stakingValidator.description.identity)
        }

    fun getStakingValidator(operatorAddress: String) =
        getStakingValidatorFromCache(operatorAddress)
        ?: grpcClient.getStakingValidator(operatorAddress)
            .let { StakingValidatorCacheRecord.insertIgnore(operatorAddress, it) }

    fun getStakingValidatorDelegations(operatorAddress: String) =
        getStakingValidatorDelegationsFromCache(operatorAddress)
        ?: grpcClient.getStakingValidatorDelegations(operatorAddress)
            .let { ValidatorDelegationCacheRecord.insertIgnore(operatorAddress, it) }

//    fun getValidatorDistribution(operatorAddress: String) = grpcClient.getValidatorDistribution(operatorAddress).result

    fun getValidatorOperatorAddress(address: String) = when {
        address.startsWith(props.provValOperPrefix()) -> findAddressByOperator(address)
        address.startsWith(props.provValConsPrefix()) -> findAddressByConsensus(address)
        address.startsWith(props.provAccPrefix()) -> findAddressByAccount(address)
        else -> null
    }

    fun getStakingValidators(status: String, offset: Int, count: Int) =
        grpcClient.getStakingValidators(status, offset, count)

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
        //TODO make this loop through all validators for the case of more than the limit
        val pairedAddresses = grpcClient.getStakingValidators("BOND_STATUS_BONDED", 0, 100)
            .map { Pair<String, String>(it.consensusPubkey.toSingleSigKeyValue()!!, it.operatorAddress) }
        latestValidators
            .filter { !currentValidatorsKeys.contains(it.pubKey.toSingleSigKeyValue()) }
            .forEach { validator ->
                pairedAddresses.firstOrNull { validator.pubKey.toSingleSigKeyValue()!! == it.first }
                    ?.let {
                        ValidatorAddressesRecord.insertIgnore(
                            it.second.translateAddress(props).accountAddr,
                            it.second,
                            validator.pubKey.toSingleSigKeyValue()!!,
                            validator.pubKey.toConsAddress(props.provValConsPrefix())!!)
                    }
            }
    }
}
