package io.provenance.explorer.service

import io.provenance.core.extensions.logger
import io.provenance.explorer.client.PbClient
import io.provenance.explorer.domain.ValidatorAddresses
import io.provenance.explorer.domain.ValidatorAdressesTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class ValidatorAddressService(private val pbClient: PbClient) {

    protected val logger = logger(ValidatorAddressService::class)
    

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
            it[ValidatorAdressesTable.consensusPubKeyAddress] = consensusPubkeyAddress
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
                    if(match != null) addValidatorKeys(validator.address, validator.pubKey, match.second)
                }
    }

}