package io.provenance.explorer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import javax.validation.constraints.NotNull

@ConfigurationProperties(prefix = "provenance-explorer")
@Validated
class ExplorerProperties {

    @NotNull
    lateinit var tendermintUrl: String

    @NotNull
    lateinit var pbUrl: String

    @NotNull
    lateinit var initialHistoryicalDayCount: String

    @NotNull
    lateinit var minimumGasPrice: String

    @NotNull
    lateinit var spotlightTtlMs: String

    fun initialHistoryicalDays() = initialHistoryicalDayCount.toInt()

    fun minGasPrice() = minimumGasPrice.toBigDecimal()

    fun spotlightTtlMs() = spotlightTtlMs.toLong()
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