package io.provenance.explorer.domain.core.sql

import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject
import java.lang.reflect.ParameterizedType

inline fun <T : Table, reified R : Any> T.jsonb(name: String, objectMapper: ObjectMapper) =
    registerColumn<R>(name, object : JsonBColumnType<R>(objectMapper) {})

abstract class JsonBColumnType<T : Any>(private val objectMapper: ObjectMapper) : ColumnType() {
    @Suppress("UNCHECKED_CAST")
    var clazz: Class<T> = (this::class.java.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<T>

    override fun sqlType() = "JSONB"

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        value?.let { objectMapper.writeValueAsString(it) }
            .let {
                PGobject().apply {
                    type = "jsonb"
                    this.value = it
                }
            }.run {
                stmt[index] = this
            }
    }

    override fun valueFromDB(value: Any): T {
        if (value is PGobject) {
            val json = value.value
            return objectMapper.readValue(json, clazz)
        }
        return value as T
    }
}
