package io.provenance.explorer.domain.entities

import com.google.protobuf.Any
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.MdParent
import io.provenance.explorer.domain.core.sql.DateTrunc
import io.provenance.explorer.domain.core.sql.Distinct
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.extensions.exec
import io.provenance.explorer.domain.extensions.map
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toDbHash
import io.provenance.explorer.domain.models.explorer.GasStatistics
import io.provenance.explorer.domain.models.explorer.GasStats
import io.provenance.explorer.domain.models.explorer.TxGasVolume
import io.provenance.explorer.domain.models.explorer.TxQueryParams
import io.provenance.explorer.domain.models.explorer.TxStatus
import io.provenance.explorer.domain.models.explorer.getCategoryForType
import io.provenance.explorer.domain.models.explorer.onlyTxQuery
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Avg
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.Max
import org.jetbrains.exposed.sql.Min
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.jodatime.DateColumnType
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

object TxCacheTable : IntIdTable(name = "tx_cache") {
    val hash = varchar("hash", 64)
    val height = reference("height", BlockCacheTable.height)
    val gasWanted = integer("gas_wanted")
    val gasUsed = integer("gas_used")
    val txTimestamp = datetime("tx_timestamp")
    val errorCode = integer("error_code").nullable()
    val codespace = varchar("codespace", 16).nullable()
    val txV2 = jsonb<TxCacheTable, ServiceOuterClass.GetTxResponse>("tx_v2", OBJECT_MAPPER)
}

class TxCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxCacheRecord>(TxCacheTable) {
        fun insertIgnore(tx: ServiceOuterClass.GetTxResponse, txTime: DateTime) =
            transaction {
                (
                    findByHash(tx.txResponse.txhash)?.id ?: TxCacheTable.insertIgnoreAndGetId {
                        it[hash] = tx.txResponse.txhash
                        it[height] = tx.txResponse.height.toInt()
                        if (tx.txResponse.code > 0) it[errorCode] = tx.txResponse.code
                        if (tx.txResponse.codespace.isNotBlank()) it[codespace] = tx.txResponse.codespace
                        it[gasUsed] = tx.txResponse.gasUsed.toInt()
                        it[gasWanted] = tx.txResponse.gasWanted.toInt()
                        it[txTimestamp] = txTime
                        it[txV2] = tx
                    }
                    )!!
            }

        fun findByEntityId(id: EntityID<Int>) = transaction { TxCacheRecord.findById(id) }

        fun findByHeight(height: Int) =
            TxCacheRecord.find { TxCacheTable.height eq height }

        fun findByHash(hash: String) = transaction { TxCacheRecord.find { TxCacheTable.hash eq hash } }.firstOrNull()

        fun findSigsByHash(hash: String) = transaction { SignatureRecord.findByJoin(SigJoinType.TRANSACTION, hash) }

        fun getTotalTxCount() = transaction {
            TxCacheTable.selectAll().count().toBigInteger()
        }

        fun getGasStats(startDate: DateTime, endDate: DateTime, granularity: String) = transaction {
            val dateTrunc = DateTrunc(granularity, TxCacheTable.txTimestamp)
            val minGas = Min(TxCacheTable.gasUsed, IntegerColumnType())
            val maxGas = Max(TxCacheTable.gasUsed, IntegerColumnType())
            val avgGas = Avg(TxCacheTable.gasUsed, 5)

            TxCacheTable.slice(dateTrunc, minGas, maxGas, avgGas)
                .select { TxCacheTable.txTimestamp.between(startDate.startOfDay(), endDate.startOfDay().plusDays(1)) }
                .groupBy(dateTrunc)
                .orderBy(dateTrunc, SortOrder.DESC)
                .map {
                    GasStatistics(
                        it[dateTrunc]!!.withZone(DateTimeZone.UTC).toString("yyyy-MM-dd HH:mm:ss"),
                        it[minGas]!!,
                        it[maxGas]!!,
                        it[avgGas]!!
                    )
                }
        }

