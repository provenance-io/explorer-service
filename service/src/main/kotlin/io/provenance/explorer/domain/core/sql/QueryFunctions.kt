package io.provenance.explorer.domain.core.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.DecimalColumnType
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.append
import org.jetbrains.exposed.sql.jodatime.CustomDateTimeFunction
import org.jetbrains.exposed.sql.jodatime.DateColumnType
import org.jetbrains.exposed.sql.stringLiteral
import org.joda.time.DateTime
import java.math.BigDecimal
import kotlin.Array

// Generic Distinct function
class Distinct<T>(val expr: Expression<T>, _columnType: IColumnType) : Function<T>(_columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("distinct(", expr, ")") }
}

// Custom expressions for complex query types
class LagDesc(val lag: Expression<DateTime>, val orderBy: Expression<Int>) : Function<DateTime>(DateColumnType(true)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        queryBuilder { append("LAG(", lag, ") OVER (order by ", orderBy, " desc)") }
}

class Lag<T>(val lag: Expression<T>, val orderBy: Expression<T>, _columnType: IColumnType) : Function<T>(_columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        queryBuilder { append("LAG(", lag, ") OVER (order by ", orderBy, ")") }
}

class Lead<T>(val lead: Expression<T>, val orderBy: Expression<T>, _columnType: IColumnType) : Function<T>(_columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        queryBuilder { append("LEAD(", lead, ") OVER (order by ", orderBy, ")") }
}

class ExtractDay(val expr: Expression<DateTime>) : Function<String>(VarCharColumnType(9)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        queryBuilder { append("to_char(", expr, ", 'DAY')") }
}

class ExtractDOW(val expr: Expression<DateTime>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        queryBuilder { append("extract(dow from ", expr, " )") }
}

class ExtractEpoch(val expr: Expression<DateTime>) : Function<BigDecimal>(DecimalColumnType(10, 10)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        queryBuilder { append("extract(epoch from ", expr, " )") }
}

class ExtractHour(val expr: Expression<DateTime>) : Function<Int>(IntegerColumnType()) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        queryBuilder { append("extract(hour from ", expr, " )") }
}

fun DateTrunc(granularity: String, column: Expression<*>) =
    CustomDateTimeFunction("DATE_TRUNC", stringLiteral(granularity), column)

class ColumnNullsLast(private val col: Expression<*>) : Expression<String>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        append(col, " IS NOT NULL ")
    }
}

fun Expression<*>.nullsLast() = ColumnNullsLast(this)

fun EntityID<Int>?.getOrNull() = try { this?.value } catch (e: Exception) { null }

class Array<T>(colType: ColumnType, vararg val exprs: Expression<T>) : Function<Array<T>>(ArrayColumnType(colType)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("ARRAY [", exprs.joinToString(","), "]") }
}

class ArrayRaw<T>(colType: ColumnType, val exprs: List<T>) : Function<Array<T>>(ArrayColumnType(colType)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("ARRAY [", exprs.joinToString(","), "]") }
}

fun List<Expression<*>>.joinToList() = this.joinToString(",") { "$it::text" }

class ArrayAgg(val expr: Expression<Array<String>>) : Function<Array<Array<String>>>(ArrayColumnType(ArrayColumnType(TextColumnType()))) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("array_agg(", expr, ")") }
}

fun <C1 : IdTable<Int>> List<Int>.toEntities(table: C1) = this.map { EntityID(it, table) }
