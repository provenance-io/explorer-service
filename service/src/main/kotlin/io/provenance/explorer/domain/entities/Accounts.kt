package io.provenance.explorer.domain.entities

import com.google.protobuf.Any
import cosmos.auth.v1beta1.Auth
import cosmos.vesting.v1beta1.Vesting
import ibc.applications.interchain_accounts.v1.Account.InterchainAccount
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.extensions.execAndMap
import io.provenance.explorer.domain.extensions.isAddressAsType
import io.provenance.explorer.grpc.extensions.getTypeShortName
import io.provenance.explorer.grpc.extensions.toBaseAccount
import io.provenance.explorer.grpc.extensions.toContinuousVestingAccount
import io.provenance.explorer.grpc.extensions.toDelayedVestingAccount
import io.provenance.explorer.grpc.extensions.toInterchainAccount
import io.provenance.explorer.grpc.extensions.toMarkerAccount
import io.provenance.explorer.grpc.extensions.toModuleAccount
import io.provenance.explorer.grpc.extensions.toPeriodicVestingAccount
import io.provenance.explorer.grpc.extensions.toPermanentLockedAccount
import io.provenance.explorer.service.getAccountType
import io.provenance.marker.v1.MarkerAccount
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction

object AccountTable : IntIdTable(name = "account") {
    val accountAddress = varchar("account_address", 128)
    val type = varchar("type", 128)
    val accountNumber = long("account_number").nullable()
    val baseAccount = jsonb<AccountTable, Auth.BaseAccount>("base_account", OBJECT_MAPPER).nullable()
    val data = jsonb<AccountTable, Any>("data", OBJECT_MAPPER).nullable()
    val isContract = bool("is_contract").default(false)
    val owner = varchar("owner", 128).nullable()
    val isGroupPolicy = bool("is_group_policy").default(false)
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

        fun countActiveAccounts() = transaction {
            AccountRecord.find {
                (AccountTable.isContract eq Op.FALSE) and
                (AccountTable.baseAccount.isNotNull()) and
                (AccountTable.type eq "BaseAccount")
            }.count()
        }
        fun findContractAccounts() = transaction {
            AccountRecord.find { AccountTable.isContract eq Op.TRUE }.toList()
        }

        fun findByAddress(addr: String) = AccountRecord.find { AccountTable.accountAddress eq addr }.firstOrNull()

        fun saveAccount(address: String, accPrefix: String, accountData: Any?, isContract: Boolean = false, isGroupPolicy: Boolean = false) =
            transaction {
                accountData?.let { insertIgnore(it, isContract) }
                    ?: if (address.isAddressAsType(accPrefix)) {
                        insertUnknownAccount(address, isContract, isGroupPolicy)
                    } else {
                        throw ResourceNotFoundException("Invalid account: '$address'")
                    }
            }

        private fun insertUnknownAccount(addr: String, isContract: Boolean = false, isGroupPolicy: Boolean = false) = transaction {
            findByAddress(addr) ?: AccountTable.insertAndGetId {
                it[this.accountAddress] = addr
                it[this.type] = "BaseAccount"
                it[this.isContract] = isContract
                it[this.isGroupPolicy] = isGroupPolicy
            }.let { findById(it)!! }
        }