        fun findByQueryForResults(txQueryParams: TxQueryParams) = transaction {
            val columns: MutableList<Expression<*>> = mutableListOf()
            if (!txQueryParams.onlyTxQuery())
                columns.add(Distinct(TxCacheTable.id, IntegerColumnType()).alias("dist"))
            columns.addAll(
                mutableListOf(
                    TxCacheTable.id, TxCacheTable.hash,
                    TxCacheTable.height, TxCacheTable.gasWanted, TxCacheTable.gasUsed, TxCacheTable.txTimestamp,
                    TxCacheTable.errorCode, TxCacheTable.codespace, TxCacheTable.txV2
                )
            )
            val query =
                findByQueryParams(txQueryParams, columns)
                    .orderBy(Pair(TxCacheTable.height, SortOrder.DESC))
                    .limit(txQueryParams.count, txQueryParams.offset.toLong())
            TxCacheRecord.wrapRows(query).toSet()
        }

        fun findByQueryParamsForCount(txQueryParams: TxQueryParams) = transaction {
            if (txQueryParams.onlyTxQuery()) {
                findByQueryParams(txQueryParams, null).count().toBigInteger()
            } else {
                val distinctCount = TxCacheTable.id.countDistinct()
                findByQueryParams(txQueryParams, listOf(distinctCount)).first()[distinctCount].toBigInteger()
            }
        }

        private fun findByQueryParams(tqp: TxQueryParams, distinctQuery: List<Expression<*>>?) = transaction {
            var join: ColumnSet = TxCacheTable

            if (tqp.msgTypes.isNotEmpty())
                join = join.innerJoin(TxMessageTable, { TxCacheTable.id }, { TxMessageTable.txHashId })
            if ((tqp.addressId != null && tqp.addressType != null) || tqp.address != null)
                join = join.innerJoin(TxAddressJoinTable, { TxCacheTable.id }, { TxAddressJoinTable.txHashId })
            if (tqp.markerId != null || tqp.denom != null)
                join = join.innerJoin(TxMarkerJoinTable, { TxCacheTable.id }, { TxMarkerJoinTable.txHashId })
            if (tqp.nftId != null)
                join = join.innerJoin(TxNftJoinTable, { TxCacheTable.id }, { TxNftJoinTable.txHashId })

            val query = if (distinctQuery != null) join.slice(distinctQuery).selectAll() else join.selectAll()

            if (tqp.msgTypes.isNotEmpty())
                query.andWhere { TxMessageTable.txMessageType inList tqp.msgTypes }
            if (tqp.txHeight != null)
                query.andWhere { TxCacheTable.height eq tqp.txHeight }
            if (tqp.txStatus != null)
                query.andWhere {
                    if (tqp.txStatus == TxStatus.FAILURE) TxCacheTable.errorCode neq 0 else TxCacheTable.errorCode.isNull()
                }
            if (tqp.addressId != null && tqp.addressType != null)
                query.andWhere { (TxAddressJoinTable.addressId eq tqp.addressId) and (TxAddressJoinTable.addressType eq tqp.addressType) }
            else if (tqp.address != null)
                query.andWhere { (TxAddressJoinTable.address eq tqp.address) }
            if (tqp.markerId != null)
                query.andWhere { TxMarkerJoinTable.markerId eq tqp.markerId }
            else if (tqp.denom != null)
                query.andWhere { TxMarkerJoinTable.denom eq tqp.denom }
            if (tqp.nftId != null && tqp.nftType != null)
                query.andWhere { (TxNftJoinTable.metadataId eq tqp.nftId) and (TxNftJoinTable.metadataType eq tqp.nftType) }
            else if (tqp.nftUuid != null && tqp.nftType != null)
                query.andWhere { (TxNftJoinTable.metadataUuid eq tqp.nftUuid) and (TxNftJoinTable.metadataType eq tqp.nftType) }
            if (tqp.fromDate != null)
                query.andWhere { TxCacheTable.txTimestamp greaterEq tqp.fromDate.startOfDay() }
            if (tqp.toDate != null)
                query.andWhere { TxCacheTable.txTimestamp lessEq tqp.toDate.startOfDay().plusDays(1) }

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
    val txMessages by TxMessageRecord referrersOn TxMessageTable.txHashId
}

object TxMessageTypeTable : IntIdTable(name = "tx_message_type") {
    val type = varchar("type", 128)
    val module = varchar("module", 128)
    val protoType = varchar("proto_type", 256)
    val category = varchar("category", 128).nullable()
}

const val UNKNOWN = "unknown"
class TxMessageTypeRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxMessageTypeRecord>(TxMessageTypeTable) {

