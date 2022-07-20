package io.provenance.explorer.grpc.extensions

import cosmos.tx.v1beta1.ServiceOuterClass

fun ServiceOuterClass.GetTxResponse.mapEventAttrValues(idx: Int, event: String, attrList: List<String>) =
    this.txResponse.logsList[idx].eventsList.first { it.type == event }
        .attributesList.let { list -> attrList.map { attr -> attr to list.first { it.key == attr }.value } }
        .toMap()

fun ServiceOuterClass.GetTxResponse.findEvent(idx: Int, event: String) =
    this.txResponse.logsList[idx].eventsList.firstOrNull { it.type == event }

fun ServiceOuterClass.GetTxResponse.findAllMatchingEvents(eventList: List<String>) =
    this.txResponse.logsList.flatMap { log -> log.eventsList }.filter { it.type in eventList }

fun String.removeFirstSlash() = this.split("/")[1]
