package io.provenance.explorer.domain.entities

import com.google.protobuf.Timestamp
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.extensions.signatureKey
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.type
import io.provenance.explorer.grpc.toKeyValue
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
    val txV2 = jsonb<TransactionCacheTable, ServiceOuterClass.GetTxResponse>("tx_v2", OBJECT_MAPPER)
}

class TransactionCacheRecord(id: EntityID<String>) : CacheEntity<String>(id) {
    companion object : CacheEntityClass<String, TransactionCacheRecord>(TransactionCacheTable) {
        fun insertIgnore(tx: ServiceOuterClass.GetTxResponse, txTime: Timestamp) =
            transaction {
                TransactionCacheTable.insertIgnore {
                    it[hash] = tx.txResponse.txhash
                    it[height] = tx.txResponse.height.toInt()
                    if (tx.txResponse.code > 0) it[errorCode] = tx.txResponse.code
                    if (tx.txResponse.codespace.isNotBlank()) it[codespace] = tx.txResponse.codespace
                    it[txType] = if (tx.txResponse.code == 0) tx.txResponse.type()!! else "ERROR"
                    it[signer] = tx.tx.authInfo.signerInfosList.signatureKey()?.toKeyValue()
                    it[gasUsed] = tx.txResponse.gasUsed.toInt()
                    it[gasWanted] = tx.txResponse.gasWanted.toInt()
                    it[txTimestamp] = txTime.toDateTime()
                    it[txV2] = tx
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
    var txV2 by TransactionCacheTable.txV2
    override var lastHit by TransactionCacheTable.lastHit
    override var hitCount by TransactionCacheTable.hitCount
}
