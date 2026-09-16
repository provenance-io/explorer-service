package io.provenance.explorer.domain.extensions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScopeNavSnapshotKtTest {

    @Test
    fun `prefers usd millidollar NAV when multiple denoms are present`() {
        val usd = ChainScopeNav(
            priceAmount = 27_234_000_000_000L,
            priceDenom = "usd",
            volume = 1L,
            updatedBlockHeight = 100
        )
        val trading = ChainScopeNav(
            priceAmount = 99L,
            priceDenom = "uusd.trading",
            volume = 1L,
            updatedBlockHeight = 101
        )
        val other = ChainScopeNav(
            priceAmount = 50L,
            priceDenom = "nhash",
            volume = 1L,
            updatedBlockHeight = 102
        )

        assertEquals(usd, pickPreferredScopeNav(listOf(trading, other, usd)))
    }

    @Test
    fun `falls back to other usd-equivalent denoms when usd is missing`() {
        val trading = ChainScopeNav(
            priceAmount = 1_234_567L,
            priceDenom = "uusd.trading",
            volume = 1L,
            updatedBlockHeight = 50
        )
        val other = ChainScopeNav(
            priceAmount = 7L,
            priceDenom = "nhash",
            volume = 1L,
            updatedBlockHeight = 51
        )

        assertEquals(trading, pickPreferredScopeNav(listOf(other, trading)))
    }

    @Test
    fun `returns null when the list is empty or has no usd-equivalent denom`() {
        assertNull(pickPreferredScopeNav(emptyList()))
        assertNull(
            pickPreferredScopeNav(
                listOf(
                    ChainScopeNav(
                        priceAmount = 100L,
                        priceDenom = "nhash",
                        volume = 1L,
                        updatedBlockHeight = 1
                    )
                )
            )
        )
    }
}
