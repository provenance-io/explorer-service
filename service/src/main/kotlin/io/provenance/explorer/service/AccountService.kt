package io.provenance.explorer.service

import io.provenance.explorer.client.PbClient
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.models.explorer.AccountDetail
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class AccountService(private val pbClient: PbClient) {

    private fun getAccountRaw(address: String) = transaction {
        AccountRecord.findById(address)
    } ?: pbClient.getAccountInfo(address).let { AccountRecord.insertIgnore(it.account)!! }

    fun getAccountDetail(address: String) = getAccountRaw(address).let {
        println(it.id.value)
        AccountDetail(
            it.type,
            it.id.value,
            it.accountNumber,
            it.baseAccount.sequence.toInt(),
            it.baseAccount.pubKey,
            getAccountBalances(address).balances
        )
    }

    private fun getAccountBalances(address: String) = pbClient.getAccountBalances(address)

}

fun String.getAccountType() = this.split(".").last()
