package io.provenance.explorer.service

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.extensions.NHASH
import io.provenance.explorer.domain.models.explorer.CoinStr
import io.provenance.explorer.domain.models.explorer.TokenSupply
import io.provenance.explorer.grpc.v1.BankGrpcClient
import org.springframework.stereotype.Service

@Service
class TokenSupplyService(private val bankClient: BankGrpcClient) {

    protected val logger = logger(TokenSupplyService::class)

    fun getTokenStats(): TokenSupply {
        val currentSupply = bankClient.getCurrentSupply(NHASH).toBigDecimal()
        val communityPoolSupply = bankClient.getCommunityPoolAmount(NHASH).toBigDecimal()
        val bondedSupply = bankClient.getBondedAmount().toBigDecimal()

        val circulation = CoinStr((currentSupply - communityPoolSupply - bondedSupply).toString(), NHASH)
        val communityPool = CoinStr(communityPoolSupply.toString(), NHASH)
        val bonded =  CoinStr(bondedSupply.toString(), NHASH)

        return TokenSupply(circulation, communityPool, bonded)
    }
}
