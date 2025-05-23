package io.provenance.explorer.domain.entities

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.protobuf.Any
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.VANILLA_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.sql.jsonb
import io.provenance.explorer.domain.core.sql.toProcedureObject
import io.provenance.explorer.domain.extensions.CUSTOM_FEE_MSG_TYPE
import io.provenance.explorer.domain.extensions.exec
import io.provenance.explorer.domain.extensions.execAndMap
import io.provenance.explorer.domain.extensions.getCustomFeeProtoType
import io.provenance.explorer.domain.extensions.getEventMsgFeesType
import io.provenance.explorer.domain.extensions.identifyMsgBasedFeesOld
import io.provenance.explorer.domain.extensions.map
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.extensions.stringify
import io.provenance.explorer.domain.extensions.success
import io.provenance.explorer.domain.extensions.toDbHash
import io.provenance.explorer.domain.models.explorer.CustomFeeList
import io.provenance.explorer.domain.models.explorer.EventFee
import io.provenance.explorer.domain.models.explorer.MsgProtoBreakout
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxFeeData
import io.provenance.explorer.domain.models.explorer.TxQueryParams
import io.provenance.explorer.domain.models.explorer.TxUpdate
import io.provenance.explorer.domain.models.explorer.getCategoryForType
import io.provenance.explorer.grpc.extensions.denomAmountToPair
import io.provenance.explorer.grpc.extensions.findAllMatchingEvents
import io.provenance.explorer.grpc.extensions.removeFirstSlash
import io.provenance.explorer.grpc.v1.MsgFeeGrpcClient
import io.provenance.explorer.model.CustomFee
import io.provenance.explorer.model.FeeCoinStr
import io.provenance.explorer.model.GasStats
import io.provenance.explorer.model.TxAssociatedValues
import io.provenance.explorer.model.TxFeepayer
import io.provenance.explorer.model.TxGasVolume
import io.provenance.explorer.model.TxStatus
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.model.base.stringfy
import io.provenance.explorer.service.AssetService
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.javatime.JavaLocalDateTimeColumnType
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

        fun insertToProcedure(txUpdate: TxUpdate, height: Int, timestamp: LocalDateTime) = transaction {
            val txStr = txUpdate.toProcedureObject()
            val query = "CALL add_tx($txStr, $height, '${timestamp.toProcedureObject()}')"
            this.exec(query)
        }

        fun getAssociatedValues(txHash: String, txHeight: Int) = transaction {
            val query = "SELECT * FROM get_tx_associated_values(?, ?)".trimIndent()
            val arguments = mutableListOf<Pair<ColumnType, *>>(
                Pair(TextColumnType(), txHash),
                Pair(IntegerColumnType(), txHeight)
            )
            query.execAndMap(arguments) { TxAssociatedValues(it.getString("value"), it.getString("type")) }
        }

        fun buildInsert(tx: ServiceOuterClass.GetTxResponse, txTime: LocalDateTime) =
            listOf(
                tx.txResponse.txhash,
                tx.txResponse.height.toInt(),
                tx.txResponse.gasWanted.toInt(),
                tx.txResponse.gasUsed.toInt(),
                txTime,
                if (tx.txResponse.code > 0) tx.txResponse.code else null,
                tx.txResponse.codespace.ifBlank { null },
                tx,
                0
            ).toProcedureObject()

        fun findByHeight(height: Int) =
            TxCacheRecord.find { TxCacheTable.height eq height }

        fun findByHash(hash: String) = transaction {
            TxCacheRecord.find { TxCacheTable.hash eq hash }
                .orderBy(Pair(TxCacheTable.height, SortOrder.DESC))
                .toList()
        }

        fun getTotalTxCount() = transaction {
            TxCacheTable.selectAll().count().toBigInteger()
        }

        fun getTotalTxCountToDate(toDate: LocalDateTime) = transaction {
            TxCacheTable.select { TxCacheTable.txTimestamp lessEq toDate }.count().toBigInteger()
        }

        fun findByQueryForResults(txQueryParams: TxQueryParams) = transaction {
            val columns = TxCacheTable.columns.toMutableList()
            val query = findByQueryParams(txQueryParams, columns)
            if (!txQueryParams.onlyTxQuery()) {
                query.groupBy(*TxCacheTable.columns.toTypedArray())
            }
            query.orderBy(Pair(TxCacheTable.height, SortOrder.DESC))
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

            if (tqp.msgTypes.isNotEmpty()) {
                join = join.innerJoin(TxMsgTypeQueryTable, { TxCacheTable.id }, { txHashId })
            }
            if ((tqp.addressId != null && tqp.addressType != null) || tqp.address != null) {
                join = join.innerJoin(TxAddressJoinTable, { TxCacheTable.id }, { txHashId })
            }
            if (tqp.markerId != null || tqp.denom != null) {
                join = join.innerJoin(TxMarkerJoinTable, { TxCacheTable.id }, { txHashId })
            }
            if (tqp.nftId != null) {
                join = join.innerJoin(TxNftJoinTable, { TxCacheTable.id }, { txHashId })
            }
            if (tqp.ibcChannelIds.isNotEmpty()) {
                join = join.innerJoin(TxIbcTable, { TxCacheTable.id }, { txHashId })
            }

            val query = if (distinctQuery != null) join.slice(distinctQuery).selectAll() else join.selectAll()

            if (tqp.msgTypes.isNotEmpty()) {
                query.andWhere { TxMsgTypeQueryTable.typeId inList tqp.msgTypes }
            }
            if (tqp.txHeight != null) {
                query.andWhere { TxCacheTable.height eq tqp.txHeight }
            }
            if (tqp.txStatus != null) {
                query.andWhere {
                    if (tqp.txStatus == TxStatus.FAILURE) TxCacheTable.errorCode neq 0 else TxCacheTable.errorCode.isNull()
                }
            }
            if (tqp.addressId != null && tqp.addressType != null) {
                query.andWhere { (TxAddressJoinTable.addressId eq tqp.addressId) and (TxAddressJoinTable.addressType eq tqp.addressType) }
            } else if (tqp.address != null) {
                query.andWhere { (TxAddressJoinTable.address eq tqp.address) }
            }
            if (tqp.markerId != null) {
                query.andWhere { TxMarkerJoinTable.markerId eq tqp.markerId }
            } else if (tqp.denom != null) {
                query.andWhere { TxMarkerJoinTable.denom eq tqp.denom }
            }
            if (tqp.nftId != null && tqp.nftType != null) {
                query.andWhere { (TxNftJoinTable.metadataId eq tqp.nftId) and (TxNftJoinTable.metadataType eq tqp.nftType) }
            } else if (tqp.nftUuid != null && tqp.nftType != null) {
                query.andWhere { (TxNftJoinTable.metadataUuid eq tqp.nftUuid) and (TxNftJoinTable.metadataType eq tqp.nftType) }
            }
            if (tqp.fromDate != null) {
                query.andWhere { TxCacheTable.txTimestamp greaterEq tqp.fromDate.startOfDay() }
            }
            if (tqp.toDate != null) {
                query.andWhere { TxCacheTable.txTimestamp lessEq tqp.toDate.startOfDay().plusDays(1) }
            }
            if (tqp.ibcChannelIds.isNotEmpty()) {
                query.andWhere { TxIbcTable.channelId inList tqp.ibcChannelIds }
            }

            query
        }

        fun countForDates(daysPrior: Int, atDateTime: LocalDateTime? = null): List<Pair<LocalDate, Long>> = transaction {
            val atDateQuery = atDateTime?.let {
                "tx_timestamp between '${atDateTime.minusDays(daysPrior.toLong()).toLocalDate()}' and '${atDateTime.toLocalDate()}'"
            } ?: "tx_timestamp > current_timestamp - interval '$daysPrior days'"

            val query = """
                select sum(daily_tx_cnt.cnt) as count, ds
                from (select count(*) cnt, tx_timestamp ts, date_trunc('day', tx_timestamp) ds
                      from tx_cache
                      where $atDateQuery
                      group by ts, ds) as daily_tx_cnt
                group by ds
                order by ds;
            """.trimIndent()
            query.execAndMap {
                Pair(
                    it.getTimestamp("ds").toLocalDateTime().toLocalDate(),
                     it.getLong("count")
                )
            }
        }

        fun pulseTransactionsWithValue(
            denom: String, afterDateTime: LocalDateTime,
                                       page: Int, count: Int,
                                       sort: List<SortOrder>, sortColumn: List<String>
        ): PagedResults<Map<String, kotlin.Any?>> = transaction {
            /* This is simultaneously the scariest and most beautiful query I've ever seen. */
            val query = """
                select tx.id as tx_id,
                       tx.hash,
                       tx.height,
                       tx.tx_timestamp,
                       mtype.type,
                       attr_denom_value.denom as denom,
                       sum(attr_denom_value.value) as denom_total
                from tx_cache tx
                         join tx_msg_event as tme on tx.id = tme.tx_hash_id
                         join tx_message_type as mtype on mtype.id = tme.tx_msg_type_id
                         join tx_marker_join as denom on denom.tx_hash_id = tx.id
                         join
                     (SELECT attr.tx_msg_event_id,
                             substring(rec from '[0-9]+(.*)${'$'}')     AS denom,
                             substring(rec from '^[0-9]+')::bigint AS value
                      FROM tx_msg_event_attr attr
                               CROSS JOIN LATERAL unnest(string_to_array(replace(attr.attr_value,'"',''), ',')) AS rec
                           where attr.attr_key = 'amount') as attr_denom_value
                     on attr_denom_value.tx_msg_event_id = tme.id
                where tme.tx_msg_type_id IN
                      (select id from tx_message_type where module in ('exchange', 'bank'))
                  and tx.tx_timestamp > ?
                  and tx.error_code is null
                  and tx.codespace is null
                  and denom.denom = ?
                  and event_type = 'coin_spent'
                  and attr_denom_value.denom = ?
                group by tx_id, tx.hash, tx.height, tx.tx_timestamp, mtype.type, attr_denom_value.denom                
            """.trimIndent()
            val arguments = mutableListOf<Pair<ColumnType, *>>(
                Pair(JavaLocalDateTimeColumnType(), afterDateTime),
                Pair(TextColumnType(), denom),
                Pair(TextColumnType(), denom),
            )

            val countQuery = "select count(*) from ($query) as count"
            val rowCount = countQuery.execAndMap(arguments) {
                it.getLong(1)
            }.first()

            arguments.add(Pair(IntegerColumnType(), count))
            arguments.add(Pair(IntegerColumnType(), page * count))

            val sortExpr = "order by " + if (sort.isNotEmpty() && sort.size == sortColumn.size)
                List(sort.size) { i -> "${sortColumn[i]} ${sort[i]} " }.joinToString(",")
            else "height desc, tx_id "

            "$query $sortExpr limit ? offset ?".execAndMap(arguments) {
                    val map = mutableMapOf<String, kotlin.Any?>()
                    (1..it.metaData.columnCount).forEach { index ->
                        map[it.metaData.getColumnName(index)] = it.getObject(index)
                    }
                    map // return a list of map because i like to party
                }.let {
                    PagedResults(rowCount.div(count).toInt(), it, rowCount, emptyMap())
                }
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
    val txFees by TxFeeRecord referrersOn TxFeeTable.txHashId
    val txFeepayer by TxFeepayerRecord referrersOn TxFeepayerTable.txHashId
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

        fun findByProtoTypeIn(protoType: List<String>) = transaction {
            TxMessageTypeRecord.find { TxMessageTypeTable.protoType inList protoType }.map { it.id.value }
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
                if (type.getCategoryForType() != null) {
                    it[this.category] = type.getCategoryForType()!!.mainCategory
                }
            }
        }
    }

    var type by TxMessageTypeTable.type
    var module by TxMessageTypeTable.module
    var protoType by TxMessageTypeTable.protoType
    var category by TxMessageTypeTable.category
}

