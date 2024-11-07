package io.provenance.explorer.domain.entities

import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NavEventsRecordTest : BaseDbTest() {

    @BeforeEach
    fun setup() {
        transaction {
            // Insert test data
            NavEventsRecord.insert(
                1, DateTime.now(), "hash1", 0, "marker_nav", null, "nhash", 100L, "usd", 1000L, "market"
            )
            NavEventsRecord.insert(
                2, DateTime.now(), "hash2", 0, "scope_nav", "scope1", null, 200L, "usd", 2000L, "market"
            )
            NavEventsRecord.insert(
                3, DateTime.now(), "hash3", 0, "marker_nav", null, "hash", 300L, "usdt", 3000L, "market"
            )
        }
    }

    @AfterEach
    fun cleanup() {
        transaction {
            NavEventsTable.deleteAll()
        }
    }

    @Test
    fun `getNavEvents - filter by denom`() = transaction {
        val events = NavEventsRecord.getNavEvents(denom = "nhash")
        assertEquals(1, events.size)
        assertEquals("nhash", events.first().denom)
        assertEquals(100L, events.first().priceAmount)
    }

    @Test
    fun `getNavEvents - filter by scopeId`() = transaction {
        val events = NavEventsRecord.getNavEvents(scopeId = "scope1")
        assertEquals(1, events.size)
        assertEquals("scope1", events.first().scopeId)
        assertEquals(200L, events.first().priceAmount)
    }

    @Test
    fun `getNavEvents - filter by date range`() = transaction {
        val fromDate = DateTime.now().minusDays(1)
        val toDate = DateTime.now().plusDays(1)
        val events = NavEventsRecord.getNavEvents(fromDate = fromDate, toDate = toDate)
        assertEquals(3, events.size)
    }

    @Test
    fun `getNavEvents - filter by price denoms`() = transaction {
        val events = NavEventsRecord.getNavEvents(priceDenoms = listOf("usd"))
        assertEquals(2, events.size)
        events.forEach {
            assertEquals("usd", it.priceDenom)
        }
    }

    @Test
    fun `getNavEvents - no filters returns all records`() = transaction {
        val events = NavEventsRecord.getNavEvents()
        assertEquals(3, events.size)
    }

    @Test
    fun `getLatestNavEvents - include markers only`() = transaction {
        val events = NavEventsRecord.getLatestNavEvents(
            priceDenom = "usd",
            includeMarkers = true,
            includeScope = false
        )
        assertEquals(1, events.size)
        assertNotNull(events.first().denom)
        assertNull(events.first().scopeId)
    }

    @Test
    fun `getLatestNavEvents - include scope only`() = transaction {
        val events = NavEventsRecord.getLatestNavEvents(
            priceDenom = "usd",
            includeMarkers = false,
            includeScope = true
        )
        assertEquals(1, events.size)
        assertNull(events.first().denom)
        assertNotNull(events.first().scopeId)
    }

    @Test
    fun `getLatestNavEvents - include both markers and scope`() = transaction {
        val events = NavEventsRecord.getLatestNavEvents(
            priceDenom = "usd",
            includeMarkers = true,
            includeScope = true
        )
        assertEquals(2, events.size)
    }

    @Test
    fun `getLatestNavEvents - with from date filter`() = transaction {
        val fromDate = DateTime.now().minusHours(1)
        val events = NavEventsRecord.getLatestNavEvents(
            priceDenom = "usd",
            includeMarkers = true,
            includeScope = true,
            optionalFromDate = fromDate
        )
        assertEquals(2, events.size)
        events.forEach {
            assertTrue(it.blockTime.isAfter(fromDate))
        }
    }

    @Test
    fun `getLatestNavEvents - throws exception when neither markers nor scope included`() {
        assertThrows(IllegalArgumentException::class.java) {
            NavEventsRecord.getLatestNavEvents(
                priceDenom = "usd",
                includeMarkers = false,
                includeScope = false
            )
        }
    }

    @Test
    fun `getLatestNavEvents - returns empty list when no matching records`() = transaction {
        val events = NavEventsRecord.getLatestNavEvents(
            priceDenom = "nonexistent",
            includeMarkers = true,
            includeScope = true
        )
        assertTrue(events.isEmpty())
    }
}
