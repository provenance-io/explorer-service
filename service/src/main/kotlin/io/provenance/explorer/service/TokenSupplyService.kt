package io.provenance.explorer.service

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.models.explorer.TokenSupply
import io.provenance.explorer.grpc.v1.BankGrpcClient
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service

@Service
class TokenSupplyService(private val bankClient: BankGrpcClient, private val markerClient: MarkerGrpcClient) {

    protected val logger = logger(TokenSupplyService::class)

    fun getTokenStats() = runBlocking {
        // retrieve nhash marker address
        val markerAddress = MarkerCacheRecord.findByDenom(NHASH)?.markerAddress!!
        val markerAmount = bankClient.getMarkerBalance(markerAddress, NHASH).toBigDecimal()

        val currentSupply = bankClient.getCurrentSupply(NHASH).toBigDecimal()
        val communityPoolSupply = bankClient.getCommunityPoolAmount(NHASH).toBigDecimal()
        val (stakingTotal, bonded) = bankClient.getStakingPool().pool.let { pool ->
            pool.bondedTokens.toBigDecimal().plus(pool.notBondedTokens.toBigDecimal()) to
                pool.bondedTokens.toBigDecimal().toCoinStr(NHASH)
        }

        val zeroSeqAccounts = AccountRecord.findZeroSequenceAccounts()

        val zeroSeqBalance = async { markerClient.getAllMarkerHolders(NHASH) }.await().asFlow()
            .filter { zeroSeqAccounts.contains(it.address) }
            .map { bal -> bal.coinsList.first { coin -> coin.denom == NHASH }.amount.toBigDecimal() }
            .toList()
            .sumOf { it }
            .plus(markerAmount)

        val circulation = (currentSupply - communityPoolSupply - stakingTotal - zeroSeqBalance).toCoinStr(NHASH)
        val communityPool = communityPoolSupply.toCoinStr(NHASH)

        TokenSupply(currentSupply.toCoinStr(NHASH), circulation, communityPool, bonded)
    }
}