        fun findByProtoType(protoType: String) = transaction {
            TxMessageTypeRecord.find { TxMessageTypeTable.protoType eq protoType }.firstOrNull()
        }

        fun findByType(types: List<String>) = transaction {
            TxMessageTypeRecord.find { TxMessageTypeTable.type inList types }
        }

        fun findByIdIn(idList: List<Int>) = transaction {
            TxMessageTypeRecord.find { TxMessageTypeTable.id inList idList }.toList()
        }

        fun insert(type: String, module: String, protoType: String) = transaction {
            findByProtoType(protoType)?.apply {
                if (this.type == UNKNOWN) this.type = type
                if (this.module == UNKNOWN) this.module = module
                if (type.getCategoryForType() != null) this.category = type.getCategoryForType()!!.mainCategory
            }?.id ?: TxMessageTypeTable.insertAndGetId {
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
    val txHash = varchar("tx_hash", 64)
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txMessageType = reference("tx_message_type_id", TxMessageTypeTable)
    val txMessage = jsonb<TxMessageTable, Any>("tx_message", OBJECT_MAPPER)
    val txMessageHash = text("tx_message_hash")
    val msgIdx = integer("msg_idx")
}

class TxMessageRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxMessageRecord>(TxMessageTable) {

        fun findByHash(hash: String) = transaction {
            TxMessageRecord.find { TxMessageTable.txHash eq hash }
        }

        fun getCountByHashId(hashId: Int, msgTypes: List<Int>) = transaction {
            TxMessageRecord.find {
                (TxMessageTable.txHashId eq hashId) and
                    (if (msgTypes.isNotEmpty()) (TxMessageTable.txMessageType inList msgTypes) else (Op.TRUE))
            }
                .count()
        }

        fun findByHashIdPaginated(hashId: Int, msgTypes: List<Int>, limit: Int, offset: Int) = transaction {
            TxMessageRecord.find {
                (TxMessageTable.txHashId eq hashId) and
                    (if (msgTypes.isNotEmpty()) (TxMessageTable.txMessageType inList msgTypes) else (Op.TRUE))
            }
                .orderBy(Pair(TxMessageTable.id, SortOrder.ASC))
                .limit(limit, offset.toLong())
                .toMutableList()
        }

        fun getDistinctTxMsgTypesByTxHash(txHashId: EntityID<Int>) = transaction {
            val msgIdDist = Distinct(TxMessageTable.txMessageType, IntegerColumnType()).alias("dist")
            TxMessageTable.slice(msgIdDist, TxMessageTable.txMessageType).select { TxMessageTable.txHashId eq txHashId }
                .map { it[TxMessageTable.txMessageType].value }
        }

        private fun findByTxHashAndMessageHash(txHashId: Int, messageHash: String, msgIdx: Int) = transaction {
            TxMessageRecord.find {
                (TxMessageTable.txHashId eq txHashId) and
                    (TxMessageTable.txMessageHash eq messageHash) and
                    (TxMessageTable.msgIdx eq msgIdx)
            }
                .firstOrNull()
        }

        fun findByQueryForResults(txQueryParams: TxQueryParams) = transaction {
            val columns = mutableListOf(
                TxMessageTable.id, TxMessageTable.blockHeight, TxMessageTable.txHash,
                TxMessageTable.txHashId, TxMessageTable.txMessageType, TxMessageTable.txMessage
            )
            val query =
                findByQueryParams(txQueryParams, columns)
                    .orderBy(Pair(TxMessageTable.blockHeight, SortOrder.DESC))
                    .limit(txQueryParams.count, txQueryParams.offset.toLong())
            TxMessageRecord.wrapRows(query).toSet()
        }

        fun findByQueryParamsForCount(txQueryParams: TxQueryParams) = transaction {
            val distinctCount = TxMessageTable.id.countDistinct()
            findByQueryParams(txQueryParams, listOf(distinctCount)).first()[distinctCount].toBigInteger()
        }

        private fun findByQueryParams(tqp: TxQueryParams, distinctQuery: List<Expression<*>>?) = transaction {
            var join: ColumnSet = TxCacheTable

            if (tqp.msgTypes.isNotEmpty())
                join = join.innerJoin(TxMessageTable, { TxCacheTable.id }, { TxMessageTable.txHashId })
            if ((tqp.addressId != null && tqp.addressType != null) || tqp.address != null)
                join = join.innerJoin(TxAddressJoinTable, { TxCacheTable.id }, { TxAddressJoinTable.txHashId })

            val query = if (distinctQuery != null) join.slice(distinctQuery).selectAll() else join.selectAll()

            if (tqp.msgTypes.isNotEmpty())
                query.andWhere { TxMessageTable.txMessageType inList tqp.msgTypes }
            if (tqp.txHeight != null)
                query.andWhere { TxCacheTable.height eq tqp.txHeight }
            if (tqp.txStatus != null)
                query.andWhere {
                    if (tqp.txStatus == TxStatus.FAILURE) TxCacheTable.errorCode neq 0 else TxCacheTable.errorCode.isNull()
                }
            if (tqp.addressId != null && tqp.addressType != null)
                query.andWhere { (TxAddressJoinTable.addressId eq tqp.addressId) and (TxAddressJoinTable.addressType eq tqp.addressType) }
            else if (tqp.address != null)
                query.andWhere { (TxAddressJoinTable.address eq tqp.address) }
            if (tqp.fromDate != null)
                query.andWhere { TxCacheTable.txTimestamp greaterEq tqp.fromDate.startOfDay() }
            if (tqp.toDate != null)
                query.andWhere { TxCacheTable.txTimestamp lessEq tqp.toDate.startOfDay().plusDays(1) }

            query
        }

        fun insert(blockHeight: Int, txHash: String, txId: EntityID<Int>, message: Any, type: String, module: String, msgIdx: Int) =
            transaction {
                TxMessageTypeRecord.insert(type, module, message.typeUrl).let { typeId ->
                    findByTxHashAndMessageHash(txId.value, message.value.toDbHash(), msgIdx)?.id
                        ?: TxMessageTable.insertAndGetId {
                            it[this.blockHeight] = blockHeight
                            it[this.txHash] = txHash
                            it[this.txHashId] = txId
                            it[this.txMessageType] = typeId
                            it[this.txMessage] = message
                            it[this.txMessageHash] = message.value.toDbHash()
                            it[this.msgIdx] = msgIdx
                        }
                }
            }
    }

    var blockHeight by TxMessageTable.blockHeight
    var txHashId by TxCacheRecord referencedOn TxMessageTable.txHashId
    var txHash by TxMessageTable.txHash
    var txMessageType by TxMessageTypeRecord referencedOn TxMessageTable.txMessageType
    var txMessage by TxMessageTable.txMessage
    var txMessageHash by TxMessageTable.txMessageHash
    var msgIdx by TxMessageTable.msgIdx
}

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

object TxEventsTable : IntIdTable(name = "tx_msg_event") {
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txMessageId = integer("tx_message_id")
    val txMsgTypeId = varchar("tx_message_type_id", 128)
    val eventType = varchar("event_type", 256)
}

class TxEventRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxEventRecord>(TxEventsTable) {

