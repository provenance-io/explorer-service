package io.provenance.explorer.grpc.v1

import cosmos.base.query.v1beta1.pageRequest
import io.grpc.ManagedChannelBuilder
import io.provenance.exchange.v1.AccountAmount
import io.provenance.exchange.v1.Market
import io.provenance.exchange.v1.MarketBrief
import io.provenance.exchange.v1.QueryGetAllMarketsRequest
import io.provenance.exchange.v1.QueryGetMarketCommitmentsRequest
import io.provenance.exchange.v1.QueryGetMarketRequest
import io.provenance.exchange.v1.QueryGrpcKt
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.domain.extensions.toByteString
import io.provenance.explorer.grpc.extensions.addBlockHeightToQuery
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

    suspend fun getMarketCommitments(marketId: Int, height: Int? = null): List<AccountAmount> {
        val limit = 1000
        var nextKey = "".toByteString()
        val allCommitments = mutableListOf<AccountAmount>()

        do {
            exchangeClient
                .addBlockHeightToQuery(height)
                .getMarketCommitments(
                    QueryGetMarketCommitmentsRequest.newBuilder().apply {
                        this.marketId = marketId
                        this.pagination = pageRequest {
                            this.limit = limit.toLong()
                            if (nextKey.toStringUtf8().isNotBlank()) {
                                this.key = nextKey
                            }
                        }
                    }.build()
                ).let {
                    nextKey = it.pagination.nextKey
                    allCommitments.addAll(it.commitmentsList)
                }
        } while (nextKey.toStringUtf8().isNotBlank())

        return allCommitments
    }

    suspend fun totalCommitmentCount(height: Int? = null) = exchangeClient
        .addBlockHeightToQuery(height)
        .getAllMarkets(
        QueryGetAllMarketsRequest.newBuilder().build(),
    ).marketsList.sumOf { getMarketCommitments(it.marketId, height).size }

    suspend fun totalCommittedAssetTotals(height: Int? = null) = exchangeClient
        .addBlockHeightToQuery(height)
        .getAllMarkets(
        QueryGetAllMarketsRequest.newBuilder().build()
    ).marketsList.flatMap {
        marketTotalCommittedAssets(it.marketId, height)
    }.flatten()
        .groupBy { it.first }.mapValues { it.value.sumOf { v -> v.second } }

    suspend fun marketTotalCommittedAssets(marketId: Int, height: Int? = null) = getMarketCommitments(marketId, height)
        .map { commitment ->
            commitment.amountList.map { amount ->
                amount.denom to amount.amount.toBigDecimal()
            }
        }
}
