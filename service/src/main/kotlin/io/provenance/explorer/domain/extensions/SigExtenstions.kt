package io.provenance.explorer.domain.extensions

import com.google.protobuf.Any
import com.google.protobuf.ByteString
import cosmos.crypto.multisig.Keys.LegacyAminoPubKey
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.ExplorerProperties.Companion.PROV_ACC_PREFIX
import io.provenance.explorer.domain.core.Bech32
import io.provenance.explorer.domain.core.Hash.sha256
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.toBech32Data
import io.provenance.explorer.domain.entities.SignatureRecord
import io.provenance.explorer.domain.models.explorer.ED_25519
import io.provenance.explorer.domain.models.explorer.LEGACY_MULTISIG
import io.provenance.explorer.domain.models.explorer.SECP_256_K1
import io.provenance.explorer.domain.models.explorer.SECP_256_R1
import io.provenance.explorer.grpc.extensions.mapTxEventAttrValues
import io.provenance.explorer.grpc.extensions.removeFirstSlash
import org.bouncycastle.crypto.digests.RIPEMD160Digest

// PubKeySecp256k1
fun ByteString.secp256k1PubKeyToBech32(hrpPrefix: String) = let {
    val base64 = this.toByteArray()
    require(base64.size == 33) { "Invalid Base 64 pub key byte length must be 33 not ${base64.size}" }
    require(base64[0] == 0x02.toByte() || base64[0] == 0x03.toByte()) { "Invalid first byte must be 2 or 3 not  ${base64[0]}" }
    val shah256 = base64.toSha256()
    val ripemd = shah256.toRIPEMD160()
    require(ripemd.size == 20) { "RipeMD size must be 20 not ${ripemd.size}" }
    Bech32.encode(hrpPrefix, ripemd)
}

// PubKeySecp256r1
fun ByteString.secp256r1PubKeyToBech32(hrpPrefix: String, protoType: String) = let {
    val protoSha = protoType.toByteArray().toSha256()
    val key = protoSha + this.toByteArray()
    val keySha = key.toSha256()
    Bech32.encode(hrpPrefix, keySha)
}

// PubKeyEd25519
// Used by validators to create keys
fun ByteString.edPubKeyToBech32(hrpPrefix: String) = let {
    val base64 = this.toByteArray()
    require(base64.size == 32) { "Invalid Base 64 pub key byte length must be 32 not ${base64.size}" }
    base64.toSha256().copyOfRange(0, 20).toBech32Data(hrpPrefix).address
}

// LegacyAminoPubKey
// Used as a multisig key - rarely used
fun ByteArray.legacyMultisigPubKeyToBech32(hrpPrefix: String) = let {
    this.toSha256().copyOfRange(0, 20).toBech32Data(hrpPrefix).address
}

fun ByteArray.toSha256() = sha256(this)

fun ByteArray.toRIPEMD160() = RIPEMD160Digest().let {
    it.update(this, 0, this.size)
    val buffer = ByteArray(it.digestSize)
    it.doFinal(buffer, 0)
    buffer
}

// PubKey Extensions
fun Any.sigToAddress(hrpPrefix: String) =
    when {
        typeUrl.contains(SECP_256_K1) ->
            this.unpack(cosmos.crypto.secp256k1.Keys.PubKey::class.java).key.secp256k1PubKeyToBech32(hrpPrefix)
        typeUrl.contains(SECP_256_R1) ->
            this.unpack(cosmos.crypto.secp256r1.Keys.PubKey::class.java).key
                .secp256r1PubKeyToBech32(hrpPrefix, typeUrl.removeFirstSlash())
        typeUrl.contains(ED_25519) ->
            this.unpack(cosmos.crypto.ed25519.Keys.PubKey::class.java).key.edPubKeyToBech32(hrpPrefix)
        typeUrl.contains(LEGACY_MULTISIG) ->
            this.unpack(LegacyAminoPubKey::class.java).toByteArray().legacyMultisigPubKeyToBech32(hrpPrefix)
        else -> null.also { logger().error("This typeUrl is not supported as a pubkey type: $typeUrl") }
    }

fun String.typeToLabel() =
    when {
        this.contains(SECP_256_K1) -> SECP_256_K1
        this.contains(SECP_256_R1) -> SECP_256_R1
        this.contains(ED_25519) -> ED_25519
        this.contains(LEGACY_MULTISIG) -> LEGACY_MULTISIG
        else -> "Not discernible".also { logger().error("This typeUrl is not supported as a pubkey type: $this") }
    }

fun Any.sigToBase64(): String =
    when {
        typeUrl.contains(SECP_256_K1) -> this.unpack(cosmos.crypto.secp256k1.Keys.PubKey::class.java).key.toBase64()
        typeUrl.contains(SECP_256_R1) -> this.unpack(cosmos.crypto.secp256r1.Keys.PubKey::class.java).key.toBase64()
        typeUrl.contains(ED_25519) -> this.unpack(cosmos.crypto.ed25519.Keys.PubKey::class.java).key.toBase64()
        else -> "".also { logger().debug("This typeUrl is not supported in single sig: $typeUrl") }
    }

fun ServiceOuterClass.GetTxResponse.getFirstSigner() =
    this.tx.authInfo.signerInfosList.firstOrNull()?.publicKey?.getAddress(PROV_ACC_PREFIX)
        ?: this.mapTxEventAttrValues(TX_EVENT, TX_ACC_SEQ)[0]!!.getAddrFromAccSeq()

fun Any.getAddress(hrpPrefix: String) =
    SignatureRecord.findByPubkeyObject(this, this.typeUrl)?.address
        ?: this.sigToAddress(hrpPrefix)!!

const val TX_EVENT = "tx"
const val TX_ACC_SEQ = "acc_seq"

fun String.getAddrFromAccSeq() = this.split("/")[0]
