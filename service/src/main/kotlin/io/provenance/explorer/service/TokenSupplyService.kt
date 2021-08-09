package io.provenance.explorer.service

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.models.explorer.TokenSupply
import io.provenance.explorer.grpc.v1.BankGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class TokenSupplyService(private val bankClient: BankGrpcClient) {

    protected val logger = logger(TokenSupplyService::class)

    fun getTokenStats() = transaction {
        // retrieve nhash marker address
        val markerAddress = MarkerCacheRecord.findByDenom(NHASH)?.markerAddress!!
        val markerAmount = bankClient.getMarkerBalance(markerAddress, NHASH).toBigDecimal()

        val currentSupply = bankClient.getCurrentSupply(NHASH).toBigDecimal()
        val communityPoolSupply = bankClient.getCommunityPoolAmount(NHASH).toBigDecimal()
        val bondedSupply = bankClient.getBondedAmount().toBigDecimal()

        val circulation = (currentSupply - communityPoolSupply - bondedSupply - markerAmount).toCoinStr(NHASH)
        val communityPool = communityPoolSupply.toCoinStr(NHASH)
        val bonded = bondedSupply.toCoinStr(NHASH)

        TokenSupply(circulation, communityPool, bonded)
    }
}