object TxMsgTypeSubtypeTable : IntIdTable(name = "tx_msg_type_subtype") {
    val txTimestamp = datetime("tx_timestamp")
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txMsgId = reference("tx_msg_id", TxMessageTable)
    val primaryType = reference("primary_type_id", TxMessageTypeTable)
    val secondaryType = optReference("secondary_type_id", TxMessageTypeTable)
}

class TxMsgTypeSubtypeRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxMsgTypeSubtypeRecord>(TxMsgTypeSubtypeTable) {

        fun buildInserts(primary: MsgProtoBreakout, secondaries: List<MsgProtoBreakout>, txInfo: TxData) = transaction {
            val primId = TxMessageTypeRecord.insert(primary.type, primary.module, primary.proto)
            val recs =
                if (secondaries.isNotEmpty()) {
                    secondaries.map {
                        listOf(
                            0,
                            0,
                            primId.value,
                            TxMessageTypeRecord.insert(it.type, it.module, it.proto).value,
                            txInfo.txTimestamp,
                            0,
                            txInfo.blockHeight,
                            txInfo.txHash
                        ).toProcedureObject()
                    }
                } else {
                    listOf(
                        listOf(
                            0,
                            0,
                            primId.value,
                            null,
                            txInfo.txTimestamp,
                            0,
                            txInfo.blockHeight,
                            txInfo.txHash
                        ).toProcedureObject()
                    )
                }
            primId to recs
        }
    }

    var txMsgId by TxMsgTypeSubtypeTable.txMsgId
    var primaryType by TxMessageTypeRecord referencedOn TxMsgTypeSubtypeTable.primaryType
    var secondaryType by TxMessageTypeRecord optionalReferencedOn TxMsgTypeSubtypeTable.secondaryType
    var blockHeight by TxMsgTypeSubtypeTable.blockHeight
    var txHashId by TxCacheRecord referencedOn TxMsgTypeSubtypeTable.txHashId
    var txHash by TxMsgTypeSubtypeTable.txHash
    var txTimestamp by TxMsgTypeSubtypeTable.txTimestamp
}

