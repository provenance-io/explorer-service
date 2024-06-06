package io.provenance.explorer.domain

import io.provenance.explorer.domain.extensions.toThirdDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CoinExtensionsTest {

    @Test
    @Tag("junit-jupiter")
    fun `test toThirdDecimal rounding down`() {
        val testCases = listOf(
            BigDecimal("1.1234567") to BigDecimal("1.123"),
            BigDecimal("2.9876543") to BigDecimal("2.987"),
            BigDecimal("3.999") to BigDecimal("3.999"),
            BigDecimal("4.000") to BigDecimal("4.000"),
            BigDecimal("5.5555555") to BigDecimal("5.555"),
            BigDecimal("6.0001234") to BigDecimal("6.000"),
            BigDecimal("0.123456") to BigDecimal("0.123")
        )

        // Iterate over test cases and verify results
        testCases.forEach { (input, expected) ->
            val result = input.toThirdDecimal()
            assertEquals(expected, result, "Expected $expected but got $result for input $input")
        }
    }
}
