package io.provenance.explorer.domain.extensions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.JSON_NODE_FACTORY
import io.provenance.explorer.OBJECT_MAPPER

val protoTypesToCheckForMetadata = arrayOf(
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

val protoTypesFieldsToCheckForMetadata = arrayOf(
    "scopeId",
    "specificationId",
    "recordId",
    "sessionId",
    "contractSpecificationId",
    "scopeSpecificationId"
)

val protoTypesToCheckForSmartContract = arrayOf(
    "/cosmwasm.wasm.v1beta1.MsgInstantiateContract",
    "/cosmwasm.wasm.v1beta1.MsgExecuteContract",
    "/cosmwasm.wasm.v1beta1.MsgMigrateContract"
)

val protoTypesFieldsToCheckForSmartContract = arrayOf(
    "init_msg",
    "msg",
    "migrate_msg"
)

fun Message.toObjectNode(protoPrinter: JsonFormat.Printer) =
    OBJECT_MAPPER.readTree(protoPrinter.print(this))
        .let { node ->
            node.get("@type")?.asText()?.let { proto ->
                if (protoTypesToCheckForMetadata.contains(proto))
                    protoTypesFieldsToCheckForMetadata.forEach { fromBase64ToMAddress(node, it) }
                if (protoTypesToCheckForSmartContract.contains(proto))
                    protoTypesFieldsToCheckForSmartContract.forEach { fromBase64ToObject(node, it) }
            }
            (node as ObjectNode).remove("@type")
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
        val newValue = jsonNode.get(fieldName).asText().fromBase64ToMAddress().toString()
        (jsonNode as ObjectNode).replace(fieldName, JSON_NODE_FACTORY.textNode(newValue))
        found = true // stop after first find
    }

    if (!found) {
        jsonNode.forEach { fromBase64ToMAddress(it, fieldName) }
    }
}
