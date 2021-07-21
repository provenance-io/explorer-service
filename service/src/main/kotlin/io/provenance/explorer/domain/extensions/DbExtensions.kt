package io.provenance.explorer.domain.extensions

import com.google.api.CustomHttpPattern
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.sql.ResultSet
import java.sql.Timestamp

fun Timestamp.toDateTime(newZone: DateTimeZone, pattern: String): String =
    DateTime(this.time).withZone(newZone).toString(pattern)

fun String.exec(args: Iterable<Pair<IColumnType, Any?>>): ResultSet =
    with(TransactionManager.current().connection.prepareStatement(this, false)) {
        this.fillParameters(args)
        this.executeQuery()
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