package io.provenance.explorer.domain.extensions

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import cosmos.bank.v1beta1.Tx
import cosmos.base.abci.v1beta1.Abci
import cosmos.slashing.v1beta1.Slashing
import cosmos.staking.v1beta1.Staking
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.Bech32
import io.provenance.explorer.domain.core.Hash
import io.provenance.explorer.domain.core.toBech32Data
import io.provenance.explorer.domain.models.explorer.Addresses
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.joda.time.DateTime
import tendermint.types.BlockOuterClass
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64

fun ByteString.toValue() = Base64.getEncoder().encodeToString(this.toByteArray())

//PubKeySecp256k1
fun ByteString.secpPubKeyToBech32(hrpPrefix: String) = let {
    val base64 = this.toByteArray()
    require(base64.size == 33) { "Invalid Base 64 pub key byte length must be 33 not ${base64.size}" }
    require(base64[0] == 0x02.toByte() || base64[0] == 0x03.toByte()) { "Invalid first byte must be 2 or 3 not  ${base64[0]}" }
    val shah256 = base64.toSha256()
    val ripemd = shah256.toRIPEMD160()
    require(ripemd.size == 20) { "RipeMD size must be 20 not ${ripemd.size}" }
    Bech32.encode(hrpPrefix, Bech32.convertBits(ripemd, 8, 5, true))
}

//PubKeyEd25519
// Used by validators to create keys
fun ByteString.edPubKeyToBech32(hrpPrefix: String) = let {
    val base64 = this.toByteArray()
    require(base64.size == 32) { "Invalid Base 64 pub key byte length must be 32 not ${base64.size}" }
    val truncated = base64.toSha256().copyOfRange(0, 20)
    Bech32.encode(hrpPrefix, Bech32.convertBits(truncated, 8, 5, true))
}

fun ByteArray.toSha256() = Hash.sha256(this)

fun ByteArray.toRIPEMD160() = RIPEMD160Digest().let {
    it.update(this, 0, this.size)
    val buffer = ByteArray(it.digestSize)
    it.doFinal(buffer, 0)
    buffer
}


fun Abci.TxResponse.type() = this.logsList?.flatMap { it.eventsList }
    ?.firstOrNull { it.type == "message" }
    ?.attributesList
    ?.firstOrNull { it.key == "action" }
    ?.value


fun List<TxOuterClass.SignerInfo>.signatureKey() =
    if (this.isNotEmpty()) this[0].publicKey else null

fun ServiceOuterClass.GetTxResponse.sendMsg() =
    this.tx.body.messagesList.first { it.typeUrl.contains("MsgSend") }.unpack(Tx.MsgSend::class.java)

fun Abci.TxResponse.fee(minGasPrice: BigDecimal) = this.gasUsed.toBigDecimal().multiply(minGasPrice)
    .setScale(2, RoundingMode.CEILING)

fun Slashing.ValidatorSigningInfo.uptime(currentHeight: Int) = let {
    val startHeight = this.startHeight.toInt()
    val missingBlockCounter = this.missedBlocksCounter.toInt()
    BigDecimal(currentHeight - startHeight - missingBlockCounter)
            .divide(BigDecimal(currentHeight - startHeight), 2, RoundingMode.CEILING)
            .multiply(BigDecimal(100.00))

}

fun Long.isPastDue(currentMillis: Long) = DateTime.now().millis - this > currentMillis

// translates page (this) to offset
fun Int.toOffset(count: Int) = (this - 1) * count

fun String.translateAddress(props: ExplorerProperties) = this.toBech32Data().let {
    Addresses(
        it.hexData,
        Bech32.encode(props.provAccPrefix(), it.fiveBitData),
        Bech32.encode(props.provValOperPrefix(), it.fiveBitData),
        Bech32.encode(props.provValConsPrefix(), it.fiveBitData),
    )
}

fun ByteString.translateByteArray(props: ExplorerProperties) = this.toByteArray().toBech32Data().let {
    Addresses(
        it.hexData,
        Bech32.encode(props.provAccPrefix(), it.fiveBitData),
        Bech32.encode(props.provValOperPrefix(), it.fiveBitData),
        Bech32.encode(props.provValConsPrefix(), it.fiveBitData),
    )
}

fun Staking.Validator.getStatusString() =
    when {
        this.jailed -> "jailed"
        this.status in listOf(
            Staking.BondStatus.BOND_STATUS_BONDED,
            Staking.BondStatus.BOND_STATUS_UNBONDING) -> "active"
        else -> "candidate"
    }

fun Timestamp.toDateTime() = DateTime(Instant.ofEpochSecond( this.seconds, this.nanos.toLong()).toEpochMilli())

fun Timestamp.formattedString() = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond( this.seconds, this.nanos.toLong()))

fun BlockOuterClass.Block.height() = this.header.height.toInt()

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
