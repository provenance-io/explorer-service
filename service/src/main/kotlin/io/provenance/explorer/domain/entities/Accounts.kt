package io.provenance.explorer.domain.entities

import com.google.protobuf.Any
import cosmos.auth.v1beta1.Auth
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.grpc.extensions.getTypeShortName
import io.provenance.explorer.service.getAccountType
import io.provenance.marker.v1.MarkerAccount
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction

object AccountTable : IntIdTable(name = "account") {
    val accountAddress = varchar("account_address", 128)
    val type = varchar("type", 128)
    val accountNumber = long("account_number").nullable()
    val baseAccount = jsonb<AccountTable, Auth.BaseAccount>("base_account", OBJECT_MAPPER).nullable()
    val data = jsonb<AccountTable, Any>("data", OBJECT_MAPPER).nullable()
    val isContract = bool("is_contract").default(false)
}

class AccountRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AccountRecord>(AccountTable) {

        fun findAccountsMissingNumber() = transaction {
            AccountRecord.find { AccountTable.accountNumber.isNull() and AccountTable.baseAccount.isNotNull() }.toList()
        }

        fun findSigsByAddress(address: String) = SignatureRecord.findByJoin(SigJoinType.ACCOUNT, address)

        fun findByAddress(addr: String) = AccountRecord.find { AccountTable.accountAddress eq addr }.firstOrNull()

        fun findListByAddress(list: List<String>) = AccountRecord.find { AccountTable.accountAddress.inList(list) }

        fun insertUnknownAccount(addr: String, isContract: Boolean = false) = transaction {
            findByAddress(addr) ?: AccountTable.insertAndGetId {
                it[this.accountAddress] = addr
                it[this.type] = "BaseAccount"
                it[this.isContract] = isContract
            }.let { findById(it)!! }
        }

        fun insertIgnore(acc: Any, isContract: Boolean = false) =
            acc.typeUrl.getTypeShortName().let { type ->
                when (type) {
                    Auth.ModuleAccount::class.java.simpleName ->
                        acc.unpack(Auth.ModuleAccount::class.java).let {
                            insertIgnore(
                                it.baseAccount.address,
                                acc.typeUrl.getAccountType(),
                                it.baseAccount.accountNumber,
                                it.baseAccount,
                                acc,
                                isContract
                            )
                        }
                    Auth.BaseAccount::class.java.simpleName ->
                        acc.unpack(Auth.BaseAccount::class.java).let {
                            insertIgnore(
                                it.address,
                                acc.typeUrl.getAccountType(),
                                it.accountNumber,
                                it,
                                acc,
                                isContract
                            )
                        }
                    MarkerAccount::class.java.simpleName ->
                        acc.unpack(MarkerAccount::class.java).let {
                            insertIgnore(
                                it.baseAccount.address,
                                acc.typeUrl.getAccountType(),
                                it.baseAccount.accountNumber,
                                it.baseAccount,
                                acc,
                                isContract
                            )
                        }
                    else -> throw IllegalArgumentException("This account type has not been handled yet: ${acc.typeUrl}")
                }
            }

        private fun insertIgnore(
            address: String,
            type: String,
            number: Long,
            baseAccount: Auth.BaseAccount,
            data: Any,
            isContract: Boolean
        ) = transaction {
            (
                findByAddress(address)?.apply {
                    this.baseAccount = baseAccount
                    this.accountNumber = number
                    this.data = data
                    this.isContract = isContract
                    this.type = type // can change from base to marker with 1.8.0
                } ?: AccountTable.insertAndGetId {
                    it[this.accountAddress] = address
                    it[this.type] = type
                    it[this.accountNumber] = number
                    it[this.baseAccount] = baseAccount
                    it[this.data] = data
                    it[this.isContract] = isContract
                }.let { findById(it)!! }
                )
                .also {
                    SignatureJoinRecord.insert(baseAccount.pubKey, SigJoinType.ACCOUNT, address)
                }
        }
    }

    var accountAddress by AccountTable.accountAddress
    var type by AccountTable.type
    var accountNumber by AccountTable.accountNumber
    var baseAccount by AccountTable.baseAccount
    var data by AccountTable.data
    var isContract by AccountTable.isContract
}
