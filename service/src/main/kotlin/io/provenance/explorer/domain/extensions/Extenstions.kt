package io.provenance.explorer.domain.extensions

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import io.provenance.explorer.domain.Bech32
import io.provenance.explorer.domain.core.Hash
import io.provenance.explorer.domain.models.clients.CustomPubKey
import io.provenance.explorer.domain.models.clients.PubKey
import io.provenance.explorer.domain.models.clients.pb.PbTransaction
import io.provenance.explorer.domain.models.clients.pb.SigningInfo
import io.provenance.explorer.domain.models.clients.pb.TxAuthInfoSigner
import io.provenance.explorer.domain.models.clients.tendermint.BlockMeta
import io.provenance.explorer.jackson.AccountModule
import io.provenance.explorer.jackson.TxMessageModule
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.util.encoders.Hex
import org.joda.time.DateTime
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Base64

fun String.dayPart() = this.substring(0, 10)

fun String.fromBase64() = Base64.getDecoder().decode(this)

//PubKeySecp256k1
fun String.pubKeyToBech32(hrpPrefix: String) = let {
    val base64 = this.fromBase64()
    require(base64.size == 33) { "Invalid Base 64 pub key byte length must be 33 not ${base64.size}" }
    require(base64[0] == 0x02.toByte() || base64[0] == 0x03.toByte()) { "Invalid first byte must be 2 or 3 not  ${base64[0]}" }
    val shah256 = base64.toSha256()
    val ripemd = shah256.toRIPEMD160()
    require(ripemd.size == 20) { "RipeMD size must be 20 not ${ripemd.size}" }
    Bech32.encode(hrpPrefix, Bech32.convertBits(ripemd, 8, 5, true))
}

//PubKeyEd25519
fun String.edPubKeyToBech32(hrpPrefix: String) = this.edPubKeyToAddress().addressToBech32(hrpPrefix)

fun String.edPubKeyToAddress() = let {
    val base64 = this.fromBase64()
    require(base64.size == 32) { "Invalid Base 64 pub key byte length must be 32 not ${base64.size}" }
    val truncated = base64.toSha256().copyOfRange(0, 20)
    Hex.toHexString(truncated)
}

fun ByteArray.toSha256() = Hash.sha256(this)

fun ByteArray.toRIPEMD160() = RIPEMD160Digest().let {
    it.update(this, 0, this.size)
    val buffer = ByteArray(it.getDigestSize())
    it.doFinal(buffer, 0)
    buffer
}

fun String.addressToBech32(hrpPrefix: String) = let {
    val bytes = Hex.decode(this)
    Bech32.encode(hrpPrefix, Bech32.convertBits(bytes, 8, 5, true))
}

fun BlockMeta.height() = this.header.height.toInt()

fun BlockMeta.day() = this.header.time.dayPart()

fun PbTransaction.type() = this.logs?.flatMap { it.events }
    ?.firstOrNull { it.type == "message" }
    ?.attributes
    ?.firstOrNull { it.key == "action" }
    ?.value


fun List<TxAuthInfoSigner>.signatureKey() = if (this.isNotEmpty()) this[0].publicKey.key else null

fun PbTransaction.sendMsg() = this.tx.value.msg[0].value

fun PbTransaction.fee(minGasPrice: BigDecimal) = this.gasUsed.toBigDecimal().multiply(minGasPrice).setScale(2, RoundingMode.CEILING)

fun SigningInfo.uptime(currentHeight: Int) = let {
    val startHeight = this.startHeight.toInt()
    val missingBlockCounter = this.missedBlocksCounter.toInt()
    BigDecimal(currentHeight - startHeight - missingBlockCounter)
            .divide(BigDecimal(currentHeight - startHeight), 2, RoundingMode.CEILING)
            .multiply(BigDecimal(100.00))

}

fun Long.isPastDue(currentMillis: Long) = DateTime.now().millis - this > currentMillis

// translates page (this) to offset
fun Int.toOffset(count: Int) = (this - 1) * count

fun CustomPubKey.toPubKey() = PubKey(this.type, this.key)


/**
 * ObjectMapper extension for getting the ObjectMapper configured
 * Attach to Spring Boot via @Bean and @Primary:
 *  @Primary
 *  @Bean
 *  fun mapper(): ObjectMapper = ObjectMapper().configureFigure()
 */
fun ObjectMapper.configureProvenance(): ObjectMapper = registerKotlinModule()
    .registerModule(JavaTimeModule())
    .registerModule(ProtobufModule())
    .registerModule(AccountModule())
    .registerModule(TxMessageModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
