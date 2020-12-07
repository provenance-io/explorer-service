package io.provenance.explorer.config

import com.fasterxml.jackson.databind.JsonNode
import feign.Feign
import feign.Request
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import io.provenance.core.extensions.logger
import io.provenance.core.extensions.toJsonString
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.domain.TendermintApiException
import io.provenance.pbc.clients.CosmosRemoteInvocationException
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@EnableConfigurationProperties(
        value = [ExplorerProperties::class]
)
@Configuration
class RestClientConfig(val explorerProperties: ExplorerProperties) {


    @Bean
    fun tendermintClient() = Feign.Builder()
            .options(Request.Options(5000, 5000, false))
            .encoder(JacksonEncoder(OBJECT_MAPPER))
            .decoder { r, x ->
                val log = LoggerFactory.getLogger(this::class.java)
                if (r.status() != 200) {
                    log.error("Response code of: ${r.status()} calling url: ${r.request().url()} reason: ${r.reason()}")
                    throw TendermintApiException(r.toString())
                }
                val response = OBJECT_MAPPER.readValue(r.body().asReader(), JsonNode::class.java)
                if (response.has("error") || !response.has("result")) {
                    log.error("Error response from tender mint calling url: ${r.request().url()} response: ${response.toString()}")
                    throw TendermintApiException(response.toString())
                }
                response.get("result")
            }.errorDecoder { method, r ->
                val log = LoggerFactory.getLogger(this::class.java)
                val status = r.status()
                val url = r.request().url()
                val httpMethod = r.request().httpMethod().name
                log.warn("$method: $status $httpMethod $url")
                val body = r.body()?.asReader()?.readLines()?.joinToString("\n")
                throw TendermintApiException(body.toString())
            }.target(TendermintClient::class.java, explorerProperties.tendermintUrl)


    @Bean
    fun bpClient() = Feign.Builder()
            .options(Request.Options(5000, 5000, false))
            .encoder(JacksonEncoder(OBJECT_MAPPER))
            .decoder(JacksonDecoder(OBJECT_MAPPER))
            .errorDecoder { method, r ->
                val log = LoggerFactory.getLogger(this::class.java)
                val status = r.status()
                val url = r.request().url()
                val httpMethod = r.request().httpMethod().name
                log.warn("$method: $status $httpMethod $url")

                val body = r.body()?.asReader()?.readLines()?.joinToString("\n")
                throw CosmosRemoteInvocationException(method, httpMethod, url, status, body)
            }.target(PbClient::class.java, explorerProperties.pbUrl)

}