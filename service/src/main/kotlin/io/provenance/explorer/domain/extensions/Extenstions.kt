package io.provenance.explorer.domain.extensions

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.protobuf.ByteString
import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import com.google.protobuf.util.JsonFormat
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import cosmos.bank.v1beta1.Tx
import cosmos.base.abci.v1beta1.Abci
import cosmos.slashing.v1beta1.Slashing
import cosmos.staking.v1beta1.Staking
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.Bech32
import io.provenance.explorer.domain.core.Hash
import io.provenance.explorer.domain.core.toBech32Data
import io.provenance.explorer.domain.entities.SignatureRecord
import io.provenance.explorer.domain.models.explorer.Addresses
import io.provenance.explorer.domain.models.explorer.Signatures
import io.provenance.explorer.grpc.extensions.toAddress
import io.provenance.explorer.grpc.extensions.toMultiSig
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import tendermint.types.BlockOuterClass
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlin.math.ceil

fun ByteString.toValue() = Base64.getEncoder().encodeToString(this.toByteArray())
fun ByteString.toHash() = this.toByteArray().toBech32Data().hexData

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

fun Slashing.ValidatorSigningInfo.uptime(currentHeight: BigInteger) = let {
    val startHeight = this.startHeight.toBigInteger()
    val missingBlockCounter = this.missedBlocksCounter.toBigInteger()
    BigDecimal(currentHeight - startHeight - missingBlockCounter)
            .divide(BigDecimal(currentHeight - startHeight), 2, RoundingMode.CEILING)
            .multiply(BigDecimal(100.00))

}

fun Long.isPastDue(currentMillis: Long) = DateTime.now().millis - this > currentMillis

// translates page (this) to offset
fun Int.toOffset(count: Int) = (this - 1) * count

fun <T> List<T>.pageOfResults(page: Int, count: Int): List<T> {
    val fromIndex = page.toOffset(count)
    if (fromIndex > this.lastIndex)
        return listOf<T>()

    var toIndex = page.toOffset(count) + count
    if (toIndex > this.lastIndex)
        toIndex = this.lastIndex + 1

    return this.subList(fromIndex, toIndex)
}

// Total # of results divided by count per page
fun Long.pageCountOfResults(count: Int): Int =
    if (this < count) 1
    else ceil(this.toDouble() / count).toInt()

fun BigInteger.pageCountOfResults(count: Int): Int =
    if (this < count.toBigInteger()) 1
    else ceil(this.toDouble() / count).toInt()

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
        this.status == Staking.BondStatus.BOND_STATUS_BONDED -> "active"
        else -> "candidate"
    }

fun Timestamp.toDateTime() = DateTime(Instant.ofEpochSecond( this.seconds, this.nanos.toLong()).toEpochMilli())

fun Timestamp.formattedString() = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond( this.seconds, this.nanos.toLong()))

fun DateTime.startOfDay() = this.withZone(DateTimeZone.UTC).withTimeAtStartOfDay()

fun BlockOuterClass.Block.height() = this.header.height.toInt()

fun List<SignatureRecord>.toSigObj(hrpPrefix: String) =
    if (this.isNotEmpty())
        Signatures(
            this.map { rec -> rec.pubkeyObject.toAddress(hrpPrefix) ?: rec.base64Sig },
            this.first().multiSigObject?.toMultiSig()?.threshold
        )
    else Signatures(listOf(), null)

fun String.toScaledDecimal(scale: Int) = BigDecimal(this.toBigInteger(), scale)

fun Message.toObjectNode(protoPrinter: JsonFormat.Printer) =
    OBJECT_MAPPER.readValue(protoPrinter.print(this), ObjectNode::class.java)
        .let { node ->
            node.remove("@type")
            node
        }

// this == gas_limit
fun TxOuterClass.Fee.getMinGasFee() = this.amountList.first().amount.toInt().div(this.gasLimit.toDouble())

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
