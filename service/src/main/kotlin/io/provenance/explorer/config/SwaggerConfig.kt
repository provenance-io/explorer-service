 package io.provenance.explorer.config

 import com.fasterxml.jackson.databind.BeanDescription
 import io.swagger.v3.oas.models.OpenAPI
 import io.swagger.v3.oas.models.info.Contact
 import io.swagger.v3.oas.models.info.Info
 import org.springdoc.core.models.GroupedOpenApi
 import org.springframework.context.annotation.Bean
 import org.springframework.context.annotation.Configuration

 // @EnableConfigurationProperties(
//    value = [ExplorerProperties::class]
// )
 @Configuration
 class SwaggerConfig(val props: ExplorerProperties) {

     @Bean
     fun v2Api() = GroupedOpenApi.builder()
         .group("public")
         .displayName("Public")
         .packagesToScan("io.provenance.explorer.web")
         .build()

    @Bean
    fun api(): OpenAPI {
        val contact = Contact()
            .name("Provenance Blockchain Foundation")
            .url("https://www.provenance.io")
            .email("info@provenance.io")

        val apiInfo = Info().title("Provenance Explorer")
//            .description("Provenance Explorer")
            .version("v3")
//            .contact(contact)

//        val securitySchemeName = "Authorization"

        return OpenAPI()
            .info(apiInfo)
//            .addSecurityItem(SecurityRequirement().addList(securitySchemeName))
//            .components(
//                Components().addSecuritySchemes(
//                    securitySchemeName,
//                    SecurityScheme()
//                        .type(SecurityScheme.Type.APIKEY)
//                        .`in`(SecurityScheme.In.HEADER))
//                )
    }

//     @Bean
//     fun hiddenApiCustomizer(): OpenApiCustomiser {
//         return OpenApiCustomiser { openApi ->
//             val hiddenAnnotations = listOf(
//                 HiddenApi::class.java.simpleName
//             )
//             openApi.paths?.entries?.removeIf { entry ->
//                 entry.value.readOperations().any { operation ->
//                     operation.tags.any { hiddenAnnotations.contains(it) }
//                 }
//             }
//         }
//     }

//        val docket = Docket(DocumentationType.OAS_30)
//            .apiInfo(apiInfo)
//            .host(props.swaggerUrl)
//            .protocols(setOf(props.swaggerProtocol))
//            .consumes(setOf(MediaType.APPLICATION_JSON_VALUE))
//            .produces(setOf(MediaType.APPLICATION_JSON_VALUE))
//            .forCodeGeneration(true)
//            .securityContexts(
//                listOf(
//                    SecurityContext.builder()
//                        .securityReferences(listOf(SecurityReference(AUTHORIZATION, emptyArray())))
//                        .build()
//                )
//            )
//            .securitySchemes(listOf(ApiKey(AUTHORIZATION, AUTHORIZATION, "header")))
//            .select()
//            .apis(RequestHandlerSelectors.basePackage("io.provenance.explorer.web"))
//
//        if (props.hiddenApis()) {
//            docket.apis(Predicate.not(RequestHandlerSelectors.withClassAnnotation(HiddenApi::class.java)))
//                .apis(Predicate.not(RequestHandlerSelectors.withMethodAnnotation(HiddenApi::class.java)))
//        }
//
//        return docket.build()
//    }

//    @Suppress("UNCHECKED_CAST")
//    @Bean
//    fun springfoxHandlerProviderBeanPostProcessor(): BeanPostProcessor? {
//        return object : BeanPostProcessor {
//            @Throws(BeansException::class)
//            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
//                if (bean is WebMvcRequestHandlerProvider || bean is WebFluxRequestHandlerProvider) {
//                    customizeSpringfoxHandlerMappings(getHandlerMappings(bean))
//                }
//                return bean
//            }
//
//            private fun <T : RequestMappingInfoHandlerMapping?> customizeSpringfoxHandlerMappings(mappings: MutableList<T>) {
//                mappings.removeIf { mapping -> mapping?.patternParser != null }
//            }
//
//            private fun getHandlerMappings(bean: Any): MutableList<RequestMappingInfoHandlerMapping> {
//                return try {
//                    val field = ReflectionUtils.findField(bean.javaClass, "handlerMappings")
//                    field?.isAccessible = true
//                    field?.get(bean) as MutableList<RequestMappingInfoHandlerMapping>
//                } catch (e: Exception) {
//                    throw IllegalStateException(e)
//                }
//            }
//        }
//    }
 }
