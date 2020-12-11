package io.provenance.explorer.config

import io.p8e.crypto.Bech32
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import javax.validation.constraints.NotNull

@ConfigurationProperties(prefix = "provenance-explorer")
@Validated
class ExplorerProperties {

    @NotNull
    lateinit var mainnet: String

    @NotNull
    lateinit var tendermintUrl: String

    @NotNull
    lateinit var pbUrl: String

    @NotNull
    lateinit var initialHistoricalDayCount: String

    @NotNull
    lateinit var minimumGasPrice: String

    @NotNull
    lateinit var spotlightTtlMs: String

    fun initialHistoricalDays() = initialHistoricalDayCount.toInt()

    fun minGasPrice() = minimumGasPrice.toBigDecimal()

    fun spotlightTtlMs() = spotlightTtlMs.toLong()

    fun provenancePrefix() = if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_PREFIX else Bech32.PROVENANCE_TESTNET_PREFIX
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