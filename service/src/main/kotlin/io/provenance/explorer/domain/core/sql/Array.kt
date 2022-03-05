package io.provenance.explorer.domain.core.sql

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.ComparisonOp
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.IsNullOp
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.OrOp
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.QueryParameter
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.transactions.TransactionManager

fun <T> Table.array(name: String, columnType: ColumnType): Column<Array<T>> = registerColumn(name, ArrayColumnType(columnType))

class ArrayColumnType(private val type: ColumnType) : ColumnType() {
    override fun sqlType(): String = buildString {
        append(type.sqlType())
        append(" ARRAY")
    }
    override fun valueToDB(value: Any?): Any? {
        if (value is ArrayList<*>) {
            val columnType = type.sqlType().split("(")[0]
            val jdbcConnection = (TransactionManager.current().connection as JdbcConnectionImpl).connection
            return jdbcConnection.createArrayOf(columnType, value.toArray())
        } else {
            return super.valueToDB(value)
        }
    }
    override fun valueFromDB(value: Any): Any {
        if (value is java.sql.Array) {
            return value.array
        }
        if (value is Array<*>) {
            return value
        }
        error("Array does not support for this database")
    }

    override fun notNullValueToDB(value: Any): Any {
        if (value is ArrayList<*>) {
            if (value.isEmpty())
                return "'{}'"

            val columnType = type.sqlType().split("(")[0]
            val jdbcConnection = (TransactionManager.current().connection as JdbcConnectionImpl).connection
            return jdbcConnection.createArrayOf(columnType, value.toArray())
                ?: error("Can't create non null array for $value")
        } else {
            return super.notNullValueToDB(value)
        }
    }
}

class AnyOp(val expr1: Expression<*>, val expr2: Expression<*>) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        if (expr2 is OrOp) {
            queryBuilder.append("(").append(expr2).append(")")
        } else {
            queryBuilder.append(expr2)
        }
        queryBuilder.append(" = ANY (")
        if (expr1 is OrOp) {
            queryBuilder.append("(").append(expr1).append(")")
        } else {
            queryBuilder.append(expr1)
        }
        queryBuilder.append(")")
    }
}

class ContainsOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "@>")

infix fun <T, S> ExpressionWithColumnType<T>.any(t: S): Op<Boolean> {
    if (t == null) {
        return IsNullOp(this)
    }
    return AnyOp(this, QueryParameter(t, columnType))
}

infix fun <T, S> ExpressionWithColumnType<T>.contains(arry: Array<in S>): Op<Boolean> = ContainsOp(this, QueryParameter(arry, columnType))
