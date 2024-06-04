package io.provenance.explorer.service

import io.provenance.explorer.domain.models.OsmosisHistoricalPrice
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI

class TokenServiceTest {

    private lateinit var accountClient: AccountGrpcClient
    private lateinit var tokenService: TokenService

    @BeforeEach
    fun setUp() {
        accountClient = AccountGrpcClient(URI("https://www.google.com"))
        tokenService = TokenService(accountClient)
    }

    @Test
    fun `test fetchOsmosisData and print results`() = runBlocking {
        val fromDate = DateTime.parse("2023-01-01")
        val toDate = DateTime.parse("2023-01-10")

        val result: List<OsmosisHistoricalPrice> = tokenService.fetchOsmosisData(fromDate, toDate)

        result.forEach {
            println("Time: ${it.time}, Open: ${it.open}, High: ${it.high}, Low: ${it.low}, Close: ${it.close}, Volume: ${it.volume}")
        }
    }
}
