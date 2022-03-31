package io.provenance.explorer.config

import io.provenance.explorer.service.async.EventStream
import kotlinx.coroutines.runBlocking
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@EnableConfigurationProperties(value = [EventStreamProperties::class])
@Configuration
class EventStreamConfig(val esProps: EventStreamProperties) {

//    @Bean
//    fun eventStream() = runBlocking {
//        EventStream(esProps).beginStream()
//    }

}
