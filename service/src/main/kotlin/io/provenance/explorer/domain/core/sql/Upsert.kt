package io.provenance.explorer.domain.core.sql

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager

// With some tinkering from https://github.com/JetBrains/Exposed/issues/167
// and, https://ohadshai.medium.com/first-steps-with-kotlin-exposed-cb361a9bf5ac
class BatchUpsert(
    table: Table,
    private val conflictKeys: List<Column<*>>,
    private val updateKeys: List<Column<*>>
) : BatchInsertStatement(table, false) {
    override fun prepareSQL(transaction: Transaction): String {
        val tm = TransactionManager.current()
        val updateSetter = updateKeys.joinToString { "${tm.identity(it)} = EXCLUDED.${tm.identity(it)}" }
        val onConflict = "ON CONFLICT (${conflictKeys.joinToString { tm.identity(it) }}) DO UPDATE SET $updateSetter"
        return "${super.prepareSQL(transaction)} $onConflict"
    }
}

fun <T : Table, E> T.batchUpsert(
    data: List<E>,
    conflictKeys: List<Column<*>>,
    updateKeys: List<Column<*>>,
    body: T.(BatchUpsert, E) -> Unit
) {
    BatchUpsert(this, conflictKeys, updateKeys).apply {
        data.forEach {
            addBatch()
            body(this, it)
        }
        execute(TransactionManager.current())
    }
}

class BatchInsert(
    table: Table,
    private val conflictKeys: List<Column<*>>,
) : BatchInsertStatement(table, false) {
    override fun prepareSQL(transaction: Transaction): String {
        val tm = TransactionManager.current()
        val onConflict = "ON CONFLICT (${conflictKeys.joinToString { tm.identity(it) }}) DO NOTHING"
        return "${super.prepareSQL(transaction)} $onConflict"
    }
}

fun <T : Table, E> T.batchInsert(
    data: List<E>,
    conflictKeys: List<Column<*>>,
    body: T.(BatchInsert, E) -> Unit
) {
    BatchInsert(this, conflictKeys).apply {
        data.forEach {
            addBatch()
            body(this, it)
        }
        execute(TransactionManager.current())
    }
}
