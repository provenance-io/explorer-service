 package io.provenance.explorer.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

 @Configuration
 @OpenAPIDefinition(
     servers = [
         io.swagger.v3.oas.annotations.servers.Server(
             url = "http://localhost:8612",
             description = "Local environment",
         ),
         io.swagger.v3.oas.annotations.servers.Server(
             url = "https://service-explorer.provenance.io",
             description = "Sandbox environment",
         ),
         io.swagger.v3.oas.annotations.servers.Server(
             url = "https://service-explorer.provenance.io",
             description = "Production environment",
         ),
     ],
 )
 class OpenApiConfig(val props: ExplorerProperties) {
     @Bean
     fun explorerApi() = GroupedOpenApi.builder()
         .group("explorer")
         .displayName("Explorer Public API")
         .packagesToScan("io.provenance.explorer.web")
         .build()

    @Bean
    fun api(): OpenAPI {
        val contact = Contact()
            .name("Provenance Blockchain Foundation")
            .url(props.swaggerUrl)
            .email("info@provenance.io")

        val apiInfo = Info().title("Provenance Blockchain Explorer")
            .description("Blockchain Explorer API")
            .version("v3")
            .contact(contact)

        return OpenAPI()
            .info(apiInfo)
    }
 }
