package io.provenance.explorer.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiInfo
import springfox.documentation.service.Contact
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2

@Configuration
@EnableSwagger2
class SwaggerConfig {

    @Bean
    fun api(): Docket {
        val contact = Contact("Provenance", "provenance.io", "info@provenance.io")

        val apiInfo = ApiInfo(
            "Provenance Explorer",
            "Provenance Explorer",
            "1",
            "",
            contact,
            "",
            "",
            listOf()
        )

        return Docket(DocumentationType.SWAGGER_2)
            .apiInfo(apiInfo)
            .forCodeGeneration(true)
            .select()
            .apis(RequestHandlerSelectors.basePackage("io.provenance.explorer.web"))
            .build()
    }
}
