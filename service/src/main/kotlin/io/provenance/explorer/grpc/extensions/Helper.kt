package io.provenance.explorer.grpc.extensions

import cosmos.tx.v1beta1.ServiceOuterClass

fun ServiceOuterClass.GetTxResponse.mapEventAttrValues(idx: Int, event: String, attrList: List<String>) =
    this.txResponse.logsList[idx].eventsList.firstOrNull { it.type == event }
        ?.attributesList?.let { list -> attrList.map { attr -> attr to list.first { it.key == attr }.value.scrubQuotes() } }
        ?.toMap() ?: mapOf()

fun ServiceOuterClass.GetTxResponse.findEvent(idx: Int, event: String) =
    this.txResponse.logsList[idx].eventsList.firstOrNull { it.type == event }

fun ServiceOuterClass.GetTxResponse.findAllMatchingEvents(eventList: List<String>) =
    this.txResponse.logsList.flatMap { log -> log.eventsList }.filter { it.type in eventList }

fun String.removeFirstSlash() = this.split("/")[1]

fun ServiceOuterClass.GetTxResponse.mapTxEventAttrValues(eventType: String, attrKey: String) =
    this.txResponse.eventsList
        .filterIndexed { _, event ->
            event.type == eventType && event.attributesList.map { it.key.toStringUtf8() }.contains(attrKey)
        }.mapIndexed { idx, event ->
            idx to event.attributesList.first { it.key.toStringUtf8() == attrKey }.value.toStringUtf8()
        }.toMap()
