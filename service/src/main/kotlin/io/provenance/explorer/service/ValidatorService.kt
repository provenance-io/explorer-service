package io.provenance.explorer.service

import io.provenance.explorer.client.PbClient
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.PbDelegations
import io.provenance.explorer.domain.ValidatorDetails
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.ValidatorAddressesRecord
import io.provenance.explorer.domain.extensions.edPubKeyToBech32
import io.provenance.explorer.domain.extensions.uptime
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ValidatorService(
    private val explorerProperties: ExplorerProperties,
    private val cacheService: CacheService,
    private val blockService: BlockService,
    private val pbClient: PbClient
) {

    protected val logger = logger(ValidatorService::class)

    fun getValidators(blockHeight: Int) =
        cacheService.getValidatorsByHeight(blockHeight)
            ?: pbClient.getValidatorsAtHeight(blockService.getLatestBlockHeightIndex())
                .let { cacheService.addValidatorsToCache(blockHeight, it.result) }

    fun getValidator(address: String) =
        getValidatorOperatorAddress(address)?.let { addr ->
            val currentHeight = blockService.getLatestBlockHeightIndex()
            //TODO make async and add caching
            val stakingValidator = getStakingValidator(addr.operatorAddress)
            val signingInfo = getSigningInfos().result.firstOrNull { it.address == addr.consensusAddress }
            val validatorSet = pbClient.getLatestValidators().result.validators
            val latestValidator = validatorSet.firstOrNull { it.address == addr.consensusAddress }!!
            val votingPowerPercent = BigDecimal(validatorSet.sumBy { it.votingPower.toInt() })
                .divide(latestValidator.votingPower.toBigDecimal(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
            ValidatorDetails(
                latestValidator.votingPower.toInt(),
                votingPowerPercent,
                stakingValidator.description.moniker,
                addr.operatorAddress,
                addr.operatorAddress,
                addr.consensusPubKeyAddress,
                signingInfo!!.missedBlocksCounter?.toInt() ?: 0,
                currentHeight - signingInfo.startHeight?.toInt()!! ?: 0,
                if (stakingValidator.bondHeight != null) stakingValidator.bondHeight.toInt() else 0,
                signingInfo.uptime(currentHeight))
        }

    fun getStakingValidator(operatorAddress: String) =
        cacheService.getStakingValidator(operatorAddress)
            ?: pbClient.getStakingValidator(operatorAddress).let { cacheService.addStakingValidatorToCache(operatorAddress, it.result) }

    fun getStakingValidatorDelegations(operatorAddress: String) =
        cacheService.getStakingValidatorDelegations(operatorAddress)
            ?: pbClient.getStakingValidatorDelegations(operatorAddress)
                .let { cacheService.addStakingValidatorDelegations(operatorAddress, PbDelegations(it.result)) }

    fun getValidatorDistribution(operatorAddress: String) = pbClient.getValidatorDistribution(operatorAddress).result

    fun getValidatorOperatorAddress(address: String) = when {
        address.startsWith(explorerProperties.provenanceValidatorConsensusPubKeyPrefix()) -> findAddressByConsensusPubKey(address)
        address.startsWith(explorerProperties.provenanceValidatorConsensusPrefix()) -> findAddressByConsensus(address)
        address.startsWith(explorerProperties.provenanceValidatorOperatorPrefix()) -> findAddressByOperator(address)
        else -> null
    }

    fun getStakingValidators(status: String, page: Int, count: Int) = pbClient.getStakingValidators(status, page, count)

    fun getSigningInfos() = pbClient.getSlashingSigningInfo()

    fun findAddressByConsensusPubKey(address: String) =
        ValidatorAddressesRecord.findByConsensusPubKey(address)
            ?: discoverAddresses().let { ValidatorAddressesRecord.findByConsensusPubKey(address) }

    fun findAddressByConsensus(address: String) =
        ValidatorAddressesRecord.findByConsensus(address)
            ?: discoverAddresses().let { ValidatorAddressesRecord.findByConsensus(address) }

    fun findAddressByOperator(address: String) =
        ValidatorAddressesRecord.findByOperator(address)
            ?: discoverAddresses().let { ValidatorAddressesRecord.findByOperator(address) }

    private fun findAllConsensusAddresses() = transaction { ValidatorAddressesRecord.all().map { it.consensusAddress } }

    private fun discoverAddresses() = let {
        val currentValidators = findAllConsensusAddresses()
        val latestValidators = pbClient.getLatestValidators()
        //TODO make this loop through all validators for the case of more than the limit
        val pairedAddresses = pbClient.getStakingValidators("BOND_STATUS_BONDED", 1, 100)
            .result
            .map { Pair<String, String>(it.consensusPubkey.value.edPubKeyToBech32(explorerProperties.provenanceValidatorConsensusPubKeyPrefix()), it.operatorAddress) }
        latestValidators.result.validators
            .filter { !currentValidators.contains(it.address) }
            .forEach { validator ->
                pairedAddresses.firstOrNull { validator.pubKey.value.edPubKeyToBech32(explorerProperties.provenanceValidatorConsensusPubKeyPrefix()) == it.first }?.let {
                    ValidatorAddressesRecord.insertIgnore(validator.address, validator.pubKey.value.edPubKeyToBech32(explorerProperties.provenanceValidatorConsensusPubKeyPrefix()), it.second)
                }
            }
    }
}
