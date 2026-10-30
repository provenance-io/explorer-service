package io.provenance.explorer.domain.extensions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

class LoanLedgerSnapshotKtTest {

    @Test
    fun `unset ledger epoch days are null`() {
        assertNull(ledgerDaysSinceEpochToDate(0))
        assertNull(ledgerDaysSinceEpochToDate(-1))
    }

    @Test
    fun `positive ledger epoch days convert to UTC dates`() {
        val asOf = LocalDate.of(2026, 10, 30)
        assertEquals(LocalDate.of(1970, 1, 2), ledgerDaysSinceEpochToDate(1))
        assertEquals(asOf, ledgerDaysSinceEpochToDate(asOf.toEpochDay().toInt()))
    }

    @Test
    fun `base64 uuid spec id converts to scopespec bech32`() {
        val uuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")
        val bytes = ByteBuffer.allocate(16)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
        val addr = specificationIdToScopeSpecAddr(Base64.getEncoder().encodeToString(bytes))
        assertTrue(addr!!.startsWith("scopespec"))
    }

    @Test
    fun `blank spec id is skipped`() {
        assertNull(specificationIdToScopeSpecAddr(""))
    }
}
