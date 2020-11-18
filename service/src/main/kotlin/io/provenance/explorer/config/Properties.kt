package io.provenance.explorer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.validation.annotation.Validated
import javax.validation.constraints.AssertTrue
import javax.validation.constraints.NotNull

@ConfigurationProperties(prefix = "provenance-explorer")
@Validated
class ExplorerProperties {

    @NotNull
    lateinit var pbUrl: String

    @NotNull
    lateinit var txsCountInitDays: String

    @NotNull
    lateinit var txsMetricJobEnabled: String

    @NotNull
    lateinit var minimumGasPrice: String

    fun txsInitDays() = txsCountInitDays.toInt()

    fun isMetricJobEnabled() = txsMetricJobEnabled.toBoolean()

    fun minGasPrice() = minimumGasPrice.toDouble()

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