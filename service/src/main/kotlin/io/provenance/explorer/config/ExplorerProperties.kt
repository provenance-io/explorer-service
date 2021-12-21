package io.provenance.explorer.config

import io.provenance.explorer.domain.core.Bech32
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import javax.validation.constraints.NotNull

@ConfigurationProperties(prefix = "explorer")
@Validated
class ExplorerProperties {

    @NotNull
    lateinit var mainnet: String

    @NotNull
    lateinit var pbUrl: String

    @NotNull
    lateinit var initialHistoricalDayCount: String

    @NotNull
    lateinit var spotlightTtlMs: String

    @NotNull
    lateinit var figmentApikey: String

    @NotNull
    lateinit var figmentUrl: String

    @NotNull
    lateinit var genesisVersionUrl: String

    @NotNull
    lateinit var upgradeVersionRegex: String

    @NotNull
    lateinit var hiddenApis: String

    fun initialHistoricalDays() = initialHistoricalDayCount.toInt()

    fun spotlightTtlMs() = spotlightTtlMs.toLong()

    fun hiddenApis() = hiddenApis.toBoolean()

    // tp or pb
    fun provAccPrefix() =
        if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_ACCOUNT_PREFIX
        else Bech32.PROVENANCE_TESTNET_ACCOUNT_PREFIX

    // valoper
    fun provValOperPrefix() =
        if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_VALIDATOR_ACCOUNT_PREFIX
        else Bech32.PROVENANCE_TESTNET_VALIDATOR_ACCOUNT_PREFIX

    // valcons
    fun provValConsPrefix() =
        if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_CONSENSUS_ACCOUNT_PREFIX
        else Bech32.PROVENANCE_TESTNET_CONSENSUS_ACCOUNT_PREFIX
}
