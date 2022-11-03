package io.provenance.explorer.domain

import com.google.protobuf.Any
import io.provenance.explorer.domain.extensions.sigToAddress
import io.provenance.explorer.domain.extensions.toByteString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.Base64

class ExtensionsTest {

    @Test
    @Tag("junit-jupiter")
    fun `should return bech32 address from given secp256k1 pubkey proto`() {
        val bech32address = "tp102789pey8hyd25pzvxhxn8k62nrffcjtng8kef"
        val key = Base64.getDecoder().decode("Ar2rEV/s88aWyU8CMX+ipwL7/XNI5Z6gexrWv6n1Dxx6")
        val proto = cosmos.crypto.secp256k1.Keys.PubKey.newBuilder().setKey(key.toByteString()).build()
        val any = Any.pack(proto, "")
        assertEquals(bech32address, any.sigToAddress("tp"))
    }

    @Test
    @Tag("junit-jupiter")
    fun `should return bech32 address from given secp256r1 pubkey proto`() {
        val bech32address = "cosmos1g95vqmg5fdlgm0ccyrwd0wvm6axgwkcrn5n0pl3llptr6qz8aayqtzqj47"
        val key = Base64.getDecoder().decode("Angqd50SIljag/sk8T+2eo/7vLyAvodInF8E3SKDdnmN")
        val proto = cosmos.crypto.secp256r1.Keys.PubKey.newBuilder().setKey(key.toByteString()).build()
        val any = Any.pack(proto, "")
        assertEquals(bech32address, any.sigToAddress("cosmos"))
    }
}
