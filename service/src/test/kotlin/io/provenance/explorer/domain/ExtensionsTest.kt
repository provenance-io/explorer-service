package io.provenance.explorer.domain

import io.p8e.crypto.Bech32
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class ExtensionsTest {

    @Test
    fun `should return day part of time string`() {
        assertEquals("2019-04-22", "2019-04-22T17:01:51.701356223Z".dayPart())
    }

    @Test
    fun `should return the day as a date`() {
        val expectedDate = LocalDate.parse("2019-04-22", DateTimeFormatter.ISO_DATE)
        assertEquals(expectedDate, "2019-04-22T17:01:51.701356223Z".asDay())
    }

    @Test(expected = DateTimeParseException::class)
    fun `should throw exception trying to parse day from invalid string`() {
        "NOT A DATE".asDay()
    }

    @Test
    fun `should calculate fee from multiplying gas used to min gas price as decimal and string`() {
        val txResult = TxResult(1, "date", "Log", "info", "10000", "70849", "code", mutableListOf<TxEvent>())
        assertEquals(BigDecimal("1771.23"), txResult.fee(BigDecimal(0.025)))
    }

    @Test
    fun `should convert pub key string to bech32`() {
        val result = "Al+JRNPlfbtGwfU7IybTJyzY1kM19ajg3LEUBEasttBe".pubKeyToBech32(Bech32.PROVENANCE_TESTNET_PREFIX)
        kotlin.test.assertEquals("tp14neg0whj7puhwks6536a8lqp7msvd9p04wu287", result)
    }

}