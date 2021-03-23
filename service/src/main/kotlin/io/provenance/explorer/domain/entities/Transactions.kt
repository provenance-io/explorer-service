package io.provenance.explorer.domain.entities

import com.google.protobuf.Any
import com.google.protobuf.Timestamp
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toValue
import io.provenance.explorer.domain.models.explorer.GasStatistics
import io.provenance.explorer.domain.models.explorer.TxQueryParams
import io.provenance.explorer.domain.models.explorer.TxStatus
import io.provenance.explorer.domain.models.explorer.getCategoryForType
import io.provenance.explorer.grpc.extensions.getAssociatedAddresses
import io.provenance.explorer.grpc.extensions.getAssociatedDenoms
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Avg
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.Max
import org.jetbrains.exposed.sql.Min
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.append
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.jodatime.CustomDateTimeFunction
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

object TxCacheTable : CacheIdTable<String>(name = "tx_cache") {
    val hash = varchar("hash", 64)
    override val id = hash.entityId()
    val height = reference("height", BlockCacheTable.height)
    val gasWanted = integer("gas_wanted")
    val gasUsed = integer("gas_used")
    val txTimestamp = datetime("tx_timestamp")
    val errorCode = integer("error_code").nullable()
    val codespace = varchar("codespace", 16).nullable()
    val txV2 = jsonb<TxCacheTable, ServiceOuterClass.GetTxResponse>("tx_v2", OBJECT_MAPPER)
}

class TxCacheRecord(id: EntityID<String>) : CacheEntity<String>(id) {
    companion object : CacheEntityClass<String, TxCacheRecord>(TxCacheTable) {
        fun insertIgnore(tx: ServiceOuterClass.GetTxResponse, txTime: Timestamp) =
            transaction {
                (TxCacheRecord.findById(tx.txResponse.txhash)?.id ?: TxCacheTable.insertIgnoreAndGetId {
                    it[hash] = tx.txResponse.txhash
                    it[height] = tx.txResponse.height.toInt()
                    if (tx.txResponse.code > 0) it[errorCode] = tx.txResponse.code
                    if (tx.txResponse.codespace.isNotBlank()) it[codespace] = tx.txResponse.codespace
                    it[gasUsed] = tx.txResponse.gasUsed.toInt()
                    it[gasWanted] = tx.txResponse.gasWanted.toInt()
                    it[txTimestamp] = txTime.toDateTime()
                    it[txV2] = tx
                    it[hitCount] = 0
                    it[lastHit] = DateTime.now()
                }).let {
                    tx.tx.body.messagesList.forEachIndexed { idx, msg ->
                        if (tx.txResponse.logsCount > 0)
                            tx.txResponse.logsList[0].eventsList
                                .filter { event -> event.type == "message" }[idx]
                                .let { event ->
                                    val type = event.attributesList.first { att -> att.key == "action" }.value
                                    val module = event.attributesList.first { att -> att.key == "module" }.value
                                    TxMessageRecord.insert(tx.txResponse.height.toInt(), it!!, msg, type, module)
                                }
                        else
                            TxMessageRecord.insert(tx.txResponse.height.toInt(), it!!, msg, "unknown", "unknown")

                        TxAddressJoinRecord.insert(it!!, tx.txResponse.height.toInt(), msg.getAssociatedAddresses())
                        TxMarkerJoinRecord.insert(it, tx.txResponse.height.toInt(), msg.getAssociatedDenoms())
                    }
                }
                tx.tx.authInfo.signerInfosList.forEach { sig ->
                    SignatureJoinRecord.insert(
                        sig.publicKey,
                        SigJoinType.TRANSACTION,
                        tx.txResponse.txhash
                    )
                }
            }

        fun findByHeight(height: Int) =
            TxCacheRecord.find { TxCacheTable.height eq height }

        fun findSigsByHash(hash: String) = SignatureRecord.findByJoin(SigJoinType.TRANSACTION, hash)

        fun getTotalTxCount() = transaction {
            val count = TxCacheTable.hash.count()
            TxCacheTable.slice(count).selectAll().first()[count].toBigInteger()
        }

        fun getGasStats(startDate: DateTime, endDate: DateTime, granularity: String) = transaction {
            val dateTrunc = CustomDateTimeFunction("DATE_TRUNC", stringLiteral(granularity),  TxCacheTable.txTimestamp)
            val minGas = Min(TxCacheTable.gasUsed, IntegerColumnType())
            val maxGas = Max(TxCacheTable.gasUsed, IntegerColumnType())
            val avgGas = Avg(TxCacheTable.gasUsed, 5)

            TxCacheTable.slice(dateTrunc, minGas, maxGas, avgGas)
                .select { TxCacheTable.txTimestamp.between(startDate, endDate.plusDays(1)) }
                .groupBy(dateTrunc)
                .orderBy(dateTrunc, SortOrder.DESC)
                .map { GasStatistics(
                    it[dateTrunc]!!.withZone(DateTimeZone.UTC).toString("yyyy-MM-dd HH:mm:ss"),
                    it[minGas]!!,
                    it[maxGas]!!,
                    it[avgGas]!!
                ) }
        }

        fun findByQueryForResults(txQueryParams: TxQueryParams) = transaction {
            val query =
                findByQueryParams(txQueryParams, null)
                    .groupBy(TxCacheTable.hash)
                    .orderBy(Pair(TxCacheTable.height, SortOrder.DESC))
                    .limit(txQueryParams.count, txQueryParams.offset.toLong())
            TxCacheRecord.wrapRows(query).toSet()
        }

        fun findByQueryParamsForCount(txQueryParams: TxQueryParams) = transaction {
            val distinctCount = Distinct(TxCacheTable.hash).count()
            findByQueryParams(txQueryParams, listOf(distinctCount)).first()[distinctCount].toBigInteger()
        }

        private fun findByQueryParams(tqp: TxQueryParams, distinctQuery: List<Count>?) = transaction {
            val query =
                TxMessageTable
                    .innerJoin(TxCacheTable, { TxMessageTable.txHash }, { TxCacheTable.hash })
                    .innerJoin(TxMessageTypeTable, { TxMessageTable.txMessageType }, { TxMessageTypeTable.id })
                    .leftJoin(TxAddressJoinTable, { TxMessageTable.txHash }, { TxAddressJoinTable.txHash })
                    .leftJoin(TxMarkerJoinTable, { TxMessageTable.txHash }, { TxMarkerJoinTable.txHash })
                    .slice(distinctQuery ?:
                        listOf(TxCacheTable.hash, TxCacheTable.height, TxCacheTable.gasWanted, TxCacheTable.gasUsed,
                            TxCacheTable.txTimestamp, TxCacheTable.errorCode, TxCacheTable.codespace, TxCacheTable.txV2))
                    .selectAll()

            if (tqp.msgTypes.isNotEmpty())
                query.andWhere { TxMessageTypeTable.type inList tqp.msgTypes }
            if (tqp.txHeight != null)
                query.andWhere { TxCacheTable.height eq tqp.txHeight }
            if (tqp.txStatus != null)
                query.andWhere {
                    if (tqp.txStatus == TxStatus.FAILURE) TxCacheTable.errorCode neq 0 else TxCacheTable.errorCode eq null }
            if (tqp.address != null)
                query.andWhere { TxAddressJoinTable.address eq tqp.address }
            if (tqp.denom != null)
                query.andWhere { TxMarkerJoinTable.denom eq tqp.denom }
            if (tqp.fromDate != null)
                query.andWhere { TxCacheTable.txTimestamp greaterEq tqp.fromDate }
            if (tqp.toDate != null)
                query.andWhere { TxCacheTable.txTimestamp lessEq tqp.toDate.plusDays(1) }

             query
        }

    }

