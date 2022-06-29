package io.provenance.explorer.domain.extensions

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.Any
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import cosmos.tx.v1beta1.TxOuterClass.TxBody
import io.provenance.explorer.OBJECT_MAPPER

data class TxMessageBody(
    val json: ObjectNode,
    val base64: List<String>
)

fun Message.pack(): Any = Any.pack(this, "")

fun Iterable<Any>.toTxBody(memo: String? = null, timeoutHeight: Long? = null): TxBody =
    TxBody.newBuilder()
        .addAllMessages(this)
        .also { builder ->
            memo?.run { builder.memo = this }
            timeoutHeight?.run { builder.timeoutHeight = this }
        }
        .build()

fun Any.toTxBody(memo: String? = null, timeoutHeight: Long? = null): TxBody =
    listOf(this).toTxBody(memo, timeoutHeight)

fun TxBody.toTxMessageBody(printer: JsonFormat.Printer) = TxMessageBody(
    json = OBJECT_MAPPER.readValue(printer.print(this), ObjectNode::class.java),
    base64 = this.messagesList.map { it.toByteArray().base64EncodeString() }
)
