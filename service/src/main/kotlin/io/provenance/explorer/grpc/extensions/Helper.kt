package io.provenance.explorer.grpc.extensions

import cosmos.tx.v1beta1.ServiceOuterClass


fun ServiceOuterClass.GetTxResponse.mapEventAttrValues(idx: Int, event: String, attrList: List<String>): Map<String, String> {
    return if (this.txResponse.logsList == null || this.txResponse.logsList.size <= idx) {
        mapEventAttrValuesByMsgIndex(idx, event, attrList)
    } else {
        mapEventAttrValuesFromLogs(idx, event, attrList)
    }
}

fun ServiceOuterClass.GetTxResponse.mapEventAttrValuesFromLogs(idx: Int, event: String, attrList: List<String>): Map<String, String> {
    return this.txResponse.logsList[idx].eventsList.firstOrNull { it.type == event }
        ?.attributesList?.let { list -> attrList.map { attr -> attr to list.first { it.key == attr }.value.scrubQuotes() } }
        ?.toMap() ?: mapOf()
}
fun ServiceOuterClass.GetTxResponse.mapEventAttrValuesByMsgIndex(idx: Int, event: String, attrList: List<String>): Map<String, String> {
    return this.txResponse.eventsList
        .firstOrNull { eventEntry ->
            eventEntry.type == event && eventEntry.attributesList.any { it.key == "msg_index" && it.value == idx.toString() }
        }
        ?.attributesList
        ?.filter { it.key in attrList }
        ?.associate { it.key to it.value.scrubQuotes() }
        ?: mapOf()
}

fun ServiceOuterClass.GetTxResponse.findAllMatchingEvents(eventList: List<String>) =
    this.txResponse.eventsList.filter { it.type in eventList }

fun String.removeFirstSlash() = this.split("/")[1]

fun ServiceOuterClass.GetTxResponse.mapTxEventAttrValues(eventType: String, attrKey: String) =
    this.txResponse.eventsList
        .filterIndexed { _, event ->
            event.type == eventType && event.attributesList.map { it.key }.contains(attrKey)
        }.mapIndexed { idx, event ->
            idx to event.attributesList.first { it.key == attrKey }.value
        }.toMap()
