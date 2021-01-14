package io.provenance.explorer.domain.extensions

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import io.provenance.explorer.domain.Bech32
import io.provenance.explorer.domain.BlockMeta
import io.provenance.explorer.domain.BlockResponse
import io.provenance.explorer.domain.PbTransaction
import io.provenance.explorer.domain.SigningInfo
import io.provenance.explorer.domain.TxResult
import io.provenance.explorer.domain.core.Hash
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.util.encoders.Hex
import org.joda.time.DateTime
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64

fun String.dayPart() = this.substring(0, 10)

fun String.fromBase64() = Base64.getDecoder().decode(this)

fun String.pubKeyToBech32(hrpPrefix: String) = let {
    val base64 = this.fromBase64()
    require(base64.size == 33) { "Invalid Base 64 pub key byte length must be 33 not ${base64.size}" }
    require(base64[0] == 0x02.toByte() || base64[0] == 0x03.toByte()) { "Invalid first byte must be 2 or 3 not  ${base64[0]}" }
    val shah256 = base64.toSha256()
    val ripemd = shah256.toRIPEMD160()
    require(ripemd.size == 20) { "RipeMD size must be 20 not ${ripemd.size}" }
    Bech32.encode(hrpPrefix, Bech32.convertBits(ripemd, 8, 5, true))
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

fun PbTransaction.type() = if(this.logs!= null) this.logs?.flatMap { it.events }?.firstOrNull { it.type == "message" }?.attributes?.firstOrNull { it.key == "action" }?.value else null

fun PbTransaction.feePayer() = this.tx.value.signatures[0]

fun PbTransaction.fee(minGasPrice: BigDecimal) = this.gasUsed.toBigDecimal().multiply(minGasPrice).setScale(2, RoundingMode.CEILING)

fun BlockResponse.height() = this.block.header.height.toInt()

fun SigningInfo.uptime(currentHeight: Int) = let {
    BigDecimal(currentHeight - this.startHeight.toInt() - this.missedBlocksCounter.toInt())
            .divide(BigDecimal(currentHeight - this.startHeight.toInt()), 2, RoundingMode.CEILING)
            .multiply(BigDecimal(100.00))

}

fun Long.isPastDue(currentMillis: Long) = DateTime.now().millis - this > currentMillis

// Json Extensions
private object jackson {
    val om = ObjectMapper()
        .registerModules(ProtobufModule(), JavaTimeModule())
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
}

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
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
