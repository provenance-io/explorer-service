package io.provenance.explorer.service

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.models.explorer.TokenSupply
import io.provenance.explorer.grpc.v1.BankGrpcClient
import org.springframework.stereotype.Service

@Service
class TokenSupplyService(private val bankClient: BankGrpcClient) {

    protected val logger = logger(TokenSupplyService::class)

    private val DENOM = "nhash"

    fun getTokenStats() = TokenSupply(
        bankClient.getCurrentSupply(DENOM),
        bankClient.getCommunityPoolAmount(DENOM),
        bankClient.getBondedAmount(),
    )
}
