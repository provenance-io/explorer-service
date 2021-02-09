package io.provenance.explorer.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.treeToValue
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.models.clients.pb.SendTxMessage
import io.provenance.explorer.domain.models.clients.pb.SubmitProposalTxMessage
import io.provenance.explorer.domain.models.clients.pb.TxMessage
import io.provenance.explorer.domain.models.clients.pb.UnknownTxMessage
import io.provenance.explorer.domain.models.clients.pb.VoteTxMessage


class TxMessageModule : SimpleModule() {
    init {
        addDeserializer(TxMessage::class.java, TxMessageDeserializer())
    }
}

class TxMessageDeserializer : JsonDeserializer<TxMessage>() {
    private val logger = logger(TxMessageDeserializer::class)

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TxMessage? {
        val tree: JsonNode = p.codec.readTree(p)
        val txType = tree.get("@type").asText()
        return with(txType!!) {
            when {
                contains(SendTxMessage.TYPE) -> OBJECT_MAPPER.treeToValue<SendTxMessage>(tree)
                contains(VoteTxMessage.TYPE) -> OBJECT_MAPPER.treeToValue<VoteTxMessage>(tree)
                contains(SubmitProposalTxMessage.TYPE) ->
                    OBJECT_MAPPER.treeToValue<SubmitProposalTxMessage>(tree)
                else -> UnknownTxMessage(txType, tree)
                    .also { logger.error("This transaction type has not been handled yet: $txType") }
            }
        }
    }
}