object TxMsgTypeQueryTable : IntIdTable(name = "tx_msg_type_query") {
    val txHashId = integer("tx_hash_id")
    val txMsgId = integer("tx_msg_id")
    val typeId = integer("type_id")
}

object TxMessageTable : IntIdTable(name = "tx_message") {
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txMessage = jsonb<TxMessageTable, Any>("tx_message", OBJECT_MAPPER)
    val txMessageHash = text("tx_message_hash")
    val msgIdx = integer("msg_idx")
    val txTimestamp = datetime("tx_timestamp")
}

class TxMessageRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxMessageRecord>(TxMessageTable) {
        val tableColSet = TxMessageTable.columns.toMutableList()

        fun findByHash(hash: String) = transaction {
            TxMessageRecord.find { TxMessageTable.txHash eq hash }
        }

        fun getCountByHashId(hashId: Int, msgTypes: List<Int>) = transaction {
            val distinctCount = TxMsgTypeQueryTable.txMsgId.countDistinct()
            val query = TxMsgTypeQueryTable
                .slice(distinctCount)
                .select { TxMsgTypeQueryTable.txHashId eq hashId }
            if (msgTypes.isNotEmpty()) {
                query.andWhere { TxMsgTypeQueryTable.typeId inList msgTypes }
            }
            query.first()[distinctCount].toBigInteger()
        }

