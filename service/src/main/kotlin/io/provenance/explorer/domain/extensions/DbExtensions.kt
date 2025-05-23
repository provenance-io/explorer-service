package io.provenance.explorer.domain.extensions

import io.provenance.explorer.OBJECT_MAPPER
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Timestamp.toDateTime(newZone: ZoneId, pattern: String) =
    DateTimeFormatter.ofPattern(pattern).format(this.toLocalDateTime())

fun String.exec(args: Iterable<Pair<IColumnType, Any?>>): ResultSet =
    with(TransactionManager.current().connection.prepareStatement(this, false)) {
        this.fillParameters(args)
        this.executeQuery()
    }

fun <T : Any> String.execAndMap(args: Iterable<Pair<IColumnType, Any?>> = emptyList(), transform: (ResultSet) -> T): List<T> {
    val result = arrayListOf<T>()
    TransactionManager.current().exec(this, args, StatementType.SELECT) { rs ->
        while (rs.next()) {
            result += transform(rs)
        }
    }
    return result
}

fun <R> ResultSet?.map(transform: (ResultSet) -> R): ArrayList<R> {
    val result = arrayListOf<R>()
    this?.use {
        while (it.next()) {
            result += transform(this)
        }
    }
    return result
}

inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R {
    var closed = false
    try {
        return block(this)
    } catch (e: Throwable) {
        closed = true
        try {
            this?.close()
        } catch (closeException: Throwable) {
            e.addSuppressed(closeException)
        }
        throw e
    } finally {
        if (!closed) {
            this?.close()
        }
    }
}

fun <T> String.mapper(clazz: Class<T>) = OBJECT_MAPPER.readValue(this, clazz)
