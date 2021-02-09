package io.provenance.explorer.domain.entities

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.models.clients.pb.Account
import io.provenance.explorer.domain.models.clients.pb.BaseAccount
import io.provenance.explorer.domain.models.clients.pb.MarkerAccount
import io.provenance.explorer.domain.models.clients.pb.MarkerBaseAccount
import io.provenance.explorer.domain.models.clients.pb.ModuleAccount
import io.provenance.explorer.domain.models.clients.pb.toMarkerBaseAccount
import io.provenance.explorer.service.getAccountType
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction


object AccountTable : IdTable<String>(name = "account") {
    val accountAddress = varchar("account_address", 128).primaryKey()
    override val id = accountAddress.entityId()
    val type = varchar("type", 128)
    val accountNumber = long("account_number")
    val baseAccount = jsonb<AccountTable, MarkerBaseAccount>("base_account", OBJECT_MAPPER)
    val data = jsonb<AccountTable, Account>("data", OBJECT_MAPPER)
}

class AccountRecord(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, AccountRecord>(AccountTable) {

        fun insertIgnore(acc: Account) =
            when (acc) {
                is ModuleAccount -> insertIgnore(
                    acc.baseAccount.address,
                    acc.type.getAccountType(),
                    acc.baseAccount.accountNumber.toLong(),
                    acc.baseAccount,
                    acc)
                is BaseAccount -> insertIgnore(
                    acc.address,
                    acc.type.getAccountType(),
                    acc.accountNumber.toLong(),
                    acc.toMarkerBaseAccount(),
                    acc)
                is MarkerAccount -> insertIgnore(
                    acc.baseAccount.address,
                    acc.type.getAccountType(),
                    acc.baseAccount.accountNumber.toLong(),
                    acc.baseAccount,
                    acc)
                else -> throw IllegalArgumentException("This account type has not been handled yet: ${acc.type}")
            }


        fun <T : Account> insertIgnore(
            address: String,
            type: String,
            number: Long,
            baseAccount: MarkerBaseAccount,
            data: T
        ) = transaction {
                AccountTable.insertIgnore {
                    it[this.accountAddress] = address
                    it[this.type] = type
                    it[this.accountNumber] = number
                    it[this.baseAccount] = baseAccount
                    it[this.data] = data
                }
            }.let { findById(address) }
    }

    var accountAddress by AccountTable.accountAddress
    var type by AccountTable.type
    var accountNumber by AccountTable.accountNumber
    var baseAccount by AccountTable.baseAccount
    var data by AccountTable.data
}