        fun findByHashIdPaginated(hashId: Int, msgTypes: List<Int>, limit: Int, offset: Int) = transaction {
            val query = TxMessageTable
                .innerJoin(TxMsgTypeSubtypeTable, { TxMessageTable.id }, { txMsgId })
                .slice(tableColSet)
                .select { TxMessageTable.txHashId eq hashId }
            if (msgTypes.isNotEmpty()) {
                query.andWhere { TxMsgTypeQueryTable.typeId inList msgTypes }
            }
            query
                .orderBy(Pair(TxMessageTable.msgIdx, SortOrder.ASC))
                .limit(limit, offset.toLong())
                .let { TxMessageRecord.wrapRows(it).toMutableList() }
        }

        fun getDistinctTxMsgTypesByTxHash(txHashId: EntityID<Int>) = transaction {
            val query = """
                select c.tx_type
                from tx_message tm
                      join tx_msg_type_subtype tmts on tm.id = tmts.tx_msg_id
                      cross join lateral ( values (primary_type_id), (secondary_type_id) ) c(tx_type)
                where tm.tx_hash_id = ?;
            """.trimIndent()
            val arguments = mutableListOf(Pair(IntegerColumnType(), txHashId.value))
            query.execAndMap(arguments) { it.getInt("tx_type") }
        }

        fun findByQueryForResults(txQueryParams: TxQueryParams) = transaction {
            findByQueryParams(txQueryParams, tableColSet).let {
                // Because of the way the db takes hints on when to materialize data for sorting, it is in our best interest
                // to sort by the deepest level of the inner joins that have been executed. In this case this will yield a
                // query that takes less than a second whereas ordering by the same field in the tx message table takes 18 minutes.
                if ((txQueryParams.addressId != null && txQueryParams.addressType != null) || txQueryParams.address != null)
                    it.orderBy(Pair(TxAddressJoinTable.blockHeight, SortOrder.DESC))
                else
                    it.orderBy(Pair(TxMessageTable.blockHeight, SortOrder.DESC))
            }.let {
                it.limit(txQueryParams.count, txQueryParams.offset.toLong())
            }.let {
                TxMessageRecord.wrapRows(it).toSet()
            }
        }

        fun findByQueryParamsForCount(txQueryParams: TxQueryParams) = transaction {
            val distinctCount = TxMessageTable.id.countDistinct()
            findByQueryParams(txQueryParams, listOf(distinctCount)).first()[distinctCount].toBigInteger()
        }

