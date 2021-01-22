package io.provenance.explorer.domain

import io.provenance.explorer.domain.extensions.addressToBech32
import io.provenance.explorer.domain.extensions.dayPart
import io.provenance.explorer.domain.extensions.edPubKeyToBech32
import io.provenance.explorer.domain.extensions.pubKeyToBech32
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ExtensionsTest {

    @Test
    fun `should return day part of time string`() {
        assertEquals("2019-04-22", "2019-04-22T17:01:51.701356223Z".dayPart())
    }

    @Test
    fun `should convert pub key string to bech32`() {
        val result = "Al+JRNPlfbtGwfU7IybTJyzY1kM19ajg3LEUBEasttBe".pubKeyToBech32(Bech32.PROVENANCE_TESTNET_PREFIX)
        assertEquals("tp14neg0whj7puhwks6536a8lqp7msvd9p04wu287", result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `should throw illegal argument exception for base 64 size for pub key to bech32 extension`() {
        "BAleJRNPlfbtGwfU7IybTJyzY1kM19ajg3LEUBEasttBe".pubKeyToBech32(Bech32.PROVENANCE_TESTNET_PREFIX)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `should throw illegal argument exception for base 64 not having a 2 or 3 as first byte for bech32 extension`() {
        "00000000000000000000000000000000000000000000".pubKeyToBech32(Bech32.PROVENANCE_TESTNET_PREFIX)
    }

    @Test
    fun `should return a bech32 address from a hex validator consensus address`() {
        val result = "685EDA8451FE32FCD9C58A7EA5299B6E66400EA2".addressToBech32(Bech32.PROVENANCE_TESTNET_CONSENSUS_ACCOUNT_PREFIX)
        assertEquals("tpvalcons1dp0d4pz3lce0ekw93fl222vmdenyqr4zlxa7re", result)
    }

    @Test
    fun `should return a bech32 address from a PubKeyEd25519 pub key validator consensus address`() {
        val result = "GX1/l9SIO/QZsy6oBKg8rBkKAZ51wOt1IBaD4amSLGg=".edPubKeyToBech32(Bech32.PROVENANCE_MAINNET_CONSENSUS_ACCOUNT_PREFIX)
        assertEquals("pbvalcons196s9wzks8q4hqc3fnx8crmt9cnlfr24ra6l44f", result)
    }
}
