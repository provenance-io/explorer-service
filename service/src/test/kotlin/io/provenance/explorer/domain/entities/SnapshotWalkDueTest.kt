package io.provenance.explorer.domain.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class SnapshotWalkDueTest {

    private val maxAge = Duration.ofHours(6)

    @Test
    fun `never written is due`() {
        val now = LocalDateTime.of(2026, 10, 9, 16, 0)
        assertTrue(snapshotWalkDue(now, null, maxAge))
        assertEquals("never", snapshotWalkDueReason(now, null, maxAge))
    }

    @Test
    fun `fresh write is not due`() {
        val now = LocalDateTime.of(2026, 10, 9, 16, 0)
        val updated = now.minusHours(2)
        assertFalse(snapshotWalkDue(now, updated, maxAge))
    }

    @Test
    fun `write older than max age is due`() {
        val now = LocalDateTime.of(2026, 10, 9, 16, 0)
        val updated = now.minusHours(6).minusMinutes(1)
        assertTrue(snapshotWalkDue(now, updated, maxAge))
        assertEquals("stale", snapshotWalkDueReason(now, updated, maxAge))
    }

    @Test
    fun `end of day is due when last write was before 22 00 UTC`() {
        val updated = LocalDateTime.of(2026, 10, 9, 16, 30)
        val evening = LocalDateTime.of(2026, 10, 9, 22, 5)
        assertTrue(snapshotWalkDue(evening, updated, maxAge))
        assertEquals("end-of-day", snapshotWalkDueReason(evening, updated, maxAge))
    }

    @Test
    fun `end of day is not due when last write finished after 22 00 UTC`() {
        val updated = LocalDateTime.of(2026, 10, 9, 22, 20)
        val evening = LocalDateTime.of(2026, 10, 9, 22, 40)
        assertFalse(snapshotWalkDue(evening, updated, maxAge))
    }

    @Test
    fun `new utc day with no row yet is due`() {
        val nextMorning = LocalDateTime.of(2026, 10, 10, 0, 10)
        assertTrue(snapshotWalkDue(nextMorning, null, maxAge))
    }
}