        private fun findByQueryParams(tqp: TxQueryParams, distinctQuery: List<Expression<*>>?) = transaction {
            logger().info("Query Params: $tqp")

            var join: ColumnSet = TxMessageTable

            if (tqp.msgTypes.isNotEmpty())
                join = if (tqp.primaryTypesOnly)
                    join.innerJoin(TxMsgTypeSubtypeTable, { TxMessageTable.txHashId }, { txHashId })
                else
                    join.innerJoin(TxMsgTypeQueryTable, { TxMessageTable.txHashId }, { txHashId })
            if (tqp.txStatus != null)
                join = join.innerJoin(TxCacheTable, { TxMessageTable.txHashId }, { TxCacheTable.id })
            if ((tqp.addressId != null && tqp.addressType != null) || tqp.address != null)
                join = join.innerJoin(TxAddressJoinTable, { TxMessageTable.txHashId }, { txHashId })
            if (tqp.smCodeId != null)
                join = join.innerJoin(TxSmCodeTable, { TxMessageTable.txHashId }, { txHashId })
            if (tqp.smContractAddrId != null)
                join = join.innerJoin(TxSmContractTable, { TxMessageTable.txHashId }, { txHashId })

            val query = if (distinctQuery != null) join.slice(distinctQuery).selectAll() else join.selectAll()

            if (tqp.msgTypes.isNotEmpty())
                if (tqp.primaryTypesOnly) query.andWhere { TxMsgTypeSubtypeTable.primaryType inList tqp.msgTypes }
                else query.andWhere { TxMsgTypeQueryTable.typeId inList tqp.msgTypes }
            if (tqp.txHeight != null)
                query.andWhere { TxMessageTable.blockHeight eq tqp.txHeight }
            if (tqp.txStatus != null)
                query.andWhere {
                    if (tqp.txStatus == TxStatus.FAILURE) TxCacheTable.errorCode neq 0 else TxCacheTable.errorCode.isNull()
                }
            if (tqp.addressId != null && tqp.addressType != null)
                query.andWhere { (TxAddressJoinTable.addressId eq tqp.addressId) and (TxAddressJoinTable.addressType eq tqp.addressType) }
            else if (tqp.address != null)
                query.andWhere { (TxAddressJoinTable.address eq tqp.address) }
            if (tqp.smCodeId != null)
                query.andWhere { (TxSmCodeTable.smCode eq tqp.smCodeId) }
            if (tqp.smContractAddrId != null)
                query.andWhere { (TxSmContractTable.contractId eq tqp.smContractAddrId) }
            if (tqp.fromDate != null)
                query.andWhere { TxMessageTable.txTimestamp greaterEq tqp.fromDate.startOfDay() }
            if (tqp.toDate != null)
                query.andWhere { TxMessageTable.txTimestamp lessEq tqp.toDate.startOfDay().plusDays(1) }

            query
        }

        fun buildInsert(txInfo: TxData, message: Any, msgIdx: Int) = transaction {
            listOf(
                0,
                txInfo.blockHeight,
                txInfo.txHash,
                message,
                message.value.toDbHash(),
                0,
                msgIdx,
                txInfo.txTimestamp
            ).toProcedureObject()
        }
    }

    var blockHeight by TxMessageTable.blockHeight
    var txHashId by TxCacheRecord referencedOn TxMessageTable.txHashId
    var txHash by TxMessageTable.txHash
    val txMessageType by TxMsgTypeSubtypeRecord referrersOn TxMsgTypeSubtypeTable.txMsgId
    var txMessage by TxMessageTable.txMessage
    var txMessageHash by TxMessageTable.txMessageHash
    var msgIdx by TxMessageTable.msgIdx
    var txTimestamp by TxMessageTable.txTimestamp
}

object TxEventsTable : IntIdTable(name = "tx_msg_event") {
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val txMessageId = integer("tx_message_id")
    val txMsgTypeId = integer("tx_msg_type_id")
    val eventType = varchar("event_type", 256)
}

class TxEventRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxEventRecord>(TxEventsTable) {

        fun buildInsert(
            blockHeight: Int,
            txHash: String,
            type: String,
            msgTypeId: Int
        ) =
            listOf(
                0,
                blockHeight,
                0,
                txHash,
                0,
                type,
                msgTypeId
            ).toProcedureObject()
    }
    var blockHeight by TxEventsTable.blockHeight
    var txHash by TxEventsTable.txHash
    var txHashId by TxEventsTable.txHashId
    var txMessageId by TxEventsTable.txMessageId
    var txMsgTypeId by TxEventsTable.txMsgTypeId
    var eventType by TxEventsTable.eventType
}

object TxEventAttrTable : LongIdTable(name = "tx_msg_event_attr") {
    val txMsgEventId = integer("tx_msg_event_id")
    val attrKey = varchar("attr_key", 256)
    val attrValue = text("attr_value")
    val attrIdx = integer("attr_idx")
    val attrHash = text("attr_hash")
}

