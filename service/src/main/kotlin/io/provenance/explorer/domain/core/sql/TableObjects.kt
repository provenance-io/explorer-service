package io.provenance.explorer.domain.core.sql

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.GeneratedMessageV3
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.extensions.execAndMap
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val DEFAULT_DATE_TIME_STRING_FORMATTER = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss.SSS")
    .withLocale(Locale.ROOT)

// TODO - the timezone of the application should be global.
fun LocalDateTime.toProcedureObject() = java.sql.Timestamp(this.toInstant(ZoneOffset.UTC).toEpochMilli()).toString()

fun GeneratedMessageV3.toProcedureObject() = OBJECT_MAPPER.writeValueAsString(this).replaceSingleQuotes()

// Creates String from list = value,value
fun List<Any?>.toProcedureObject() =
    this.joinToString(",", "(", ")") { value ->
        if (value == null) {
            "null"
        } else {
            when (value) {
                is LocalDateTime -> "'${value.toProcedureObject()}'"
                is GeneratedMessageV3 -> "'${value.toProcedureObject()}'"
                is Int, is Double, is BigDecimal, is Long -> value.toString()
                is String -> "'${value.replaceSingleQuotes()}'"
                is Boolean -> value.toString()
                is ObjectNode -> "'$value'"
                else -> "not a thing"
            }
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

fun String.replaceSingleQuotes() = this.replace("'", "''")

fun Set<String>.toDbQueryList() = this.joinToString(",") { "'$it'" }
