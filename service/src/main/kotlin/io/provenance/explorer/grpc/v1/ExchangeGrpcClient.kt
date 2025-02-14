package io.provenance.explorer.grpc.v1

import cosmos.base.query.v1beta1.Pagination
import io.grpc.ManagedChannelBuilder
import io.provenance.exchange.v1.AccountAmount
import io.provenance.exchange.v1.Market
import io.provenance.exchange.v1.MarketBrief
import io.provenance.exchange.v1.QueryGetAllMarketsRequest
import io.provenance.exchange.v1.QueryGetMarketCommitmentsRequest
import io.provenance.exchange.v1.QueryGetMarketRequest
import io.provenance.exchange.v1.QueryGrpcKt
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class ExchangeGrpcClient(channelUri: URI) {

    private val exchangeClient: QueryGrpcKt.QueryCoroutineStub

    init {
        val channel =
            ManagedChannelBuilder.forAddress(channelUri.host, channelUri.port)
                .also {
                    if (channelUri.scheme == "grpcs") {
                        it.useTransportSecurity()
                    } else {
                        it.usePlaintext()
                    }
                }
                .idleTimeout(5, TimeUnit.MINUTES)
                .keepAliveTime(60, TimeUnit.SECONDS)
                .keepAliveTimeout(20, TimeUnit.SECONDS)
                .intercept(GrpcLoggingInterceptor())
                .build()

        exchangeClient = QueryGrpcKt.QueryCoroutineStub(channel)
    }

    suspend fun getMarket(marketId: Int): Market = exchangeClient.getMarket(
        QueryGetMarketRequest.newBuilder().apply {
            this.marketId = marketId
        }.build()
    ).market

    suspend fun getMarketBriefsByDenom(denom: String): List<MarketBrief> = exchangeClient.getAllMarkets(
        QueryGetAllMarketsRequest.newBuilder().build()
    ).marketsList.filter {
        getMarketCommitments(it.marketId).find { commitment ->
            commitment.amountList.find { amount ->
                amount.denom == denom
            } != null
        } != null
    }

    suspend fun getMarketCommitments(marketId: Int): List<AccountAmount> = exchangeClient.getMarketCommitments(
        QueryGetMarketCommitmentsRequest.newBuilder().apply {
            this.marketId = marketId
            // TODO cripes this is going to need paging
            this.pagination = Pagination.PageRequest.newBuilder().apply {
                limit = 10000
            }.build()
        }.build()
    ).commitmentsList

    suspend fun totalCommitmentCount() = exchangeClient.getAllMarkets(
        QueryGetAllMarketsRequest.newBuilder().build()
    ).marketsList.sumOf { getMarketCommitments(it.marketId).size }

    suspend fun totalCommittedAssetTotals() = exchangeClient.getAllMarkets(
        QueryGetAllMarketsRequest.newBuilder().build()
    ).marketsList.flatMap {
        getMarketCommitments(it.marketId).map { commitment ->
            commitment.amountList.map { amount ->
                amount.denom to amount.amount.toBigDecimal()
            }
        }
    }.flatten()
        .groupBy { it.first }.mapValues { it.value.sumOf { v -> v.second } }
}