        private fun findByTxHashIdMessageIdAndEventType(txHashId: Int, messageId: Int, eventType: String) = transaction {
            TxEventRecord.find { (TxEventsTable.txHashId eq txHashId) and (TxEventsTable.txMessageId eq messageId) and (TxEventsTable.eventType eq eventType) }
                .firstOrNull()
        }

        fun insert(blockHeight: Int, txHash: String, txId: EntityID<Int>, type: String, msgId: Int, msgTypeId: String) =
            transaction {
                findByTxHashIdMessageIdAndEventType(txId.value, msgId, type)?.id ?: TxEventsTable.insertAndGetId {
                    it[this.blockHeight] = blockHeight
                    it[this.txHash] = txHash
                    it[this.txHashId] = txId
                    it[this.txMessageId] = msgId
                    it[this.txMsgTypeId] = msgTypeId
                    it[this.eventType] = type
                }
            }
    }

    var blockHeight by TxEventsTable.blockHeight
    var txHash by TxEventsTable.txHash
    var txHashId by TxEventsTable.txHashId
    var txMessageId by TxEventsTable.txMessageId
    var txMsgTypeId by TxEventsTable.txMsgTypeId
    var eventType by TxEventsTable.eventType
}

object TxEventAttrTable : IntIdTable(name = "tx_msg_event_attr") {
    val txMsgEventId = integer("tx_msg_event_id")
    val attrKey = varchar("attr_key", 256)
    val attrValue = text("attr_value")
}

class TxEventAttrRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxEventAttrRecord>(TxEventAttrTable) {

