package io.provenance.explorer.domain.extensions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.Any
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import cosmos.base.abci.v1beta1.Abci
import io.provenance.explorer.JSON_NODE_FACTORY
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.model.base.isMAddress
import tendermint.abci.Types
import java.util.Base64

val protoTypesToCheckForMetadata = listOf(
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
    "/provenance.metadata.v1.MsgDeleteContractSpecFromScopeSpecRequest"
)

val protoTypesFieldsToCheckForMetadata = listOf(
    "scopeId",
    "specificationId",
    "recordId",
    "sessionId",
    "contractSpecificationId",
    "scopeSpecificationId",
    "contractSpecIds",
    "scopeSpecId",
    "contractSpecId",
    "recordSpecId"
)

val protoTypesToCheckForSmartContract = listOf(
    "/cosmwasm.wasm.v1beta1.MsgInstantiateContract",
    "/cosmwasm.wasm.v1beta1.MsgExecuteContract",
    "/cosmwasm.wasm.v1beta1.MsgMigrateContract",
    "/cosmwasm.wasm.v1.MsgInstantiateContract",
    "/cosmwasm.wasm.v1.MsgExecuteContract",
    "/cosmwasm.wasm.v1.MsgMigrateContract"
)

val protoTypesFieldsToCheckForSmartContract = listOf(
    "initMsg",
    "msg",
    "migrateMsg"
)

fun Message.toObjectNode(protoPrinter: JsonFormat.Printer) =
    OBJECT_MAPPER.readTree(protoPrinter.print(this))
        .let { node ->
            node.get("@type")?.asText()?.let { proto ->
                if (protoTypesToCheckForMetadata.contains(proto)) {
                    protoTypesFieldsToCheckForMetadata.forEach { fromBase64ToMAddress(node, it) }
                }
                if (protoTypesToCheckForSmartContract.contains(proto)) {
                    protoTypesFieldsToCheckForSmartContract.forEach { fromBase64ToObject(node, it) }
                }
            }
            (node as ObjectNode).remove("@type")
            node
        }

fun Message.toObjectNodeNonTxMsg(protoPrinter: JsonFormat.Printer, fieldNames: List<String>) =
    OBJECT_MAPPER.readTree(protoPrinter.print(this))
        .let { node ->
            fieldNames.forEach { fromBase64ToObject(node, it) }
            node
        }

fun Message.toObjectNodePrint(protoPrinter: JsonFormat.Printer) =
    OBJECT_MAPPER.readTree(protoPrinter.preservingProtoFieldNames().print(this))

fun Message.toObjectNodeMAddressValues(protoPrinter: JsonFormat.Printer, fieldNames: List<String>) =
    OBJECT_MAPPER.readTree(protoPrinter.print(this))
        .let { node ->
            fieldNames.forEach { fromBase64ToMAddress(node, it) }
            node
        }

fun fromBase64ToObject(jsonNode: JsonNode, fieldName: String) {
    var found = false

    if (jsonNode.has(fieldName)) {
        val newValue = jsonNode.get(fieldName).asText().fromBase64()
        (jsonNode as ObjectNode).replace(fieldName, OBJECT_MAPPER.readTree(newValue))
        found = true // stop after first find
    }

    if (!found) {
        jsonNode.forEach { fromBase64ToMAddress(it, fieldName) }
    }
}

fun fromBase64ToMAddress(jsonNode: JsonNode, fieldName: String) {
    var found = false

    if (jsonNode.has(fieldName)) {
        when {
            jsonNode.get(fieldName).isTextual -> {
                val value = jsonNode.get(fieldName).asText()
                if (!value.isMAddress()) {
                    (jsonNode as ObjectNode).replace(
                        fieldName,
                        JSON_NODE_FACTORY.textNode(value.fromBase64ToMAddress().toString())
                    )
                }
            }
            jsonNode.get(fieldName).isArray -> {
                val oldArray = (jsonNode.get(fieldName) as ArrayNode)
                val newArray = JSON_NODE_FACTORY.arrayNode()
                oldArray.forEach {
                    val value = it.asText().fromBase64ToMAddress().toString()
                    newArray.add(JSON_NODE_FACTORY.textNode(value))
                }
                (jsonNode as ObjectNode).replace(fieldName, newArray)
            }
        }
        found = true
    }

    if (!found) {
        jsonNode.forEach { fromBase64ToMAddress(it, fieldName) }
    }
}

fun List<Abci.StringEvent>.msgEventsToObjectNodePrint(protoPrinter: JsonFormat.Printer) =
    this.map { OBJECT_MAPPER.readTree(protoPrinter.preservingProtoFieldNames().print(it)) }

/**
 * Safely decodes a base64-encoded string to UTF-8 text.
 * If the decoded bytes are not valid UTF-8 or contain binary data (invalid control characters),
 * returns the original base64 string.
 *
 * @param base64Text The potentially base64-encoded string
 * @return The decoded UTF-8 text if valid, otherwise the original string
 */
private fun safeDecodeBase64ToText(base64Text: String?): String? {
    if (base64Text.isNullOrEmpty()) {
        return base64Text
    }

    return try {
        val isBase64Encoded = base64Text.length % 4 == 0 &&
            base64Text.matches(Regex("^[A-Za-z0-9+/=]+\$"))
        if (isBase64Encoded) {
            val decodedBytes = Base64.getDecoder().decode(base64Text)
            val decoded = try {
                String(decodedBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                // Invalid UTF-8, keep original base64
                return base64Text
            }
            // If decoding produced the same string, it wasn't actually base64 or was binary
            if (decoded == base64Text) {
                base64Text
            } else {
                // Validate decoded result doesn't contain invalid control characters
                val hasInvalidChars = decoded.any {
                    val code = it.code
                    // Control chars < 32 except tab(9), LF(10), CR(13)
                    code < 32 && code !in listOf(9, 10, 13)
                }
                if (hasInvalidChars) {
                    // Contains binary data, keep original base64
                    base64Text
                } else {
                    // Valid UTF-8 text, use decoded value
                    decoded
                }
            }
        } else {
            // Not base64-encoded, use as-is
            base64Text
        }
    } catch (e: Exception) {
        // decoding failed, keep original
        base64Text
    }
}

fun List<Types.Event>.txEventsToObjectNodePrint(protoPrinter: JsonFormat.Printer) =
    this.map { event ->
        event.toObjectNodePrint(protoPrinter).let { node ->
            val oldArray = (node.get("attributes") as ArrayNode)
            val newArray = JSON_NODE_FACTORY.arrayNode()
            oldArray.forEach {
                val newNode = JSON_NODE_FACTORY.objectNode()
                val keyText = it.get("key").asText()
                val valueText = it.get("value")?.asText()

                // Keys and values are typically base64-encoded strings that should be decoded
                val newKey = safeDecodeBase64ToText(keyText)!!
                val newValue = safeDecodeBase64ToText(valueText)

                newNode.put("key", newKey)
                newNode.put("value", newValue)
                newArray.add(newNode)
            }
            (node as ObjectNode).replace("attributes", newArray)
            node
        }
    }

fun List<Any>.toObjectNodeList(protoPrinter: JsonFormat.Printer) =
    this.map { (it.toObjectNodePrint(protoPrinter) as ObjectNode) }
