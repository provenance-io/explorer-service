package io.provenance.explorer.config

import io.provenance.explorer.domain.annotation.HiddenApi
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.AUTHORIZATION
import org.springframework.http.MediaType
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiInfo
import springfox.documentation.service.ApiKey
import springfox.documentation.service.Contact
import springfox.documentation.service.SecurityReference
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spi.service.contexts.SecurityContext
import springfox.documentation.spring.web.plugins.Docket
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
                        .securityReferences(listOf(SecurityReference("Bearer Token", emptyArray())))
                        .build()
                )
            )
            .securitySchemes(listOf(ApiKey("Bearer Token", AUTHORIZATION, "header")))
            .select()
            .apis(RequestHandlerSelectors.basePackage("io.provenance.explorer.web"))

        if (props.hiddenApis())
            docket.apis(Predicate.not(RequestHandlerSelectors.withClassAnnotation(HiddenApi::class.java)))
                .apis(Predicate.not(RequestHandlerSelectors.withMethodAnnotation(HiddenApi::class.java)))

        return docket.build()
    }
}