        private fun findByTxMsgEventIdAndKey(eventId: Int, attrKey: String) = transaction {
            TxEventAttrRecord.find { (TxEventAttrTable.txMsgEventId eq eventId) and (TxEventAttrTable.attrKey eq attrKey) }
                .firstOrNull()
        }

        fun insert(key: String, value: String, eventId: Int) =
            transaction {
                findByTxMsgEventIdAndKey(eventId, key) ?: TxEventAttrTable.insert {
                    it[this.txMsgEventId] = eventId
                    it[this.attrKey] = key
                    it[this.attrValue] = value
                }
            }
    }

    var txMsgEventId by TxEventAttrTable.txMsgEventId
    var attrKey by TxEventAttrTable.attrKey
    var attrValue by TxEventAttrTable.attrValue
}

object TxSingleMessageCacheTable : IntIdTable(name = "tx_single_message_cache") {
    val txTimestamp = datetime("tx_timestamp")
    val txHash = varchar("tx_hash", 64)
    val gasUsed = integer("gas_used")
    val txMessageType = varchar("tx_message_type", 128)
    val processed = bool("processed")
}

class TxSingleMessageCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxSingleMessageCacheRecord>(TxSingleMessageCacheTable) {

        fun insert(txTime: DateTime, txHash: String, gasUsed: Int, type: String) = transaction {
            TxSingleMessageCacheTable.insertIgnore {
                it[this.txTimestamp] = txTime
                it[this.txHash] = txHash
                it[this.gasUsed] = gasUsed
                it[this.txMessageType] = type
                it[this.processed] = false
            }
        }

        fun getGasStats(fromDate: DateTime, toDate: DateTime, granularity: String) = transaction {
            val tblName = "tx_single_message_gas_stats_${granularity.lowercase()}"
            val query = """
                |SELECT
                |   $tblName.tx_timestamp,
                |   $tblName.min_gas_used,
                |   $tblName.max_gas_used,
                |   $tblName.avg_gas_used,
                |   $tblName.stddev_gas_used,
                |   $tblName.tx_message_type
                |FROM $tblName
                |WHERE $tblName.tx_timestamp >= ? 
                |  AND $tblName.tx_timestamp < ?
                |ORDER BY $tblName.tx_timestamp DESC
                |""".trimMargin()

            val dateTimeType = DateColumnType(true)
            val arguments = listOf<Pair<DateColumnType, DateTime>>(
                Pair(dateTimeType, fromDate.startOfDay()),
                Pair(dateTimeType, toDate.startOfDay().plusDays(1))
            )

            val tz = DateTimeZone.UTC
            val pattern = "yyyy-MM-dd HH:mm:ss"

            query.exec(arguments)
                .map {
                    GasStats(
                        it.getTimestamp("tx_timestamp").toDateTime(tz, pattern),
                        it.getInt("min_gas_used"),
                        it.getInt("max_gas_used"),
                        it.getInt("avg_gas_used"),
                        it.getInt("stddev_gas_used"),
                        it.getString("tx_message_type")
                    )
                }
        }

