package io.provenance.explorer.domain.extensions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

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
        val addr = specificationIdToScopeSpecAddr("BFTd2Lw1AUeJhvG9Zlrx9j0=")
        assertTrue(addr!!.startsWith("scopespec"))
    }

    @Test
    fun `blank spec id is skipped`() {
        assertNull(specificationIdToScopeSpecAddr(""))
    }
}
