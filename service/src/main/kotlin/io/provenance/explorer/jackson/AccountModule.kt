package io.provenance.explorer.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.treeToValue
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.models.clients.pb.Account
import io.provenance.explorer.domain.models.clients.pb.BaseAccount
import io.provenance.explorer.domain.models.clients.pb.MarkerAccount
import io.provenance.explorer.domain.models.clients.pb.ModuleAccount
import io.provenance.explorer.domain.models.clients.pb.UnknownAccount


class AccountModule : SimpleModule() {
    init {
        addDeserializer(Account::class.java, AccountDeserializer())
    }
}

class AccountDeserializer : JsonDeserializer<Account>() {
    private val logger = logger(AccountDeserializer::class)

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Account? {
        val tree: JsonNode = p.codec.readTree(p)
        val accountType = tree.get("@type").asText()
        return with(accountType!!) {
            when {
                contains(ModuleAccount.TYPE) -> OBJECT_MAPPER.treeToValue<ModuleAccount>(tree)
                contains(BaseAccount.TYPE) -> OBJECT_MAPPER.treeToValue<BaseAccount>(tree)
                contains(MarkerAccount.TYPE) -> OBJECT_MAPPER.treeToValue<MarkerAccount>(tree)
                else -> UnknownAccount(accountType, tree)
                    .also { logger.error("This account type has not been handled yet: $accountType") }
            }
        }
    }
}
