package io.provenance.explorer.grpc.flow

import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provlabs.flow.api.NavEvent
import org.joda.time.DateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
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

    @Test
    @Disabled("This test is used for manually executing the grpc endpoint")
    fun `test getAllLatestNavPrices with valid data`() {
        val priceDenom = "usd"
        val includeMarkers = true
        val includeScopes = true
        val fromDate = DateTime.now().minusDays(1)

        val navEvents: List<NavEvent> = grpcClient.getAllLatestNavPrices(priceDenom, includeMarkers, includeScopes, fromDate)

        assertNotNull(navEvents)
        assertFalse(navEvents.isEmpty(), "Expected non-empty list of NAV events")

        navEvents.forEach { event ->
            assertEquals(priceDenom, event.priceDenom, "Price denomination mismatch")
        }
    }

    @Test
    @Disabled("This test is used for manually executing the grpc endpoint")
    fun `test getAllMarkerNavByPriceDenoms with valid data`() {
        val denom = UTILITY_TOKEN
        val priceDenoms = listOf("uusd.trading", "uusdc.figure.se", "uusdt.figure.se")
        val fromDate = DateTime.now().minusDays(7)

        val navEvents: List<NavEvent> = grpcClient.getAllMarkerNavByPriceDenoms(denom, priceDenoms, fromDate)

        assertNotNull(navEvents)
        assertFalse(navEvents.isEmpty(), "Expected non-empty list of NAV events")

        navEvents.forEach { event ->
            assertEquals(denom, event.denom, "Nav denomination mismatch")
            assertTrue(priceDenoms.contains(event.priceDenom), "Price denom mismatch")
        }
    }
}
