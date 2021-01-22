package io.provenance.explorer.config

import feign.Feign
import feign.Logger
import feign.Request
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.domain.TendermintApiCustomException
import io.provenance.explorer.domain.TendermintApiException
import io.provenance.explorer.domain.core.logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@EnableConfigurationProperties(
    value = [ExplorerProperties::class]
)
@Configuration
class RestClientConfig(val explorerProperties: ExplorerProperties) {


    @Bean
    fun tendermintClient() = Feign.Builder()
        .options(
            Request.Options(
                explorerProperties.tendermintClientTimeoutMs(),
                explorerProperties.tendermintClientTimeoutMs(),
                false))
        .logger(ExplorerFeignLogger(TendermintClient::class.java.name))
        .logLevel(Logger.Level.BASIC)
        .encoder(JacksonEncoder(OBJECT_MAPPER))
        .decoder(JacksonDecoder(OBJECT_MAPPER)) //TODO figure out why this hates me
        .errorDecoder { method, r ->
            val log = LoggerFactory.getLogger(this::class.java)
            val status = r.status()
            val url = r.request().url()
            val httpMethod = r.request().httpMethod().name
            log.warn("$method: $status $httpMethod $url")
            val body = r.body()?.asReader()?.readLines()?.joinToString("\n")
            throw TendermintApiException(body.toString())
        }.target(TendermintClient::class.java, explorerProperties.tendermintUrl)


    @Bean
    fun pbClient() = Feign.Builder()
        .options(Request.Options(explorerProperties.pbClientTimeoutMs(), explorerProperties.pbClientTimeoutMs(), false))
        .logger(ExplorerFeignLogger(PbClient::class.java.name))
        .logLevel(Logger.Level.BASIC)
        .encoder(JacksonEncoder(OBJECT_MAPPER))
        .decoder(JacksonDecoder(OBJECT_MAPPER))
        .errorDecoder { method, r ->
            val log = LoggerFactory.getLogger(this::class.java)
            val status = r.status()
            val url = r.request().url()
            val httpMethod = r.request().httpMethod().name
            log.warn("$method: $status $httpMethod $url")

            val body = r.body()?.asReader()?.readLines()?.joinToString("\n")
            throw TendermintApiCustomException(method, httpMethod, url, status, body)
        }.target(PbClient::class.java, explorerProperties.pbUrl)


    class ExplorerFeignLogger constructor(val clazz: String) : Logger() {

        val log = logger(clazz)

        override fun log(configKey: String?, format: String?, vararg args: Any?) {
            //no op
        }

        override fun logRequest(configKey: String?, logLevel: Level, request: Request) {
            if (!request.url().endsWith("/status"))
                log.info("Requesting external api method: ${request.httpMethod()} url: ${request.url()}")
        }

    }
}
