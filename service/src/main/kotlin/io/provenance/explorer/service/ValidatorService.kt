package io.provenance.explorer.service

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.*
import io.provenance.explorer.domain.core.logger
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ValidatorService(private val explorerProperties: ExplorerProperties,
                       private val cacheService: CacheService,
                       private val blockService: BlockService,
                       private val pbClient: PbClient,
                       private val tendermintClient: TendermintClient
) {

    protected val logger = logger(ValidatorService::class)

    fun getValidators(blockHeight: Int) = let {
        var validators = cacheService.getValidatorsByHeight(blockHeight)
        if (validators == null) {
            logger.info("cache miss for validators height $blockHeight")
            validators = pbClient.getValidatorsAtHeight(blockService.getLatestBlockHeightIndex()).let {
                cacheService.addValidatorsToCache(blockHeight, it.get("result"))
                it
            }.get("result")
        }
        OBJECT_MAPPER.readValue(validators.toString(), PbValidatorsResponse::class.java)
    }

    fun getValidator(address: String) = let {
        var validatorAddresses: ValidatorAddresses? = getValidatorOperatorAddress(address)
        var validatorDetails: ValidatorDetails? = null
        if (validatorAddresses != null) {
            val currentHeight = blockService.getLatestBlockHeightIndex()
            //TODO make async and add caching
            val stakingValidator = getStakingValidator(validatorAddresses.operatorAddress)
            val signingInfo = getSigningInfos().result.firstOrNull { it.address == validatorAddresses.consensusAddress }
            val validatorSet = pbClient.getLatestValidators().result.validators
            val latestValidator = validatorSet.firstOrNull { it.address == validatorAddresses.consensusAddress }!!
            val votingPowerPercent = BigDecimal(validatorSet.sumBy { it.votingPower.toInt() }).divide(latestValidator.votingPower.toBigDecimal(), 6, RoundingMode.HALF_UP).multiply(BigDecimal(100))
            validatorDetails = ValidatorDetails(latestValidator.votingPower.toInt(), votingPowerPercent, stakingValidator.description.moniker, validatorAddresses.operatorAddress, validatorAddresses.operatorAddress,
                    validatorAddresses.consensusPubKeyAddress, signingInfo!!.missedBlocksCounter.toInt(), currentHeight - signingInfo!!.startHeight.toInt(),
                    if (stakingValidator.bondHeight != null) stakingValidator.bondHeight.toInt() else 0, signingInfo.uptime(currentHeight))
        }
        validatorDetails
    }

    fun getStakingValidator(operatorAddress: String) = let {
        var stakingValidator = cacheService.getStakingValidator(operatorAddress)
        if (stakingValidator == null) {
            logger.info("cache miss for staking validator operator address $operatorAddress")
            stakingValidator = pbClient.getStakingValidator(operatorAddress).let {
                cacheService.addStakingValidatorToCache(operatorAddress, it.result)
                it.result
            }
        }
        stakingValidator!!
    }

    fun getStakingValidatorDelegations(operatorAddress: String) = let {
        var stakingValidatorDelegations = cacheService.getStakingValidatorDelegations(operatorAddress)
        if (stakingValidatorDelegations == null) {
            logger.info("cache miss staking validator delegations for operator address $operatorAddress")
            stakingValidatorDelegations = pbClient.getStakingValidatorDelegations(operatorAddress).let {
                val pbDelegations = PbDelegations(it.result)
                cacheService.addStakingValidatorDelegations(operatorAddress, pbDelegations)
                pbDelegations
            }
        }
        stakingValidatorDelegations!!
    }

    fun getValidatorDistribution(operatorAddress: String) = pbClient.getValidatorDistribution(operatorAddress).result

    fun getValidatorOperatorAddress(address: String) = if (address.startsWith(explorerProperties.provenanceValidatorConsensusPubKeyPrefix())) {
        findAddressesByConsensusPubKeyAddress(address)
    } else if (address.startsWith(explorerProperties.provenanceValidatorConsensusPrefix())) {
        findAddressesByConsensusAddress(address)
    } else if (address.startsWith(explorerProperties.provenanceValidatorOperatorPrefix())) {
        findAddressesByOperatorAddress(address)
    } else null

    fun getStakingValidators(status: String, page: Int, count: Int) = pbClient.getStakingValidators(status, page, count)

    fun getSigningInfos() = pbClient.getSlashingSigningInfo()


    fun findAddressesByConsensusAddress(consensusAddress: String) = find(::findByConsensusAddress, consensusAddress)

    fun findAddressesByConsensusPubKeyAddress(consensusPubkeyAddress: String) = find(::findByConsensusPubKeyAddress, consensusPubkeyAddress)

    fun findAddressesByOperatorAddress(operatorPubkeyAddress: String) = find(::findByOperatorAddress, operatorPubkeyAddress)

    fun find(query: (a: String) -> ResultRow?, queryAddress: String) = let {
        var resultRow = query(queryAddress)
        if (resultRow == null) {
            logger.info("Unable to find validator with address $queryAddress with query function ${query.javaClass.name}")
            discoverAddresses()
            resultRow = query(queryAddress)
        }
        if (resultRow != null) ValidatorAddresses(resultRow[ValidatorAdressesTable.consensusAddress], resultRow[ValidatorAdressesTable.consensusPubKeyAddress], resultRow[ValidatorAdressesTable.operatorAddress])
        else null
    }

    private fun findByOperatorAddress(operatorAddress: String) = transaction {
        ValidatorAdressesTable.select { (ValidatorAdressesTable.operatorAddress eq operatorAddress) }.firstOrNull()
    }

    private fun findByConsensusAddress(consensusAddress: String) = transaction {
        ValidatorAdressesTable.select { (ValidatorAdressesTable.consensusAddress eq consensusAddress) }.firstOrNull()
    }

    private fun findByConsensusPubKeyAddress(consensusPubKeyAddress: String) = transaction {
        ValidatorAdressesTable.select { (ValidatorAdressesTable.consensusPubKeyAddress eq consensusPubKeyAddress) }.firstOrNull()
    }

    private fun findAllConsensusAddresses() = transaction { ValidatorAdressesTable.selectAll().map { it[ValidatorAdressesTable.consensusAddress] } }

    private fun addValidatorKeys(consensusAddress: String, consensusPubkeyAddress: String, operatorAddress: String) = transaction {
        ValidatorAdressesTable.insertIgnore {
            it[ValidatorAdressesTable.consensusAddress] = consensusAddress
            it[consensusPubKeyAddress] = consensusPubkeyAddress
            it[ValidatorAdressesTable.operatorAddress] = operatorAddress
        }
    }

    private fun discoverAddresses() = let {
        val currentValidators = findAllConsensusAddresses()
        val latestValidators = pbClient.getLatestValidators()
        //TODO make this loop through all validators for the case of more than the limit
        val pairedAddresses = pbClient.getStakingValidators("bonded", 1, 100).result.map { Pair<String, String>(it.consensusPubkey, it.operatorAddress) }
        latestValidators.result.validators
                .filter { !currentValidators.contains(it.address) }
                .forEach { validator ->
                    val match = pairedAddresses.firstOrNull() { validator.pubKey == it.first }
                    if (match != null) addValidatorKeys(validator.address, validator.pubKey, match.second)
                }
    }
}
