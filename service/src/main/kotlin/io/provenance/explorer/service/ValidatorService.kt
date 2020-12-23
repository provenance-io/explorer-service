package io.provenance.explorer.service

import io.provenance.core.extensions.logger
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

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
            val stakingValidator = pbClient.getStakingValidator(validatorAddresses.operatorAddress)
            val signingInfo = pbClient.getSlashingSigningInfo().result.firstOrNull { it.address == validatorAddresses.consensusAddress }
            val latestValidator = pbClient.getLatestValidators().result.validators.firstOrNull { it.address == validatorAddresses.consensusAddress }
            validatorDetails = ValidatorDetails(latestValidator!!.votingPower.toInt(), stakingValidator.result.description.moniker, validatorAddresses.operatorAddress, validatorAddresses.operatorAddress,
                    validatorAddresses.consensusPubKeyAddress, signingInfo!!.missedBlocksCounter.toInt(), currentHeight - signingInfo!!.startHeight.toInt(),
                    if (stakingValidator.result.bondHeight != null) stakingValidator.result.bondHeight.toInt() else 0, signingInfo.uptime(currentHeight))
        }
        validatorDetails
    }

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