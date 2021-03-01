package io.provenance.explorer.config

import com.google.protobuf.util.JsonFormat
import cosmos.auth.v1beta1.Auth
import cosmos.bank.v1beta1.Tx
import cosmos.crypto.ed25519.Keys
import cosmos.gov.v1beta1.Gov
import cosmos.params.v1beta1.Params
import io.provenance.marker.v1.MarkerAccount
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter
import org.springframework.http.converter.protobuf.ProtobufJsonFormatHttpMessageConverter
import org.springframework.web.client.RestTemplate
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class RestConfig {

    @Bean
    fun protoPrinter(): JsonFormat.Printer? {
        val typeRegistry = JsonFormat.TypeRegistry.newBuilder()
            .add(accountDescriptors())
            .add(pubKeyDescriptors())
            .add(msgDescriptors())
            .add(contentDescriptors())
            .build()
        return JsonFormat.printer().usingTypeRegistry(typeRegistry)
    }

    @Bean
    fun protoParser(): JsonFormat.Parser? {
        val typeRegistry = JsonFormat.TypeRegistry.newBuilder()
            .add(accountDescriptors())
            .add(pubKeyDescriptors())
            .add(msgDescriptors())
            .add(contentDescriptors())
            .build()
        return JsonFormat.parser().usingTypeRegistry(typeRegistry)
    }

    @Bean
    @Primary
    fun protobufJsonFormatHttpMessageConverter(): ProtobufHttpMessageConverter? {
        return ProtobufJsonFormatHttpMessageConverter(protoParser(), protoPrinter())
    }

    @Bean
    fun restTemplate(hmc: ProtobufHttpMessageConverter?): RestTemplate? {
        return RestTemplate(listOf(hmc))
    }

    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            @Override
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/api/**")
                    .allowedMethods("*")
                    .allowedOriginPatterns("*")
                    .allowCredentials(true)
                    .maxAge(3600)
            }
        }
    }
}

fun accountDescriptors() =
    listOf(MarkerAccount.getDescriptor(), Auth.BaseAccount.getDescriptor(), Auth.ModuleAccount.getDescriptor())

fun pubKeyDescriptors() =
    listOf(Keys.PubKey.getDescriptor(), cosmos.crypto.secp256k1.Keys.PubKey.getDescriptor())

fun msgDescriptors() =
    listOf(
        Tx.MsgSend.getDescriptor(),
        Tx.MsgMultiSend.getDescriptor(),
        cosmos.gov.v1beta1.Tx.MsgSubmitProposal.getDescriptor(),
        cosmos.gov.v1beta1.Tx.MsgVote.getDescriptor(),
        cosmos.gov.v1beta1.Tx.MsgVoteWeighted.getDescriptor(),
        cosmos.gov.v1beta1.Tx.MsgDeposit.getDescriptor()
    )

fun contentDescriptors() =
    listOf(Gov.TextProposal.getDescriptor(), Params.ParameterChangeProposal.getDescriptor())