class TxEventAttrRecord(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<TxEventAttrRecord>(TxEventAttrTable) {

        fun buildInsert(idx: Int, key: String, value: String) =
            listOf(0, 0, key, value, idx, listOf(idx, key, value).joinToString("").toDbHash()).toProcedureObject()
    }

    var txMsgEventId by TxEventAttrTable.txMsgEventId
    var attrKey by TxEventAttrTable.attrKey
    var attrValue by TxEventAttrTable.attrValue
    var attrIdx by TxEventAttrTable.attrIdx
    var attrHash by TxEventAttrTable.attrHash
}

object TxSingleMessageCacheTable : IntIdTable(name = "tx_single_message_cache") {
    val txTimestamp = datetime("tx_timestamp")
    val txHash = varchar("tx_hash", 64)
    val gasUsed = integer("gas_used")
    val txMessageType = varchar("tx_message_type", 128)
    val processed = bool("processed")
    val height = integer("height")
}

class TxSingleMessageCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxSingleMessageCacheRecord>(TxSingleMessageCacheTable) {

        fun buildInsert(txInfo: TxData, gasUsed: Int, type: String) =
            listOf(0, txInfo.txTimestamp, txInfo.txHash, gasUsed, type, false, txInfo.blockHeight).toProcedureObject()

        fun getGasStats(fromDate: LocalDateTime, toDate: LocalDateTime, granularity: String, msgType: String?) = transaction {
            val tblName = "tx_single_message_gas_stats_${granularity.lowercase()}"
            var query = """
                |SELECT
                |   tx_timestamp,
                |   min_gas_used,
                |   max_gas_used,
                |   avg_gas_used,
                |   stddev_gas_used,
                |   tx_message_type
                |FROM $tblName
                |WHERE tx_timestamp >= ? 
                |  AND tx_timestamp < ? 
            """.trimMargin()

            if (msgType != null) {
                query += " AND tx_message_type = ? "
            }

            query += " ORDER BY tx_timestamp ASC;"

            val dateTimeType: ColumnType = JavaLocalDateTimeColumnType()
            val arguments = mutableListOf<Pair<ColumnType, *>>(
                Pair(dateTimeType, fromDate.startOfDay()),
                Pair(dateTimeType, toDate.startOfDay().plusDays(1))
            )
            if (msgType != null) {
                arguments.add(Pair(VarCharColumnType(), msgType))
            }

            val pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

            query.exec(arguments)
                .map {
                    GasStats(
                        it.getTimestamp("tx_timestamp").toLocalDateTime().format(pattern),
                        it.getInt("min_gas_used"),
                        it.getInt("max_gas_used"),
                        it.getInt("avg_gas_used"),
                        it.getInt("stddev_gas_used"),
                        it.getString("tx_message_type")
                    )
                }.toList()
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
    var height by TxSingleMessageCacheTable.height
}

object TxGasCacheTable : IntIdTable(name = "tx_gas_cache") {
    val hash = varchar("hash", 64)
    val txTimestamp = datetime("tx_timestamp")
    val gasWanted = integer("gas_wanted").nullable()
    val gasUsed = integer("gas_used")
    val feeAmount = double("fee_amount").nullable()
    val processed = bool("processed").default(false)
    val height = integer("height")
    val txHashId = integer("tx_hash_id")
}

class TxGasCacheRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxGasCacheRecord>(TxGasCacheTable) {

        fun buildInsert(
            tx: ServiceOuterClass.GetTxResponse,
            txInfo: TxData,
            totalBaseFees: BigDecimal
        ) =
            listOf(
                0,
                tx.txResponse.txhash,
                txInfo.txTimestamp,
                tx.txResponse.gasWanted.toInt(),
                tx.txResponse.gasUsed.toInt(),
                totalBaseFees,
                false,
                txInfo.blockHeight,
                0
            ).toProcedureObject()

