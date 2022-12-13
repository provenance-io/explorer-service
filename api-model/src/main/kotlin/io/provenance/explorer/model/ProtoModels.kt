package io.provenance.explorer.model

import com.fasterxml.jackson.databind.node.ObjectNode

data class TxMessageBody(
    val json: ObjectNode,
    val base64: List<String>
)
