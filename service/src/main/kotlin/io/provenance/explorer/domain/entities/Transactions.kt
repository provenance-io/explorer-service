package io.provenance.explorer.domain.entities

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.extensions.pubKeyToBech32
import io.provenance.explorer.domain.extensions.signatureKey
import io.provenance.explorer.domain.extensions.type
import io.provenance.explorer.domain.models.clients.pb.PbTransaction
import io.provenance.explorer.domain.models.clients.pb.TxAuthInfoSigner
import io.provenance.explorer.domain.models.clients.pb.TxSingle
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

object TransactionCacheTable : CacheIdTable<String>(name = "transaction_cache") {
    val hash = varchar("hash", 64).primaryKey()
    override val id = hash.entityId()
    val height = reference("height", BlockCacheTable.height)
    val txType = varchar("tx_type", 64)
    val signer = varchar("signer", 128).nullable()
    val gasWanted = integer("gas_wanted")
    val gasUsed = integer("gas_used")
    val txTimestamp = datetime("tx_timestamp")
    val errorCode = integer("error_code").nullable()
    val codespace = varchar("codespace", 16).nullable()
    val tx = jsonb<TransactionCacheTable, PbTransaction>("tx", OBJECT_MAPPER)
    val txV2 = jsonb<TransactionCacheTable, TxSingle>("tx_v2", OBJECT_MAPPER)
}

class TransactionCacheRecord(id: EntityID<String>) : CacheEntity<String>(id) {
    companion object : CacheEntityClass<String, TransactionCacheRecord>(TransactionCacheTable) {
        fun insertIgnore(txn: PbTransaction, accountPrefix: String, txnV2: TxSingle) =
            transaction {
                TransactionCacheTable.insertIgnore {
                    it[hash] = txn.txhash
                    it[height] = txn.height.toInt()
                    if (txn.code != null) it[errorCode] = txn.code
                    if (txn.codespace != null) it[codespace] = txn.codespace
                    it[txType] = if (txn.code == null) txn.type()!! else "ERROR"
                    it[signer] = txnV2.tx.authInfo.signerInfos.signatureKey()?.pubKeyToBech32(accountPrefix)
                    it[gasUsed] = txn.gasUsed.toInt()
                    it[gasWanted] = txn.gasWanted.toInt()
                    it[txTimestamp] = DateTime.parse(txn.timestamp)
                    it[tx] = txn
                    it[txV2] = txnV2
                    it[hitCount] = 0
                    it[lastHit] = DateTime.now()
                }
            }

        fun findByHeight(height: Int) =
            TransactionCacheRecord.find { TransactionCacheTable.height eq height }

        fun getAllWithOffset(sort: SortOrder, count: Int, offset: Int) =
            TransactionCacheRecord.all()
                .orderBy(Pair(TransactionCacheTable.height, sort))
                .limit(count, offset)
    }

    var hash by TransactionCacheTable.hash
    var height by TransactionCacheTable.height
    var txType by TransactionCacheTable.txType
    var signer by TransactionCacheTable.signer
    var gasWanted by TransactionCacheTable.gasWanted
    var gasUsed by TransactionCacheTable.gasUsed
    var txTimestamp by TransactionCacheTable.txTimestamp
    var errorCode by TransactionCacheTable.errorCode
    var codespace by TransactionCacheTable.codespace
    var tx by TransactionCacheTable.tx
    var txV2 by TransactionCacheTable.txV2
    override var lastHit by TransactionCacheTable.lastHit
    override var hitCount by TransactionCacheTable.hitCount
}
