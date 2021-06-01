package io.provenance.explorer.domain

import io.provenance.explorer.grpc.extensions.getEscrowAccountAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class ExtensionsTest {

    @Test
    @Tag("junit-jupiter")
    fun `should return escrow account address with given portId channelId and prefix`() {
        val result = getEscrowAccountAddress("transfer", "channel", "cosmos")
        assertEquals("cosmos1dm7fargcm8km25nxe6xldj0y0j2dawg8h5s03l", result)
    }
}
