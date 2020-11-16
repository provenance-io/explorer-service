package io.provenance.explorer.domain

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
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
}