    var hash by TxCacheTable.hash
    var height by TxCacheTable.height
    var gasWanted by TxCacheTable.gasWanted
    var gasUsed by TxCacheTable.gasUsed
    var txTimestamp by TxCacheTable.txTimestamp
    var errorCode by TxCacheTable.errorCode
    var codespace by TxCacheTable.codespace
    var txV2 by TxCacheTable.txV2
    override var lastHit by TxCacheTable.lastHit
    override var hitCount by TxCacheTable.hitCount
    val txMessages by TxMessageRecord referrersOn TxMessageTable.txHash
}

class Distinct(val expr: Expression<String>): Function<String>(VarCharColumnType(128)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        queryBuilder { append("distinct(", expr, ")") }
}

object TxMessageTypeTable : IntIdTable(name = "tx_message_type") {
    val type = varchar("type", 128)
    val module = varchar("module", 128)
    val protoType = varchar("proto_type", 256)
    val category = varchar("category", 128).nullable()
}

class TxMessageTypeRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxMessageTypeRecord>(TxMessageTypeTable) {

        private fun findByProtoType(protoType: String) = transaction {
            TxMessageTypeRecord.find { TxMessageTypeTable.protoType eq protoType }.firstOrNull()
        }

        fun findByType(types: List<String>) = transaction {
            TxMessageTypeRecord.find { TxMessageTypeTable.type inList types }
        }

        fun insert(type: String, module: String, protoType: String) = transaction {
            findByProtoType(protoType)?.let {
                (if (it.type == "unknown" && type != "unknown") {
                    it.apply {
                        this.type = type
                        this.module = module
                    }
                } else it.apply {
                    if (type.getCategoryForType() != null)
                        this.category = type.getCategoryForType()!!.mainCategory
                }).id
            } ?: TxMessageTypeTable.insertAndGetId {
                it[this.type] = type
                it[this.module] = module
                it[this.protoType] = protoType
                if (type.getCategoryForType() != null)
                    it[this.category] = type.getCategoryForType()!!.mainCategory
            }
        }
    }

    var type by TxMessageTypeTable.type
    var module by TxMessageTypeTable.module
    var protoType by TxMessageTypeTable.protoType
    var category by TxMessageTypeTable.category
}

