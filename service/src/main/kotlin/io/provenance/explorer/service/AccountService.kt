package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.AccountRecord.Companion.update
import io.provenance.explorer.domain.entities.StakingValidatorCacheRecord
import io.provenance.explorer.domain.entities.TxAddressJoinRecord
import io.provenance.explorer.domain.entities.TxAddressJoinType
import io.provenance.explorer.domain.extensions.toSigObj
import io.provenance.explorer.domain.models.explorer.AccountDetail
import io.provenance.explorer.domain.models.explorer.toData
import io.provenance.explorer.grpc.extensions.getModuleAccName
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class AccountService(private val accountClient: AccountGrpcClient, private val props: ExplorerProperties) {

    protected val logger = logger(AccountService::class)

    fun getAccountRaw(address: String) = transaction { AccountRecord.findByAddress(address) } ?: saveAccount(address)

    fun saveAccount(address: String) = accountClient.getAccountInfo(address).let { AccountRecord.insertIgnore(it) }

    fun getAccountDetail(address: String) = getAccountRaw(address).let {
        AccountDetail(
            it.type,
            it.accountAddress,
            it.accountNumber,
            it.baseAccount.sequence.toInt(),
            AccountRecord.findSigsByAddress(it.accountAddress).toSigObj(props.provAccPrefix()),
            getAccountBalances(address),
            it.data.getModuleAccName()
        )
    }

    fun getAccountBalances(address: String) = accountClient.getAccountBalances(address).map { it.toData()}

    fun getCurrentSupply(denom: String) = accountClient.getCurrentSupply(denom).amount

    fun updateAccounts(accs: Set<Int>) = transaction {
        logger.info("Updating accounts")
        accs.forEach { id ->
            val record = AccountRecord.findById(id)!!
            val data = accountClient.getAccountInfo(record.accountAddress)
            if (data != record.data)
                record.update(data)
        }
    }
}

fun String.getAccountType() = this.split(".").last()