        fun getGasVolume(fromDate: LocalDateTime, toDate: LocalDateTime, granularity: String) = transaction {
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
                |ORDER BY $tblName.tx_timestamp ASC
                |
            """.trimMargin()

            val dateTimeType: ColumnType = JavaLocalDateTimeColumnType()
            val arguments = listOf<Pair<ColumnType, LocalDateTime>>(
                Pair(dateTimeType, fromDate.startOfDay()),
                Pair(dateTimeType, toDate.startOfDay().plusDays(1))
            )

            val pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

            query.exec(arguments)
                .map {
                    TxGasVolume(
                        it.getTimestamp("tx_timestamp").toLocalDateTime().format(pattern),
                        it.getBigDecimal("gas_wanted").toBigInteger(),
                        it.getBigDecimal("gas_used").toBigInteger(),
                        it.getBigDecimal("fee_amount")
                    )
                }.toList()
        }

        fun getTotalGasFees(toDate: LocalDateTime) = transaction {
            val tblName = "tx_gas_fee_volume_${DateTruncGranularity.DAY.name.lowercase()}"
            val query = """
                SELECT sum($tblName.fee_amount) as total_fee
                FROM $tblName
                WHERE $tblName.tx_timestamp <= ?
            """.trimMargin()

            val arguments = listOf<Pair<ColumnType, LocalDateTime>>(
                Pair(JavaLocalDateTimeColumnType(), toDate)
            )
            query.exec(arguments).getBigDecimal("total_fee")
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
    var height by TxGasCacheTable.height
    var txHashId by TxGasCacheTable.txHashId
}

object TxFeepayerTable : IntIdTable(name = "tx_feepayer") {
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val payerType = varchar("payer_type", 128)
    val addressId = integer("address_id")
    val address = varchar("address", 128)
    val txTimestamp = datetime("tx_timestamp")
}

enum class FeePayer { GRANTER, PAYER, FIRST_SIGNER }

fun SizedIterable<TxFeepayerRecord>.getFeepayer() = this.map { TxFeepayer(it.payerType, it.address) }
    .minByOrNull { FeePayer.valueOf(it.type).ordinal }!!

class TxFeepayerRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxFeepayerRecord>(TxFeepayerTable) {

        fun buildInsert(txInfo: TxData, type: String, addrId: Int, address: String) =
            listOf(
                0,
                txInfo.blockHeight,
                -1,
                txInfo.txHash,
                type,
                addrId,
                address,
                txInfo.txTimestamp
            ).toProcedureObject()
    }

    var blockHeight by TxFeepayerTable.blockHeight
    var txHash by TxFeepayerTable.txHash
    var txHashId by TxCacheRecord referencedOn TxFeepayerTable.txHashId
    var payerType by TxFeepayerTable.payerType
    var addressId by TxFeepayerTable.addressId
    var address by TxFeepayerTable.address
    var txTimestamp by TxFeepayerTable.txTimestamp
}

object TxFeeTable : IntIdTable(name = "tx_fee") {
    val blockHeight = integer("block_height")
    val txHash = varchar("tx_hash", 64)
    val txHashId = reference("tx_hash_id", TxCacheTable)
    val feeType = varchar("fee_type", 128)
    val markerId = integer("marker_id")
    val marker = varchar("marker", 256)
    val amount = decimal("amount", 100, 10)
    val msgType = varchar("msg_type", 256).nullable()
    val recipient = varchar("recipient", 128).nullable()
    val origFees = jsonb<TxFeeTable, CustomFeeList>("orig_fees", OBJECT_MAPPER).nullable()
    val txTimestamp = datetime("tx_timestamp")
}

enum class FeeType { BASE_FEE_USED, BASE_FEE_OVERAGE, MSG_BASED_FEE, CUSTOM_FEE }

class TxFeeRecord(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TxFeeRecord>(TxFeeTable) {

        fun updateTxFees(updateFromHeight: Int) = transaction {
            val query = "CALL update_tx_fees($updateFromHeight)"
            this.exec(query)
        }

        fun calcMarketRate(tx: ServiceOuterClass.GetTxResponse, totalBaseFees: BigDecimal) =
            totalBaseFees / tx.txResponse.gasWanted.toBigDecimal()

        fun buildInserts(
            txInfo: TxData,
            tx: ServiceOuterClass.GetTxResponse,
            assetService: AssetService,
            msgBasedFeeList: MutableList<TxFeeData>,
            totalBaseFees: BigDecimal
        ) =
            transaction {
                val feeList = mutableListOf<String>()
                // calc baseFeeUsed, baseFeeOverage in nhash
                totalBaseFees.let { totalBaseFeeAmount ->
                    val marketRate = calcMarketRate(tx, totalBaseFeeAmount)
                    var baseFeeUsed = tx.txResponse.gasUsed.toBigDecimal() * marketRate
                    var overage = totalBaseFeeAmount - baseFeeUsed
                    // If failed, set to totalBaseFeeAmount as identified
                    if (!tx.success()) {
                        baseFeeUsed = totalBaseFeeAmount
                        overage = BigDecimal.ZERO
                    }
                    Pair(overage, baseFeeUsed)
                }.let { (baseFeeOverage, baseFeeUsed) ->
                    val nhash = assetService.getAssetRaw(ExplorerProperties.UTILITY_TOKEN).second
                    // insert used fee
                    feeList.add(buildInsert(txInfo, FeeType.BASE_FEE_USED.name, nhash.id.value, nhash.denom, baseFeeUsed))
                    // insert paid too much fee if > 0
                    if (baseFeeOverage > BigDecimal.ZERO) {
                        feeList.add(
                            buildInsert(
                                txInfo,
                                FeeType.BASE_FEE_OVERAGE.name,
                                nhash.id.value,
                                nhash.denom,
                                baseFeeOverage
                            )
                        )
                    }
                    // insert additional fees grouped by msg type
                    if (tx.success()) {
                        msgBasedFeeList.forEach { fee ->
                            val feeType =
                                if (fee.msgType == CUSTOM_FEE_MSG_TYPE) FeeType.CUSTOM_FEE.name else FeeType.MSG_BASED_FEE.name
                            feeList.add(
                                buildInsert(
                                    txInfo,
                                    feeType,
                                    nhash.id.value,
                                    nhash.denom,
                                    fee.amount,
                                    fee.recipient?.ifEmpty { null },
                                    fee.msgType,
                                    fee.origFees
                                )
                            )
                        }
                    }
                }
                feeList
            }

        fun identifyMsgBasedFees(
            tx: ServiceOuterClass.GetTxResponse,
            msgFeeClient: MsgFeeGrpcClient,
            height: Int
        ): MutableList<TxFeeData> {
            val msgToFee = mutableListOf<TxFeeData>()
            // find tx level msg event
            val definedMsgFeeList = tx.txResponse.eventsList
                .firstOrNull { it.type == getEventMsgFeesType().removeFirstSlash() }
                ?.attributesList?.first { it.key == "msg_fees" }
                ?.value?.let { VANILLA_MAPPER.readValue<List<EventFee>>(it) }

            // Adds any defined fees to fee map
            definedMsgFeeList?.forEach { fee ->
                // gets the total value and denom from the defined fees
                val (amount, denom) = fee.total.denomAmountToPair()
                val customFeeMap =
                    if (fee.msg_type == getCustomFeeProtoType()) {
                        tx.findAllMatchingEvents(listOf("assess_custom_msg_fee"))
                            .flatMap { event ->
                                val nameList = event.attributesList.filter { it.key == "name" }
                                val amountList = event.attributesList.filter { it.key == "amount" }
                                val recipList = event.attributesList.filter { it.key == "recipient" }
                                nameList.mapIndexed { idx, name ->
                                    val (custAmount, custDenom) = amountList[idx].value.denomAmountToPair()
                                    CustomFee(name.value, custAmount.toBigDecimal(), custDenom, recipList[idx].value)
                                }
                            }.groupBy { it.recipient }
                    } else {
                        emptyMap()
                    }

                val data = TxFeeData(
                    TxMessageTypeRecord.findByProtoType(fee.msg_type)?.type ?: CUSTOM_FEE_MSG_TYPE,
                    amount.toBigDecimal(),
                    denom,
                    fee.recipient,
                    customFeeMap[fee.recipient]?.let { CustomFeeList(it) }
                )
                msgToFee.add(data)
            } ?: msgToFee.addAll(tx.identifyMsgBasedFeesOld(msgFeeClient, height))
            return msgToFee
        }

        private fun buildInsert(
            txInfo: TxData,
            type: String,
            markerId: Int,
            marker: String,
            amount: BigDecimal,
            recipient: String? = null,
            msgType: String? = null,
            origFees: CustomFeeList? = null
        ) = listOf(
            0,
            txInfo.blockHeight,
            0,
            txInfo.txHash,
            type,
            markerId,
            marker,
            amount,
            msgType,
            recipient,
            origFees.stringify(),
            txInfo.txTimestamp
        ).toProcedureObject()
    }

    fun toFeeCoinStr() =
        FeeCoinStr(
            this.amount.stringfy(),
            this.marker,
            this.msgType,
            this.recipient,
            this.origFees?.list
        )

    var blockHeight by TxFeeTable.blockHeight
    var txHash by TxFeeTable.txHash
    var txHashId by TxCacheRecord referencedOn TxFeeTable.txHashId
    var feeType by TxFeeTable.feeType
    var markerId by TxFeeTable.markerId
    var marker by TxFeeTable.marker
    var amount by TxFeeTable.amount
    var msgType by TxFeeTable.msgType
    var recipient by TxFeeTable.recipient
    var origFees by TxFeeTable.origFees
    var txTimestamp by TxFeeTable.txTimestamp
}
