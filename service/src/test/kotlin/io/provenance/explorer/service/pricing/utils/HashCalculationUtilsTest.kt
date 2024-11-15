package io.provenance.explorer.service.pricing.utils

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class HashCalculationUtilsTest {
    @Test
    fun `test calculatePricePerHashFromMicroUsd`() {
        var result = HashCalculationUtils.getPricePerHashFromMicroUsd(
            4800000000L,
            300000000000000
        )
        Assertions.assertEquals(BigDecimal("0.016"), result, "Price per hash calculation is incorrect")

        result = HashCalculationUtils.getPricePerHashFromMicroUsd(12345L, 0L)
        Assertions.assertEquals(BigDecimal.ZERO, result, "Should return 0.0 when volume is 0")
    }

    @Test
    fun `test calculateVolumeHash`() {
        val volumeNhash = 1000000000000L
        val result = HashCalculationUtils.calculateVolumeHash(volumeNhash)
        val expected = 1000.0.toBigDecimal().setScale(10)
        Assertions.assertEquals(expected, result, "Volume hash calculation is incorrect")
    }
}
