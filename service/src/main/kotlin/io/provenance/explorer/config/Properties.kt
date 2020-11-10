package io.provenance.explorer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.validation.annotation.Validated
import javax.validation.constraints.NotNull

@ConfigurationProperties(prefix="provenance-explorer")
@Validated
class ExplorerProperties {

    @NotNull
    lateinit var pbUrl: String

}

@ConfigurationProperties(prefix = "service")
@Validated
class ServiceProperties {

    @NotNull
    lateinit var name: String

    @NotNull
    lateinit var environment: String

    @NotNull
    lateinit var adminUUID: String
}