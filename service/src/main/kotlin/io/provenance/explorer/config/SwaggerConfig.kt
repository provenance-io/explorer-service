package io.provenance.explorer.config

import io.provenance.explorer.domain.annotation.HiddenApi
import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.AUTHORIZATION
import org.springframework.http.MediaType
import org.springframework.util.ReflectionUtils
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiInfo
import springfox.documentation.service.ApiKey
import springfox.documentation.service.Contact
import springfox.documentation.service.SecurityReference
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spi.service.contexts.SecurityContext
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.spring.web.plugins.WebFluxRequestHandlerProvider
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider
import java.util.function.Predicate

@EnableConfigurationProperties(
    value = [ExplorerProperties::class]
)
@Configuration
class SwaggerConfig(val props: ExplorerProperties) {

    @Bean
    fun api(): Docket {
        val contact = Contact("Provenance Blockchain Foundation", "provenance.io", "info@provenance.io")

        val apiInfo = ApiInfo(
            "Provenance Explorer",
            "Provenance Explorer",
            "3",
            "",
            contact,
            "",
            "",
            listOf()
        )

        val docket = Docket(DocumentationType.OAS_30)
            .apiInfo(apiInfo)
            .host(props.swaggerUrl)
            .protocols(setOf(props.swaggerProtocol))
            .consumes(setOf(MediaType.APPLICATION_JSON_VALUE))
            .produces(setOf(MediaType.APPLICATION_JSON_VALUE))
            .forCodeGeneration(true)
            .securityContexts(
                listOf(
                    SecurityContext.builder()
                        .securityReferences(listOf(SecurityReference(AUTHORIZATION, emptyArray())))
                        .build()
                )
            )
            .securitySchemes(listOf(ApiKey(AUTHORIZATION, AUTHORIZATION, "header")))
            .select()
            .apis(RequestHandlerSelectors.basePackage("io.provenance.explorer.web"))

        if (props.hiddenApis()) {
            docket.apis(Predicate.not(RequestHandlerSelectors.withClassAnnotation(HiddenApi::class.java)))
                .apis(Predicate.not(RequestHandlerSelectors.withMethodAnnotation(HiddenApi::class.java)))
        }

        return docket.build()
    }

    @Suppress("UNCHECKED_CAST")
    @Bean
    fun springfoxHandlerProviderBeanPostProcessor(): BeanPostProcessor? {
        return object : BeanPostProcessor {
            @Throws(BeansException::class)
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                if (bean is WebMvcRequestHandlerProvider || bean is WebFluxRequestHandlerProvider) {
                    customizeSpringfoxHandlerMappings(getHandlerMappings(bean))
                }
                return bean
            }

            private fun <T : RequestMappingInfoHandlerMapping?> customizeSpringfoxHandlerMappings(mappings: MutableList<T>) {
                mappings.removeIf { mapping -> mapping?.patternParser != null }
            }

            private fun getHandlerMappings(bean: Any): MutableList<RequestMappingInfoHandlerMapping> {
                return try {
                    val field = ReflectionUtils.findField(bean.javaClass, "handlerMappings")
                    field?.isAccessible = true
                    field?.get(bean) as MutableList<RequestMappingInfoHandlerMapping>
                } catch (e: Exception) {
                    throw IllegalStateException(e)
                }
            }
        }
    }
}
