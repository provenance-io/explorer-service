package io.provenance.explorer.config

import com.google.protobuf.Descriptors
import com.google.protobuf.util.JsonFormat
import org.reflections.Reflections
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
            .add(packageDescriptors())
            .build()
        return JsonFormat.printer().usingTypeRegistry(typeRegistry)
    }

    @Bean
    fun protoParser(): JsonFormat.Parser? {
        val typeRegistry = JsonFormat.TypeRegistry.newBuilder()
            .add(packageDescriptors())
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


private fun packageDescriptors(): List<Descriptors.Descriptor> {
    val descriptors = mutableListOf<Descriptors.Descriptor>()
    descriptors.addAll(findDescriptorsInPackage("cosmos"))
    descriptors.addAll(findDescriptorsInPackage("cosmwasm"))
    descriptors.addAll(findDescriptorsInPackage("ibc"))
    descriptors.addAll(findDescriptorsInPackage("io.provenance"))
    return descriptors
}


private fun findDescriptorsInPackage(basePackageName: String): List<Descriptors.Descriptor> {
    val reflections = Reflections(basePackageName)
    val messageClasses = reflections.getSubTypesOf(com.google.protobuf.Message::class.java)

    return messageClasses.mapNotNull {
        try {
            it.getMethod("getDescriptor").invoke(null) as Descriptors.Descriptor
        } catch (e: Exception) {
            null
        }
    }
}
