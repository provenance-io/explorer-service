package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.models.OsmosisHistoricalPrice
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provlabs.flow.api.NavEvent
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.net.URI

class TokenServiceTest {

    private lateinit var accountClient: AccountGrpcClient
    private lateinit var tokenService: TokenService

    @BeforeEach
    fun setUp() {
        accountClient = AccountGrpcClient(URI("https://www.google.com"))
    }
}
