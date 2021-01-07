package io.provenance.explorer.domain

import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.util.encoders.Hex
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.jvmName

fun String.dayPart() = this.substring(0, 10)

fun String.asDay() = LocalDate.parse(this.dayPart(), DateTimeFormatter.ISO_DATE)

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

fun List<BlockMeta>.maxHeight() = this.sortedByDescending { it.header.height.toInt() }.first().height()

fun List<BlockMeta>.minHeight() = this.sortedByDescending { it.header.height.toInt() }.last().height()

fun PbTransaction.type() = if(this.logs!= null) this.logs?.flatMap { it.events }?.firstOrNull { it.type == "message" }?.attributes?.firstOrNull { it.key == "action" }?.value else null

fun PbTransaction.feePayer() = this.tx.value.signatures[0]

fun TxResult.fee(minGasPrice: BigDecimal) = this.gasUsed.toBigDecimal().multiply(minGasPrice).setScale(2, RoundingMode.CEILING)

fun PbTransaction.fee(minGasPrice: BigDecimal) = this.gasUsed.toBigDecimal().multiply(minGasPrice).setScale(2, RoundingMode.CEILING)

fun BlockResponse.height() = this.block.header.height.toInt()

fun SigningInfo.uptime(currentHeight: Int) = let {
    BigDecimal(currentHeight - this.startHeight.toInt() - this.missedBlocksCounter.toInt())
            .divide(BigDecimal(currentHeight - this.startHeight.toInt())).setScale(2, RoundingMode.CEILING)
            .multiply(BigDecimal(100.00))

}

// Logger Extensions
fun logger(name: String = pkgName()): Logger = LoggerFactory.getLogger(name)
inline fun <reified T : Any> logger(clazz: KClass<T>): Logger = logger(clazz.jvmName)
inline fun <reified T : Any> T.logger(): Logger = logger(T::class)
fun pkgName(): String = object {}::class.java.`package`.name

var Logger.level by object {
    operator fun getValue(thisRef: Logger, property: KProperty<*>): Level {
        if (thisRef is ch.qos.logback.classic.Logger) {
            return thisRef.level
        }
        throw RuntimeException("Invalid reference type ${thisRef.javaClass}")
    }

    operator fun setValue(thisRef: Logger, property: KProperty<*>, value: Level) {
        if (thisRef is ch.qos.logback.classic.Logger) {
            thisRef.level = value
            return
        }
        throw RuntimeException("Invalid reference type ${thisRef.javaClass}")
    }
}


// Json Extensions
private object jackson {
    val om = ObjectMapper()
        .registerModules(ProtobufModule(), JavaTimeModule())
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
}

private val omWrite get() = jackson.om.writer()
//fun <T> T.toJsonString() = omWrite.writeValueAsString(this)
