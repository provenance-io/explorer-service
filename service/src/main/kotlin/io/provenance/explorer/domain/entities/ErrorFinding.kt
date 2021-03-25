package io.provenance.explorer.domain.entities

import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

data class TxError(
    val blockHeight: Int,
    val blockTxCount: Int,
    val realTxCount: Int
)

data class UnknownTxType(
    val type: String,
    val module: String,
    val protoType: String,
)

class ErrorFinding {
    companion object {

        fun getTxErrors() = transaction {
            val count = TxCacheTable.hash.count()

            BlockCacheTable
                .leftJoin(TxCacheTable, { BlockCacheTable.height }, { TxCacheTable.height })
                .slice(BlockCacheTable.height, BlockCacheTable.txCount, count)
                .select { BlockCacheTable.txCount greater 0 }
                .andWhere { TxCacheTable.hash.isNull()  }
                .groupBy(BlockCacheTable.height, BlockCacheTable.txCount)
                .having { BlockCacheTable.txCount neq count }
                .map { TxError(
                    it[BlockCacheTable.height],
                    it[BlockCacheTable.txCount],
                    it[count].toInt()
                ) }
        }

        fun getUnknownTxTypes() = transaction {
            TxMessageTypeRecord
                .find { (TxMessageTypeTable.type eq "unknown") or (TxMessageTypeTable.module eq "unknown") }
                .map { UnknownTxType(it.type, it.module, it.protoType) }
        }
    }
}
