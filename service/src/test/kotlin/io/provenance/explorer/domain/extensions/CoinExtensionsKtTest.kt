package io.provenance.explorer.domain.extensions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CoinExtensionsKtTest {

    @Test
    fun `test percentChange extension`() {
        val increaseFrom100To120 = BigDecimal("120").percentChange(BigDecimal("100"))
        assertEquals(BigDecimal("20.0"), increaseFrom100To120, "Percent change calculation is incorrect")

        val decreaseFrom100To80 = BigDecimal("80").percentChange(BigDecimal("100"))
        assertEquals(BigDecimal("-20.0"), decreaseFrom100To80, "Percent change calculation is incorrect")

        val noChangeAt100 = BigDecimal("100").percentChange(BigDecimal("100"))
        assertEquals(BigDecimal("0.0"), noChangeAt100, "Percent change calculation is incorrect")

        val smallIncreaseFrom100To100_01 = BigDecimal("100.01").percentChange(BigDecimal("100"))
        assertEquals(BigDecimal("0.0"), smallIncreaseFrom100To100_01, "Percent change calculation is incorrect")

        val rounding = BigDecimal("1.600").percentChange(BigDecimal("1.4"))
        assertEquals(BigDecimal("14.3"), rounding, "Percent change calculation is incorrect")

        val divisionByZero = BigDecimal("100").percentChange(BigDecimal("0"))
        assertEquals(BigDecimal.ZERO, divisionByZero, "Percent change calculation is incorrect when dividing by zero")
    }
}
