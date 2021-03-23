package io.provenance.explorer.domain.core.sql

import org.jetbrains.exposed.sql.DecimalColumnType
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.append
import org.jetbrains.exposed.sql.jodatime.DateColumnType
import org.joda.time.DateTime
import java.math.BigDecimal

// Generic Distinct function
class Distinct<T>(val expr: Expression<T>, _columnType: IColumnType) : Function<T>(_columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("distinct(", expr, ")") }
}

// Custom expressions for complex query types
class Lag(val lag: Expression<DateTime>, val orderBy: Expression<Int>): Function<DateTime>(DateColumnType(true)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        queryBuilder { append("LAG(", lag,") OVER (order by ", orderBy, " desc)") }
}

class ExtractEpoch(val expr: Expression<DateTime>): Function<BigDecimal>(DecimalColumnType(10, 10)) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        queryBuilder { append("extract(epoch from ", expr," )") }
}
