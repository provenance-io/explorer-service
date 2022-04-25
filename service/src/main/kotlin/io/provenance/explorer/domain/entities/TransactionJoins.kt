package io.provenance.explorer.domain.entities

import io.provenance.explorer.domain.core.MdParent
import io.provenance.explorer.domain.core.sql.toProcedureObject
import io.provenance.explorer.domain.models.explorer.TxData
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

enum class TxAddressJoinType { ACCOUNT, OPERATOR }

object TxAddressJoinTable : IntIdTable(name = "tx_address_join") {
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val addressType = varchar("address_type", 16)
    val addressId = integer("address_id")
    val address = varchar("address", 128)
}

class TxAddressJoinRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxAddressJoinRecord>(TxAddressJoinTable) {

        fun findValidatorsByTxHash(activeSet: Int, txHashId: EntityID<Int>) = transaction {
            val records = StakingValidatorCacheRecord.wrapRows(
                TxAddressJoinTable
                    .innerJoin(
                        StakingValidatorCacheTable,
                        { TxAddressJoinTable.addressId },
                        { StakingValidatorCacheTable.id }
                    )
                    .select {
                        (TxAddressJoinTable.txHashId eq txHashId) and
                            (TxAddressJoinTable.addressType eq TxAddressJoinType.OPERATOR.name)
                    }
            ).toList().map { it.id.value }

            ValidatorStateRecord.findByListValId(activeSet, records)
        }

        fun findAccountsByTxHash(txHashId: EntityID<Int>) = transaction {
            AccountRecord.wrapRows(
                TxAddressJoinTable
                    .innerJoin(AccountTable, { TxAddressJoinTable.addressId }, { AccountTable.id })
                    .select {
                        (TxAddressJoinTable.txHashId eq txHashId) and
                            (TxAddressJoinTable.addressType eq TxAddressJoinType.ACCOUNT.name)
                    }
            ).toList()
        }

        fun buildInsert(txInfo: TxData, addrPair: Pair<String, Int?>, address: String) =
            listOf(
                0,
                txInfo.blockHeight,
                txInfo.txHash,
                address,
                0,
                addrPair.first,
                addrPair.second!!
            ).toProcedureObject()
    }

    var blockHeight by TxAddressJoinTable.blockHeight
    var txHashId by TxCacheRecord referencedOn TxAddressJoinTable.txHashId
    var txHash by TxAddressJoinTable.txHash
    var address by TxAddressJoinTable.address
    var addressId by TxAddressJoinTable.addressId
    var addressType by TxAddressJoinTable.addressType
}

object TxMarkerJoinTable : IntIdTable(name = "tx_marker_join") {
    val blockHeight = integer("block_height")
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txHash = varchar("tx_hash", 64)
    val markerId = reference("marker_id", MarkerCacheTable)
    val denom = varchar("denom", 256)
}

class TxMarkerJoinRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxMarkerJoinRecord>(TxMarkerJoinTable) {

        fun findLatestTxByDenom(denom: String) = transaction {
            TxCacheTable
                .innerJoin(TxMarkerJoinTable, { TxCacheTable.id }, { TxMarkerJoinTable.txHashId })
                .slice(TxCacheTable.txTimestamp)
                .select { TxMarkerJoinTable.denom eq denom }
                .orderBy(Pair(TxCacheTable.height, SortOrder.DESC))
                .limit(1, 0)
                .firstOrNull()
                ?.let { it[TxCacheTable.txTimestamp] }
        }

        fun findCountByDenom(markerId: Int) = transaction {
            TxMarkerJoinRecord.find { TxMarkerJoinTable.markerId eq markerId }.count().toBigInteger()
        }

        fun buildInsert(txInfo: TxData, markerId: Int, denom: String) =
            listOf(0, txInfo.blockHeight, txInfo.txHash, denom, 0, markerId).toProcedureObject()
    }

    var blockHeight by TxMarkerJoinTable.blockHeight
    var txHashId by TxCacheRecord referencedOn TxMarkerJoinTable.txHashId
    var txHash by TxMarkerJoinTable.txHash
    var markerId by TxMarkerJoinTable.markerId
    var denom by TxMarkerJoinTable.denom
}

object TxNftJoinTable : IntIdTable(name = "tx_nft_join") {
    val blockHeight = integer("block_height")
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txHash = varchar("tx_hash", 64)
    val metadataType = varchar("metadata_type", 16)
    val metadataId = integer("metadata_id")
    val metadataUuid = varchar("metadata_uuid", 128)
}

class TxNftJoinRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxNftJoinRecord>(TxNftJoinTable) {

        fun buildInsert(txInfo: TxData, mdTriple: Triple<MdParent, Int, String>) =
            listOf(
                0,
                txInfo.blockHeight,
                0,
                txInfo.txHash,
                mdTriple.first.name,
                mdTriple.second,
                mdTriple.third
            ).toProcedureObject()

        fun findTxByUuid(uuid: String, offset: Int, limit: Int) = transaction {
            val query = TxNftJoinTable.innerJoin(TxCacheTable, { TxNftJoinTable.txHashId }, { TxCacheTable.id })
                .slice(
                    TxCacheTable.id, TxCacheTable.hash,
                    TxCacheTable.height, TxCacheTable.gasWanted, TxCacheTable.gasUsed, TxCacheTable.txTimestamp,
                    TxCacheTable.errorCode, TxCacheTable.codespace
                )
                .select { TxNftJoinTable.metadataUuid eq uuid }
                .andWhere { TxNftJoinTable.metadataType eq MdParent.SCOPE.name }
                .orderBy(Pair(TxCacheTable.height, SortOrder.DESC))
                .limit(limit, offset.toLong())
            TxCacheRecord.wrapRows(query).toSet()
        }
    }

    var blockHeight by TxNftJoinTable.blockHeight
    var txHashId by TxCacheRecord referencedOn TxNftJoinTable.txHashId
    var txHash by TxNftJoinTable.txHash
    var metadataType by TxNftJoinTable.metadataType
    var metadataId by TxNftJoinTable.metadataId
    var metadataUuid by TxNftJoinTable.metadataUuid
}

object TxSmCodeTable : IntIdTable(name = "tx_sm_code") {
    val blockHeight = integer("block_height")
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txHash = varchar("tx_hash", 64)
    val smCode = integer("sm_code")
}

class TxSmCodeRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxSmCodeRecord>(TxSmCodeTable) {

        fun findByHashId(txHashId: Int) =
            transaction { TxSmCodeRecord.find { TxSmCodeTable.txHashId eq txHashId }.firstOrNull() }

        fun buildInsert(txInfo: TxData, smCode: Int) =
            listOf(0, txInfo.blockHeight, -1, txInfo.txHash, smCode).toProcedureObject()

        fun insertIgnore(txInfo: TxData, smCode: Int) = transaction {
            TxSmCodeTable.insertIgnore {
                it[this.blockHeight] = txInfo.blockHeight
                it[this.txHashId] = txInfo.txHashId!!
                it[this.txHash] = txInfo.txHash
                it[this.smCode] = smCode
            }
        }
    }

    var blockHeight by TxSmCodeTable.blockHeight
    var txHashId by TxCacheRecord referencedOn TxSmCodeTable.txHashId
    var txHash by TxSmCodeTable.txHash
    var smCode by TxSmCodeTable.smCode
}

object TxSmContractTable : IntIdTable(name = "tx_sm_contract") {
    val blockHeight = integer("block_height")
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txHash = varchar("tx_hash", 64)
    val contractId = integer("sm_contract_id")
    val contractAddress = varchar("sm_contract_address", 128)
}

class TxSmContractRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxSmContractRecord>(TxSmContractTable) {

        fun buildInsert(txInfo: TxData, smContractId: Int, contractAddr: String) =
            listOf(0, txInfo.blockHeight, -1, txInfo.txHash, smContractId, contractAddr).toProcedureObject()
    }

    var blockHeight by TxSmContractTable.blockHeight
    var txHashId by TxCacheRecord referencedOn TxSmContractTable.txHashId
    var txHash by TxSmContractTable.txHash
    var contractId by TxSmContractTable.contractId
    var contractAddress by TxSmContractTable.contractAddress
}

object TxIbcTable : IntIdTable(name = "tx_ibc") {
    val blockHeight = integer("block_height")
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txHash = varchar("tx_hash", 64)
    val client = varchar("client", 128)
    val channelId = integer("channel_id")
}

class TxIbcRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxIbcRecord>(TxIbcTable) {

        fun buildInsert(txInfo: TxData, client: String?, channelId: Int?) =
            listOf(0, txInfo.blockHeight, -1, txInfo.txHash, client, channelId).toProcedureObject()
    }

    var blockHeight by TxIbcTable.blockHeight
    var txHashId by TxCacheRecord referencedOn TxIbcTable.txHashId
    var txHash by TxIbcTable.txHash
    var client by TxIbcTable.client
    var channelId by TxIbcTable.channelId
}