object TxMessageTable : IntIdTable(name = "tx_message") {
    val blockHeight = integer("block_height")
    val txHash = reference("tx_hash", TxCacheTable)
    val txMessageType = reference("tx_message_type_id", TxMessageTypeTable)
    val txMessage = jsonb<TxMessageTable, Any>("tx_message", OBJECT_MAPPER)
    val txMessageHash = text("tx_message_hash")
}

class TxMessageRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxMessageRecord>(TxMessageTable) {

        fun deleteByBlockHeight(heights: List<Int>) = transaction {
            TxMessageTable.deleteWhere { TxMessageTable.blockHeight inList heights }
        }

        fun findByHash(hash: String) = transaction {
            TxMessageRecord.find { TxMessageTable.txHash eq hash }
        }

        private fun findByTxHashAndMessageHash(txHash: String, messageHash: String) = transaction {
            TxMessageRecord.find { (TxMessageTable.txHash eq txHash) and (TxMessageTable.txMessageHash eq messageHash) }
                .firstOrNull()
        }

        fun insert(blockHeight: Int, txHash: EntityID<String>, message: Any, type: String, module: String) =
            transaction {
                TxMessageTypeRecord.insert(type, module, message.typeUrl).let { typeId ->
                    findByTxHashAndMessageHash(txHash.value, message.value.toValue()) ?: TxMessageTable.insert {
                        it[this.blockHeight] = blockHeight
                        it[this.txHash] = txHash
                        it[this.txMessageType] = typeId
                        it[this.txMessage] = message
                        it[this.txMessageHash] = message.value.toValue()
                    }
                }
            }
    }

    var blockHeight by TxMessageTable.blockHeight
    var txHash by TxCacheRecord referencedOn TxMessageTable.txHash
    var txMessageType by TxMessageTypeRecord referencedOn TxMessageTable.txMessageType
    var txMessage by TxMessageTable.txMessage
    var txMessageHash by TxMessageTable.txMessageHash
}

object TxAddressJoinTable : IntIdTable(name = "tx_address_join") {
    val blockHeight = integer("block_height")
    val txHash = reference("tx_hash", TxCacheTable)
    val address = varchar("address", 128)
}

class TxAddressJoinRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxAddressJoinRecord>(TxAddressJoinTable) {

        private fun findByHashAndAddress(txHash: EntityID<String>, address: String) = transaction {
            TxAddressJoinRecord
                .find { (TxAddressJoinTable.txHash eq txHash) and (TxAddressJoinTable.address eq address) }
                .firstOrNull()
        }

        fun findValidatorsByTxHash(txHash: EntityID<String>) = transaction {
            StakingValidatorCacheRecord.wrapRows(
                TxAddressJoinTable
                    .innerJoin(
                        StakingValidatorCacheTable,
                        { TxAddressJoinTable.address },
                        { StakingValidatorCacheTable.operatorAddress })
                    .select { (TxAddressJoinTable.txHash eq txHash) }
            ).toList()
        }

        fun insert(txHash: EntityID<String>, blockHeight: Int, addresses: List<String>) = transaction {
            addresses.forEach { addr ->
                findByHashAndAddress(txHash, addr) ?: TxAddressJoinTable.insert {
                    it[this.blockHeight] = blockHeight
                    it[this.txHash] = txHash
                    it[this.address] = addr
                }
            }
        }
    }

    var blockHeight by TxAddressJoinTable.blockHeight
    var txHash by TxCacheRecord referencedOn TxAddressJoinTable.txHash
    var address by TxAddressJoinTable.address
}

object TxMarkerJoinTable : IntIdTable(name = "tx_marker_join") {
    val blockHeight = integer("block_height")
    val txHash = reference("tx_hash", TxCacheTable)
    val denom = varchar("denom", 128)
}

class TxMarkerJoinRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxMarkerJoinRecord>(TxMarkerJoinTable) {

        private fun findByHashAndDenom(txHash: EntityID<String>, denom: String) = transaction {
            TxMarkerJoinRecord
                .find { (TxMarkerJoinTable.txHash eq txHash) and (TxMarkerJoinTable.denom eq denom) }
                .firstOrNull()
        }

        fun findValidatorsByTxHash(txHash: EntityID<String>) = transaction {
            StakingValidatorCacheRecord.wrapRows(
                TxMarkerJoinTable
                    .innerJoin(
                        StakingValidatorCacheTable,
                        { TxMarkerJoinTable.denom },
                        { StakingValidatorCacheTable.operatorAddress })
                    .select { (TxMarkerJoinTable.txHash eq txHash) }
            ).toList()
        }

        fun insert(txHash: EntityID<String>, blockHeight: Int, denoms: List<String>) = transaction {
            denoms.forEach { addr ->
                findByHashAndDenom(txHash, addr) ?: TxMarkerJoinTable.insert {
                    it[this.blockHeight] = blockHeight
                    it[this.txHash] = txHash
                    it[this.denom] = addr
                }
            }
        }
    }

    var blockHeight by TxMarkerJoinTable.blockHeight
    var txHash by TxCacheRecord referencedOn TxMarkerJoinTable.txHash
    var denom by TxMarkerJoinTable.denom
}
