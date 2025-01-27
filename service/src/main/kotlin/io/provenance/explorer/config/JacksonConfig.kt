package io.provenance.explorer.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import io.provenance.explorer.configureProvenance
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper {
        println("Getting ObjectMapper()")
        val mapper = ObjectMapper().configureProvenance().apply {
            JavaTimeModule().apply {
                addSerializer(LocalDateTimeSerializer.INSTANCE)
            }.also { javaTime ->
                registerModule(javaTime)
            }

            registerModule(ProtobufModule())
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

        return mapper
    }
}
