package io.provenance.explorer.service

import io.provenance.explorer.domain.models.HistoricalPrice
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.service.pricing.fetchers.HistoricalPriceFetcherFactory
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.net.URI

class TokenServiceTest {

    private lateinit var accountClient: AccountGrpcClient
    private lateinit var flowApiGrpcClient: FlowApiGrpcClient
    private lateinit var historicalPriceFetcherFactory: HistoricalPriceFetcherFactory
    private lateinit var tokenService: TokenService

    @BeforeEach
    fun setUp() {
        accountClient = AccountGrpcClient(URI("http://localhost:26657"))
        flowApiGrpcClient = FlowApiGrpcClient(URI("http://localhost:50051"))
        historicalPriceFetcherFactory = HistoricalPriceFetcherFactory(flowApiGrpcClient) // Set the factory as needed
        tokenService = TokenService(accountClient, flowApiGrpcClient, historicalPriceFetcherFactory)
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test fetchHistoricalPriceData`() = runBlocking {
        val fromDate = DateTime.now().minusDays(7)

        val result: List<HistoricalPrice> = tokenService.fetchHistoricalPriceData(fromDate)

        result.forEach {
            println("Time: ${DateTime(it.time)}, Open: ${it.open}, High: ${it.high}, Low: ${it.low}, Close: ${it.close}, Volume: ${it.volume}")
        }

        assert(result.isNotEmpty()) { "Expected non-empty list of HistoricalPrice" }
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test fetchLegacyHistoricalPriceData`() = runBlocking {
        val fromDate = DateTime.now().minusDays(7)

        val result: List<HistoricalPrice> = tokenService.fetchLegacyHistoricalPriceData(fromDate)

        result.forEach {
            println("Time: ${DateTime(it.time)}, Open: ${it.open}, High: ${it.high}, Low: ${it.low}, Close: ${it.close}, Volume: ${it.volume}")
        }

        assert(result.isNotEmpty()) { "Expected non-empty list of HistoricalPrice" }
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test getTokenDistributionStats`() {
        val result = tokenService.getTokenDistributionStats()
        println(result)
        assert(result.isNotEmpty()) { "Expected non-empty token distribution stats" }
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test getTokenBreakdown`() = runBlocking {
        val result = tokenService.getTokenBreakdown()
        println("Max supply: ${result.maxSupply}")
        println("Community pool supply: ${result.communityPool}")
        println("Bonded supply: ${result.bonded}")
        println("Burned supply: ${result.burned}")
    }
}