        private fun insertIgnore(acc: Any, isContract: Boolean = false, isGroupPolicy: Boolean = false) =
            when (acc.typeUrl.getTypeShortName()) {
                Auth.ModuleAccount::class.java.simpleName ->
                    acc.toModuleAccount().let {
                        insertIgnore(
                            it.baseAccount.address,
                            acc.typeUrl.getAccountType(),
                            it.baseAccount.accountNumber,
                            it.baseAccount,
                            acc,
                            isContract,
                            isGroupPolicy
                        )
                    }
                Auth.BaseAccount::class.java.simpleName ->
                    acc.toBaseAccount().let {
                        insertIgnore(
                            it.address,
                            acc.typeUrl.getAccountType(),
                            it.accountNumber,
                            it,
                            acc,
                            isContract,
                            isGroupPolicy
                        )
                    }
                MarkerAccount::class.java.simpleName ->
                    acc.toMarkerAccount().let {
                        insertIgnore(
                            it.baseAccount.address,
                            acc.typeUrl.getAccountType(),
                            it.baseAccount.accountNumber,
                            it.baseAccount,
                            acc,
                            isContract,
                            isGroupPolicy
                        )
                    }
                Vesting.ContinuousVestingAccount::class.java.simpleName ->
                    acc.toContinuousVestingAccount().let {
                        insertIgnore(
                            it.baseVestingAccount.baseAccount.address,
                            acc.typeUrl.getAccountType(),
                            it.baseVestingAccount.baseAccount.accountNumber,
                            it.baseVestingAccount.baseAccount,
                            acc,
                            isContract,
                            isGroupPolicy
                        )
                    }
                Vesting.DelayedVestingAccount::class.java.simpleName ->
                    acc.toDelayedVestingAccount().let {
                        insertIgnore(
                            it.baseVestingAccount.baseAccount.address,
                            acc.typeUrl.getAccountType(),
                            it.baseVestingAccount.baseAccount.accountNumber,
                            it.baseVestingAccount.baseAccount,
                            acc,
                            isContract,
                            isGroupPolicy
                        )
                    }
                Vesting.PeriodicVestingAccount::class.java.simpleName ->
                    acc.toPeriodicVestingAccount().let {
                        insertIgnore(
                            it.baseVestingAccount.baseAccount.address,
                            acc.typeUrl.getAccountType(),
                            it.baseVestingAccount.baseAccount.accountNumber,
                            it.baseVestingAccount.baseAccount,
                            acc,
                            isContract,
                            isGroupPolicy
                        )
                    }
                Vesting.PermanentLockedAccount::class.java.simpleName ->
                    acc.toPermanentLockedAccount().let {
                        insertIgnore(
                            it.baseVestingAccount.baseAccount.address,
                            acc.typeUrl.getAccountType(),
                            it.baseVestingAccount.baseAccount.accountNumber,
                            it.baseVestingAccount.baseAccount,
                            acc,
                            isContract,
                            isGroupPolicy
                        )
                    }
                InterchainAccount::class.java.simpleName ->
                    acc.toInterchainAccount().let {
                        insertIgnore(
                            it.baseAccount.address,
                            acc.typeUrl.getAccountType(),
                            it.baseAccount.accountNumber,
                            it.baseAccount,
                            acc,
                            isContract,
                            isGroupPolicy,
                            it.accountOwner
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
            isContract: Boolean,
            isGroupPolicy: Boolean,
            owner: String? = null
        ) = transaction {
            (
                findByAddress(address)?.apply {
                    this.baseAccount = baseAccount
                    this.accountNumber = number
                    this.data = data
                    this.isContract = isContract
                    this.isGroupPolicy = isGroupPolicy
                    this.type = type // can change from base to marker with 1.8.0
                } ?: AccountTable.insertAndGetId {
                    it[this.accountAddress] = address
                    it[this.type] = type
                    it[this.accountNumber] = number
                    it[this.baseAccount] = baseAccount
                    it[this.data] = data
                    it[this.isContract] = isContract
                    it[this.isGroupPolicy] = isGroupPolicy
                    it[this.owner] = owner
                }.let { findById(it)!! }
                ).also {
                SignatureRecord.insertAndGet(baseAccount.pubKey, address)
                ProcessQueueRecord.insertIgnore(ProcessQueueType.ACCOUNT, address)
            }
        }
    }

    var accountAddress by AccountTable.accountAddress
    var type by AccountTable.type
    var accountNumber by AccountTable.accountNumber
    var baseAccount by AccountTable.baseAccount
    var data by AccountTable.data
    var isContract by AccountTable.isContract
    val tokenCounts by AccountTokenCountRecord referrersOn AccountTokenCountTable.addressId
    var owner by AccountTable.owner
    var isGroupPolicy by AccountTable.isGroupPolicy
}

object AddressImageTable : IdTable<String>(name = "address_image") {
    val address = varchar("address", 256)
    override val id = address.entityId()
    val imageUrl = text("image_url")
}

class AddressImageRecord(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, AddressImageRecord>(AddressImageTable) {

        fun findByAddress(addr: String) = transaction { AddressImageRecord.findById(addr) }

        fun upsert(addr: String, url: String) = transaction {
            findByAddress(addr)?.apply { this.imageUrl = url }
                ?: AddressImageTable.insert {
                    it[this.address] = addr
                    it[this.imageUrl] = url
                }
        }
    }

    var address by AddressImageTable.address
    var imageUrl by AddressImageTable.imageUrl
}

object AccountTokenCountTable : IntIdTable(name = "account_token_count") {
    val addressId = reference("address_id", AccountTable)
    val address = varchar("address", 128)
    val ftCount = integer("ft_count").default(0)
    val nftCount = integer("nft_count").default(0)
}

class AccountTokenCountRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AccountTokenCountRecord>(AccountTokenCountTable) {

        fun findByAddress(addr: String) = transaction {
            AccountTokenCountRecord.find { AccountTokenCountTable.address eq addr }.firstOrNull()
        }

        fun upsert(addr: String, ftCount: Int, nftCount: Int) = transaction {
            findByAddress(addr)?.apply {
                this.ftCount = ftCount
                this.nftCount = nftCount
            } ?: AccountRecord.findByAddress(addr)!!.let { rec ->
                AccountTokenCountTable.insertIgnore {
                    it[this.addressId] = rec.id
                    it[this.address] = addr
                    it[this.ftCount] = ftCount
                    it[this.nftCount] = nftCount
                }
            }
        }
    }

    var address by AccountTokenCountTable.address
    var addressId by AccountRecord referencedOn AccountTokenCountTable.addressId
    var ftCount by AccountTokenCountTable.ftCount
    var nftCount by AccountTokenCountTable.nftCount
}
