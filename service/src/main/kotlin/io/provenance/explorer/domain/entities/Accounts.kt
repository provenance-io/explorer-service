package io.provenance.explorer.domain.entities

import com.google.protobuf.Any
import cosmos.auth.v1beta1.Auth
import cosmos.vesting.v1beta1.Vesting
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.extensions.execAndMap
import io.provenance.explorer.domain.extensions.isAddressAsType
import io.provenance.explorer.grpc.extensions.getTypeShortName
import io.provenance.explorer.service.getAccountType
import io.provenance.marker.v1.MarkerAccount
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Op
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

val vestingAccountTypes = listOf(
    Vesting.ContinuousVestingAccount::class.java.simpleName,
    Vesting.DelayedVestingAccount::class.java.simpleName,
    Vesting.PeriodicVestingAccount::class.java.simpleName,
    Vesting.PermanentLockedAccount::class.java.simpleName
)

fun List<AccountRecord>.addressList() = this.map { it.accountAddress }.toSet()

class AccountRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AccountRecord>(AccountTable) {

        fun findZeroSequenceAccounts() = transaction {
            val query = """
                SELECT account_address FROM account 
                WHERE (account.base_account -> 'sequence')::INTEGER = 0
                AND type = 'BaseAccount';
            """.trimIndent()
            query.execAndMap { it.getString("account_address") }
        }

        fun findAccountsByType(types: List<String>) = transaction {
            AccountRecord.find { AccountTable.type inList types }.toList()
        }

        fun findContractAccounts() = transaction {
            AccountRecord.find { AccountTable.isContract eq Op.TRUE }.toList()
        }

        fun findSigsByAddress(address: String) = SignatureRecord.findByJoin(SigJoinType.ACCOUNT, address)

        fun findByAddress(addr: String) = AccountRecord.find { AccountTable.accountAddress eq addr }.firstOrNull()

        fun saveAccount(address: String, accPrefix: String, accountData: Any?, isContract: Boolean = false) =
            transaction {
                accountData?.let { insertIgnore(it, isContract) }
                    ?: if (address.isAddressAsType(accPrefix)) insertUnknownAccount(address, isContract)
                    else throw ResourceNotFoundException("Invalid account: '$address'")
            }

        private fun insertUnknownAccount(addr: String, isContract: Boolean = false) = transaction {
            findByAddress(addr) ?: AccountTable.insertAndGetId {
                it[this.accountAddress] = addr
                it[this.type] = "BaseAccount"
                it[this.isContract] = isContract
            }.let { findById(it)!! }
        }

        private fun insertIgnore(acc: Any, isContract: Boolean = false) =
            when (acc.typeUrl.getTypeShortName()) {
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
                Vesting.ContinuousVestingAccount::class.java.simpleName ->
                    acc.unpack(Vesting.ContinuousVestingAccount::class.java).let {
                        insertIgnore(
                            it.baseVestingAccount.baseAccount.address,
                            acc.typeUrl.getAccountType(),
                            it.baseVestingAccount.baseAccount.accountNumber,
                            it.baseVestingAccount.baseAccount,
                            acc,
                            isContract
                        )
                    }
                Vesting.DelayedVestingAccount::class.java.simpleName ->
                    acc.unpack(Vesting.DelayedVestingAccount::class.java).let {
                        insertIgnore(
                            it.baseVestingAccount.baseAccount.address,
                            acc.typeUrl.getAccountType(),
                            it.baseVestingAccount.baseAccount.accountNumber,
                            it.baseVestingAccount.baseAccount,
                            acc,
                            isContract
                        )
                    }
                Vesting.PeriodicVestingAccount::class.java.simpleName ->
                    acc.unpack(Vesting.PeriodicVestingAccount::class.java).let {
                        insertIgnore(
                            it.baseVestingAccount.baseAccount.address,
                            acc.typeUrl.getAccountType(),
                            it.baseVestingAccount.baseAccount.accountNumber,
                            it.baseVestingAccount.baseAccount,
                            acc,
                            isContract
                        )
                    }
                Vesting.PermanentLockedAccount::class.java.simpleName ->
                    acc.unpack(Vesting.PermanentLockedAccount::class.java).let {
                        insertIgnore(
                            it.baseVestingAccount.baseAccount.address,
                            acc.typeUrl.getAccountType(),
                            it.baseVestingAccount.baseAccount.accountNumber,
                            it.baseVestingAccount.baseAccount,
                            acc,
                            isContract
                        )
                    }

                else -> throw IllegalArgumentException("This account type has not been handled yet: ${acc.typeUrl}")
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
