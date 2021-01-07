package io.provenance.explorer.domain.sql

import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.sql.*
import org.postgresql.util.PGobject
import java.lang.reflect.ParameterizedType
import java.sql.PreparedStatement

inline fun <T : Table, reified R : Any> T.jsonb(name: String, objectMapper: ObjectMapper) =
    registerColumn<R>(name, object : JsonBColumnType<R>(objectMapper) {})

abstract class JsonBColumnType<T : Any>(private val objectMapper: ObjectMapper) : ColumnType() {
    @Suppress("UNCHECKED_CAST")
    var clazz: Class<T> = (this::class.java.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<T>

    override fun sqlType() = "JSONB"

    override fun setParameter(stmt: PreparedStatement, index: Int, value: Any?) {
        value?.let { objectMapper.writeValueAsString(it) }
            .let {
                PGobject().apply {
                    type = "jsonb"
                    this.value = it
                }
            }.run {
                stmt.setObject(index, this)
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
