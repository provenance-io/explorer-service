package io.provenance.explorer.grpc.extensions

import cosmos.base.abci.v1beta1.Abci
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

fun ServiceOuterClass.GetTxResponse.eventsAtIndex(index: Int): List<Abci.StringEvent> {
    return if (this.txResponse.logsList == null || this.txResponse.logsList.size <= index) {
        txResponse.eventsList.filter { event ->
            event.attributesList.any { attribute ->
                attribute.key == "msg_index" && attribute.value == index.toString()
            }
        }.map { event ->
            val convertedAttributes = event.attributesList.map { attribute ->
                Abci.Attribute.newBuilder()
                    .setKey(attribute.key)
                    .setValue(attribute.value)
                    .build()
            }
            Abci.StringEvent.newBuilder()
                .setType(event.type)
                .addAllAttributes(convertedAttributes)
                .build()
        }
    } else {
        this.txResponse.logsList[index].eventsList
    }
}


