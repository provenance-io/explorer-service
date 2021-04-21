package io.provenance.explorer.domain.entities

import com.google.protobuf.Any
import cosmos.auth.v1beta1.Auth
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.grpc.extensions.getTypeShortName
import io.provenance.explorer.grpc.extensions.toMarker
import io.provenance.explorer.service.getAccountType
import io.provenance.marker.v1.MarkerAccount
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.transactions.transaction


object AccountTable : IntIdTable(name = "account") {
    val accountAddress = varchar("account_address", 128)
    val type = varchar("type", 128)
    val accountNumber = long("account_number")
    val baseAccount = jsonb<AccountTable, Auth.BaseAccount>("base_account", OBJECT_MAPPER)
    val data = jsonb<AccountTable, Any>("data", OBJECT_MAPPER)
}

class AccountRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AccountRecord>(AccountTable) {

        fun findSigsByAddress(address: String) = SignatureRecord.findByJoin(SigJoinType.ACCOUNT, address)

        fun findByAddress(addr: String) = AccountRecord.find { AccountTable.accountAddress eq addr }.firstOrNull()

        fun insertIgnore(acc: Any) =
            acc.typeUrl.getTypeShortName().let { type ->
                when (type) {
                    Auth.ModuleAccount::class.java.simpleName ->
                        acc.unpack(Auth.ModuleAccount::class.java).let {
                            insertIgnore(
                                it.baseAccount.address,
                                acc.typeUrl.getAccountType(),
                                it.baseAccount.accountNumber,
                                it.baseAccount,
                                acc
                            )
                        }
                    Auth.BaseAccount::class.java.simpleName ->
                        acc.unpack(Auth.BaseAccount::class.java).let {
                            insertIgnore(
                                it.address,
                                acc.typeUrl.getAccountType(),
                                it.accountNumber,
                                it,
                                acc
                            )
                        }
                    MarkerAccount::class.java.simpleName ->
                        acc.unpack(MarkerAccount::class.java).let {
                            insertIgnore(
                                it.baseAccount.address,
                                acc.typeUrl.getAccountType(),
                                it.baseAccount.accountNumber,
                                it.baseAccount,
                                acc
                            )
                        }
                    else -> throw IllegalArgumentException("This account type has not been handled yet: ${acc.typeUrl}")
                }
            }

        fun insertIgnore(
            address: String,
            type: String,
            number: Long,
            baseAccount: Auth.BaseAccount,
            data: Any
        ) = transaction {
            AccountTable.insertIgnoreAndGetId {
                it[this.accountAddress] = address
                it[this.type] = type
                it[this.accountNumber] = number
                it[this.baseAccount] = baseAccount
                it[this.data] = data
            }.also {
                SignatureJoinRecord.insert(baseAccount.pubKey, SigJoinType.ACCOUNT, address)
            }.let { findById(it!!)!! }
        }

        fun AccountRecord.update(acc: Any) =
            acc.typeUrl.getTypeShortName().let { type ->
                when (type) {
                    Auth.ModuleAccount::class.java.simpleName ->
                        acc.unpack(Auth.ModuleAccount::class.java).let {
                            this.apply { this.baseAccount = it.baseAccount }
                        }
                    Auth.BaseAccount::class.java.simpleName ->
                        acc.unpack(Auth.BaseAccount::class.java).let {
                            this.apply { this.baseAccount = it }
                        }
                    MarkerAccount::class.java.simpleName ->
                        acc.unpack(MarkerAccount::class.java).let {
                            this.apply { this.baseAccount = it.baseAccount }
                        }
                    else -> throw IllegalArgumentException("This account type has not been handled yet: ${acc.typeUrl}")
                }
            }
    }

    var accountAddress by AccountTable.accountAddress
    var type by AccountTable.type
    var accountNumber by AccountTable.accountNumber
    var baseAccount by AccountTable.baseAccount
    var data by AccountTable.data
}
