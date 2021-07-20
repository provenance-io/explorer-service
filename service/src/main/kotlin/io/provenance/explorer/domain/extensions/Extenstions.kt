package io.provenance.explorer.domain.extensions

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.common.hash.Hashing
import com.google.protobuf.ByteString
import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import com.google.protobuf.util.JsonFormat
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import cosmos.base.abci.v1beta1.Abci
import cosmos.staking.v1beta1.Staking
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.explorer.JSON_NODE_FACTORY
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.Bech32
import io.provenance.explorer.domain.core.Hash
import io.provenance.explorer.domain.core.toBech32Data
import io.provenance.explorer.domain.core.toMAddress
import io.provenance.explorer.domain.entities.MissedBlocksRecord
import io.provenance.explorer.domain.entities.SignatureRecord
import io.provenance.explorer.domain.models.explorer.Addresses
import io.provenance.explorer.domain.models.explorer.Signatures
import io.provenance.explorer.grpc.extensions.toAddress
import io.provenance.explorer.grpc.extensions.toMultiSig
import org.apache.commons.text.StringEscapeUtils
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

fun ByteArray.toByteString() = ByteString.copyFrom(this)
fun ByteString.toBase64() = Base64.getEncoder().encodeToString(this.toByteArray())
fun String.fromBase64() = Base64.getDecoder().decode(this).decodeToString()
fun String.fromBase64ToMAddress() = Base64.getDecoder().decode(this).toByteString().toMAddress()
fun String.toBase64() = Base64.getEncoder().encodeToString(this.toByteArray())
fun ByteString.toDbHash() = Hashing.sha512().hashBytes(this.toByteArray()).asBytes().toString()
fun ByteString.toHash() = this.toByteArray().toBech32Data().hexData

// PubKeySecp256k1
fun ByteString.secpPubKeyToBech32(hrpPrefix: String) = let {
    val base64 = this.toByteArray()
    require(base64.size == 33) { "Invalid Base 64 pub key byte length must be 33 not ${base64.size}" }
    require(base64[0] == 0x02.toByte() || base64[0] == 0x03.toByte()) { "Invalid first byte must be 2 or 3 not  ${base64[0]}" }
    val shah256 = base64.toSha256()
    val ripemd = shah256.toRIPEMD160()
    require(ripemd.size == 20) { "RipeMD size must be 20 not ${ripemd.size}" }
    Bech32.encode(hrpPrefix, ripemd)
}

// PubKeyEd25519
// Used by validators to create keys
fun ByteString.edPubKeyToBech32(hrpPrefix: String) = let {
    val base64 = this.toByteArray()
    require(base64.size == 32) { "Invalid Base 64 pub key byte length must be 32 not ${base64.size}" }
    base64.toSha256().copyOfRange(0, 20).toBech32Data(hrpPrefix).address
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

fun List<SignatureRecord>.toSigObj(hrpPrefix: String) =
    if (this.isNotEmpty())
        Signatures(
            this.map { rec -> rec.pubkeyObject.toAddress(hrpPrefix) ?: rec.base64Sig },
            this.first().multiSigObject?.toMultiSig()?.threshold
        )
    else Signatures(listOf(), null)

val protoTypesToCheck = arrayOf(
    "/provenance.metadata.v1.MsgWriteScopeRequest",
    "/provenance.metadata.v1.MsgDeleteScopeRequest",
    "/provenance.metadata.v1.MsgWriteRecordSpecificationRequest",
    "/provenance.metadata.v1.MsgDeleteRecordSpecificationRequest",
    "/provenance.metadata.v1.MsgWriteScopeSpecificationRequest",
    "/provenance.metadata.v1.MsgDeleteScopeSpecificationRequest",
    "/provenance.metadata.v1.MsgWriteContractSpecificationRequest",
    "/provenance.metadata.v1.MsgDeleteContractSpecificationRequest",
    "/provenance.metadata.v1.MsgAddScopeDataAccessRequest",
    "/provenance.metadata.v1.MsgDeleteScopeDataAccessRequest",
    "/provenance.metadata.v1.MsgAddScopeOwnerRequest",
    "/provenance.metadata.v1.MsgDeleteScopeOwnerRequest",
    "/provenance.metadata.v1.MsgWriteSessionRequest",
    "/provenance.metadata.v1.MsgWriteRecordRequest",
    "/provenance.metadata.v1.MsgDeleteRecordRequest",
    "/provenance.metadata.v1.MsgAddContractSpecToScopeSpecRequest",
    "/provenance.metadata.v1.MsgDeleteContractSpecFromScopeSpecRequest",
)

val protoTypesFieldsToCheck = arrayOf(
    "scopeId",
    "specificationId",
    "recordId",
    "sessionId",
    "contractSpecificationId",
    "scopeSpecificationId",
)

fun Message.toObjectNode(protoPrinter: JsonFormat.Printer) =
    OBJECT_MAPPER.readValue(protoPrinter.print(this), ObjectNode::class.java)
        .let { node ->
            val protoType = node.get("@type").asText()

            // Bug: Tx Msgs being printed treat ByteString as Base64 encoded #145
            if (protoTypesToCheck.contains(protoType)) {
                protoTypesFieldsToCheck.forEach { fromBase64ToMAddress(node, it) }
            }

            node.remove("@type")
            node
        }

fun fromBase64ToMAddress(jsonNode: JsonNode, fieldName: String) {
    var found = false

    if (jsonNode.has(fieldName)) {
        val newValue = jsonNode.get(fieldName).asText().fromBase64ToMAddress().toString()
        (jsonNode as ObjectNode).replace(fieldName, JSON_NODE_FACTORY.textNode(newValue))
        found = true // stop after first find
    }

    if (!found) {
        jsonNode.forEach { fromBase64ToMAddress(it, fieldName) }
    }
}

fun String.toObjectNode() = OBJECT_MAPPER.readValue(StringEscapeUtils.unescapeJson(this), ObjectNode::class.java)

// this == gas_limit
fun TxOuterClass.Fee.getMinGasFee() =
    (this.amountList.firstOrNull()?.amount?.toBigInteger() ?: 0).toDouble().div(this.gasLimit.toDouble())

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
