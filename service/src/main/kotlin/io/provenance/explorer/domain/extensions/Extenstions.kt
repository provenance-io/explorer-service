package io.provenance.explorer.domain.extensions

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.common.hash.Hashing
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import cosmos.base.abci.v1beta1.Abci
import cosmos.staking.v1beta1.Staking
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.Bech32
import io.provenance.explorer.domain.core.toBech32Data
import io.provenance.explorer.domain.core.toMAddress
import io.provenance.explorer.domain.entities.MissedBlocksRecord
import io.provenance.explorer.domain.models.explorer.Addresses
import org.apache.commons.text.StringEscapeUtils
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

fun ByteArray.toByteString() = ByteString.copyFrom(this)
fun ByteString.toBase64() = Base64.getEncoder().encodeToString(this.toByteArray())
fun String.fromBase64() = Base64.getDecoder().decode(this).decodeToString()
fun String.fromBase64ToMAddress() = Base64.getDecoder().decode(this).toByteString().toMAddress()
fun String.toBase64() = Base64.getEncoder().encodeToString(this.toByteArray())
fun ByteArray.base64EncodeString(): String = String(Base64.getEncoder().encode(this))
fun ByteString.toDbHash() = Hashing.sha512().hashBytes(this.toByteArray()).asBytes().base64EncodeString()
fun ByteString.toHash() = this.toByteArray().toBech32Data().hexData

fun Abci.TxResponse.type() = this.logsList?.flatMap { it.eventsList }
    ?.firstOrNull { it.type == "message" }
    ?.attributesList
    ?.firstOrNull { it.key == "action" }
    ?.value

fun String.validatorUptime(bondingHeight: BigInteger, currentHeight: BigInteger) =
    (MissedBlocksRecord.findLatestForVal(this)?.totalCount ?: 0).let {
        BigDecimal(currentHeight - bondingHeight - it.toBigInteger())
            .divide(BigDecimal(currentHeight - bondingHeight), 2, RoundingMode.CEILING)
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

fun String.isAddressAsType(type: String) = this.toBech32Data().hrp == type

fun String.translateAddress(props: ExplorerProperties) = this.toBech32Data().let {
    Addresses(
        it.hexData,
        Bech32.encode(props.provAccPrefix(), it.data),
        Bech32.encode(props.provValOperPrefix(), it.data),
        Bech32.encode(props.provValConsPrefix(), it.data)
    )
}

fun ByteString.translateByteArray(props: ExplorerProperties) = this.toByteArray().toBech32Data().let {
    Addresses(
        it.hexData,
        Bech32.encode(props.provAccPrefix(), it.data),
        Bech32.encode(props.provValOperPrefix(), it.data),
        Bech32.encode(props.provValConsPrefix(), it.data)
    )
}

fun Staking.Validator.getStatusString() =
    when {
        this.jailed -> "jailed"
        this.status == Staking.BondStatus.BOND_STATUS_BONDED -> "active"
        else -> "candidate"
    }

fun Staking.Validator.isActive() = this.status == Staking.BondStatus.BOND_STATUS_BONDED && !this.jailed

fun Timestamp.toDateTime() = DateTime(Instant.ofEpochSecond(this.seconds, this.nanos.toLong()).toEpochMilli())
fun Timestamp.formattedString() = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(this.seconds, this.nanos.toLong()))
fun DateTime.startOfDay() = this.withZone(DateTimeZone.UTC).withTimeAtStartOfDay()
fun String.toDateTime() = DateTime.parse(this)

fun BlockOuterClass.Block.height() = this.header.height.toInt()

fun String.toObjectNode() = OBJECT_MAPPER.readValue(StringEscapeUtils.unescapeJson(this), ObjectNode::class.java)

// this == gas_limit
fun TxOuterClass.Fee.getMinGasFee() =
    (this.amountList.firstOrNull()?.amount?.toBigInteger() ?: 0).toDouble().div(this.gasLimit.toDouble())

fun List<BigDecimal>.average() = this.fold(BigDecimal.ZERO, BigDecimal::add)
    .divide(this.size.toBigDecimal(), 3, RoundingMode.CEILING)

/**
 * ObjectMapper extension for getting the ObjectMapper configured
 * Attach to
 * Spring Boot via @Bean and @Primary:
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
