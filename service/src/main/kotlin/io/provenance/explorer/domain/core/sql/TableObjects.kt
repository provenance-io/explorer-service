package io.provenance.explorer.domain.core.sql

import com.google.protobuf.GeneratedMessageV3
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.extensions.execAndMap
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.math.BigDecimal
import java.util.Locale

val DEFAULT_DATE_TIME_STRING_FORMATTER = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss.SSS").withLocale(Locale.ROOT)

fun DateTime.toProcedureObject() = java.sql.Timestamp(this.millis).toString()

fun GeneratedMessageV3.toProcedureObject() = OBJECT_MAPPER.writeValueAsString(this)

// Creates String from list = value,value
fun List<Any?>.toProcedureObject() =
    this.joinToString(",", "(", ")") { value ->
        if (value == null) "null"
        else
            when (value) {
                is DateTime -> "'${value.toProcedureObject()}'"
                is GeneratedMessageV3 -> "'${value.toProcedureObject()}'"
                is Int, is Double, is BigDecimal -> value.toString()
                is String -> "'$value'"
                is Boolean -> value.toString()
                else -> "not a thing"
            }
    }

fun List<String>.toObject() = this.joinToString(",", "(", ")")

// Creates string with array braces = ARRAY [ value, value ]
fun List<String>.toArray(tableName: String) =
    this.joinToString(",", " (ARRAY [", "])::$tableName[] ")

fun toDbText(value: String) = transaction {
    val query = "SELECT ($value)::TEXT AS text".trimIndent()
    query.execAndMap { it.getString("text") }.first()
}
