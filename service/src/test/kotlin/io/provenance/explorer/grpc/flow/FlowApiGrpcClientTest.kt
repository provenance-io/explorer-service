package io.provenance.explorer.grpc.flow

import io.provlabs.flow.api.NavEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.joda.time.DateTime
import java.net.URI

class FlowApiGrpcClientTest {

    companion object {
        private lateinit var grpcClient: FlowApiGrpcClient

        @BeforeAll
        @JvmStatic
        fun setup() {
            val testGrpcUri = URI.create("grpc://localhost:50051")
            grpcClient = FlowApiGrpcClient(testGrpcUri)
        }
    }

    @Test
    @Disabled("This test is used for manually executing the grpc endpoint")
    fun `test getLatestNavPrices with valid data`() {
        val priceDenom = "usd"
        val includeMarkers = true
        val includeScopes = true
        val fromDate = DateTime.now().minusDays(7)
        val limit = 5

        val navEvents: List<NavEvent> = grpcClient.getLatestNavPrices(priceDenom, includeMarkers, includeScopes, fromDate, limit)

        assertNotNull(navEvents)
        assertFalse(navEvents.isEmpty(), "Expected non-empty list of NAV events")
        assertTrue(navEvents.size <= limit, "Expected results within the specified limit")

        navEvents.forEach { event ->
            assertEquals(priceDenom, event.priceDenom, "Price denomination mismatch")
        }
    }
}