        fun updateGasStats(): Unit = transaction {
            val conn = TransactionManager.current().connection
            val queries = listOf("CALL update_gas_fee_stats()")
            conn.executeInBatch(queries)
        }
    }

    var txTimestamp by TxSingleMessageCacheTable.txTimestamp
    var txHash by TxSingleMessageCacheTable.txHash
    var gasUsed by TxSingleMessageCacheTable.gasUsed
    var txMessageType by TxSingleMessageCacheTable.txMessageType
    var processed by TxSingleMessageCacheTable.processed
}

object TxGasCacheTable : IntIdTable(name = "tx_gas_cache") {
    val hash = varchar("hash", 64)
    val txTimestamp = datetime("tx_timestamp")
    val gasWanted = integer("gas_wanted").nullable()
    val gasUsed = integer("gas_used")
    val feeAmount = double("fee_amount").nullable()
    val processed = bool("processed")
}

class TxGasCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxGasCacheRecord>(TxGasCacheTable) {

        fun insertIgnore(tx: ServiceOuterClass.GetTxResponse, txTime: DateTime) =
            transaction {
                if (findByHash(tx.txResponse.txhash) == null) {
                    // calculate fee amount
                    val authInfo = tx.txResponse.tx.unpack(TxOuterClass.Tx::class.java).authInfo
                    val fee = authInfo.fee.amountList.firstOrNull()?.amount?.toDouble() ?: 0.0

                    TxGasCacheTable.insertIgnore {
                        it[hash] = tx.txResponse.txhash
                        it[txTimestamp] = txTime
                        it[gasUsed] = tx.txResponse.gasUsed.toInt()
                        it[gasWanted] = tx.txResponse.gasWanted.toInt()
                        it[feeAmount] = fee
                        it[processed] = false // default
                    }
                }
            }

        private fun findByHash(hash: String) = transaction {
            TxGasCacheRecord.find { TxGasCacheTable.hash eq hash }
        }.firstOrNull()

        fun getGasVolume(fromDate: DateTime, toDate: DateTime, granularity: String) = transaction {
            val tblName = "tx_gas_fee_volume_${granularity.lowercase()}"
            val query = """
                |SELECT
                |   $tblName.tx_timestamp,
                |   $tblName.gas_wanted,
                |   $tblName.gas_used,
                |   $tblName.fee_amount
                |FROM $tblName
                |WHERE $tblName.tx_timestamp >= ? 
                |  AND $tblName.tx_timestamp < ?
                |ORDER BY $tblName.tx_timestamp DESC
                |""".trimMargin()

            val dateTimeType = DateColumnType(true)
            val arguments = listOf<Pair<DateColumnType, DateTime>>(
                Pair(dateTimeType, fromDate.startOfDay()),
                Pair(dateTimeType, toDate.startOfDay().plusDays(1))
            )

            val tz = DateTimeZone.UTC
            val pattern = "yyyy-MM-dd HH:mm:ss"

            query.exec(arguments)
                .map {
                    TxGasVolume(
                        it.getTimestamp("tx_timestamp").toDateTime(tz, pattern),
                        it.getBigDecimal("gas_wanted").toBigInteger(),
                        it.getBigDecimal("gas_used").toBigInteger(),
                        it.getBigDecimal("fee_amount")
                    )
                }
        }

        fun updateGasFeeVolume(): Unit = transaction {
            val conn = TransactionManager.current().connection
            val queries = listOf("CALL update_gas_fee_volume()")
            conn.executeInBatch(queries)
        }
    }

    var hash by TxGasCacheTable.hash
    var txTimestamp by TxGasCacheTable.txTimestamp
    var gasWanted by TxGasCacheTable.gasWanted
    var gasUsed by TxGasCacheTable.gasUsed
    var feeAmount by TxGasCacheTable.feeAmount
    var processed by TxGasCacheTable.processed
}
