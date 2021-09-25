package io.provenance.explorer.domain.entities

import io.provenance.explorer.domain.core.MdParent
import io.provenance.explorer.domain.models.explorer.TxData
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
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

        private fun findByHashAndAddress(txHashId: EntityID<Int>, addrPair: Pair<String, Int?>, addr: String) =
            transaction {
                TxAddressJoinRecord
                    .find {
                        (TxAddressJoinTable.txHashId eq txHashId) and
                            (
                                if (addrPair.second != null)
                                    (TxAddressJoinTable.addressType eq addrPair.first) and (TxAddressJoinTable.addressId eq addrPair.second!!)
                                else (TxAddressJoinTable.address eq addr)
                                )
                    }
                    .firstOrNull()
            }

        fun findValidatorsByTxHash(txHashId: EntityID<Int>) = transaction {
            val records = StakingValidatorCacheRecord.wrapRows(
                TxAddressJoinTable
                    .innerJoin(StakingValidatorCacheTable, { TxAddressJoinTable.addressId }, { StakingValidatorCacheTable.id })
                    .select {
                        (TxAddressJoinTable.txHashId eq txHashId) and
                            (TxAddressJoinTable.addressType eq TxAddressJoinType.OPERATOR.name)
                    }
            ).toList().map { it.id.value }

            ValidatorStateRecord.findByListValId(records)
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

        fun insert(txHash: String, txId: EntityID<Int>, blockHeight: Int, addrPair: Pair<String, Int?>, address: String) =
            transaction {
                findByHashAndAddress(txId, addrPair, address) ?: TxAddressJoinTable.insert {
                    it[this.blockHeight] = blockHeight
                    it[this.txHashId] = txId
                    it[this.txHash] = txHash
                    it[this.addressId] = addrPair.second!!
                    it[this.addressType] = addrPair.first
                    it[this.address] = address
                }
            }
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

        private fun findByHashAndDenom(txId: EntityID<Int>, markerId: Int) = transaction {
            TxMarkerJoinRecord
                .find { (TxMarkerJoinTable.txHashId eq txId) and (TxMarkerJoinTable.markerId eq markerId) }
                .firstOrNull()
        }

        fun insert(txHash: String, txId: EntityID<Int>, blockHeight: Int, markerId: Int, denom: String) = transaction {
            findByHashAndDenom(txId, markerId) ?: TxMarkerJoinTable.insert {
                it[this.blockHeight] = blockHeight
                it[this.txHash] = txHash
                it[this.txHashId] = txId
                it[this.denom] = denom
                it[this.markerId] = markerId
            }
        }
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

        private fun findByHashIdAndUuid(txHashId: EntityID<Int>, mdTriple: Triple<MdParent, Int, String>) =
            transaction {
                TxNftJoinRecord
                    .find {
                        (TxNftJoinTable.txHashId eq txHashId) and
                            (TxNftJoinTable.metadataType eq mdTriple.first.name) and
                            (TxNftJoinTable.metadataId eq mdTriple.second)
                    }
                    .firstOrNull()
            }

        fun insert(txHash: String, txId: EntityID<Int>, blockHeight: Int, mdTriple: Triple<MdParent, Int, String>) =
            transaction {
                findByHashIdAndUuid(txId, mdTriple) ?: TxNftJoinTable.insert {
                    it[this.blockHeight] = blockHeight
                    it[this.txHashId] = txId
                    it[this.txHash] = txHash
                    it[this.metadataId] = mdTriple.second
                    it[this.metadataType] = mdTriple.first.name
                    it[this.metadataUuid] = mdTriple.third
                }
            }

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

        private fun findByHashIdAndSmCode(txHashId: Int, smCode: Int) =
            transaction {
                TxSmCodeRecord
                    .find { (TxSmCodeTable.txHashId eq txHashId) and (TxSmCodeTable.smCode eq smCode) }
                    .firstOrNull()
            }

        fun findByHashId(txHashId: Int) =
            transaction { TxSmCodeRecord.find { TxSmCodeTable.txHashId eq txHashId }.firstOrNull() }

        fun insert(txInfo: TxData, smCode: Int) =
            transaction {
                findByHashIdAndSmCode(txInfo.txHashId!!, smCode) ?: TxSmCodeTable.insert {
                    it[this.blockHeight] = txInfo.blockHeight
                    it[this.txHashId] = txInfo.txHashId
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

        private fun findByHashIdAndSmContractId(txHashId: Int, contractId: Int) =
            transaction {
                TxSmContractRecord
                    .find { (TxSmContractTable.txHashId eq txHashId) and (TxSmContractTable.contractId eq contractId) }
                    .firstOrNull()
            }

        fun insert(txInfo: TxData, smContractId: Int, contractAddr: String) =
            transaction {
                findByHashIdAndSmContractId(txInfo.txHashId!!, smContractId) ?: TxSmContractTable.insert {
                    it[this.blockHeight] = txInfo.blockHeight
                    it[this.txHashId] = txInfo.txHashId
                    it[this.txHash] = txInfo.txHash
                    it[this.contractId] = smContractId
                    it[this.contractAddress] = contractAddr
                }
            }
    }

    var blockHeight by TxSmContractTable.blockHeight
    var txHashId by TxCacheRecord referencedOn TxSmContractTable.txHashId
    var txHash by TxSmContractTable.txHash
    var contractId by TxSmContractTable.contractId
    var contractAddress by TxSmContractTable.contractAddress
}
