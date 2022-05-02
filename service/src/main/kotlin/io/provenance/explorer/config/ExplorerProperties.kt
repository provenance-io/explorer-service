package io.provenance.explorer.config

import io.provenance.explorer.domain.core.Bech32
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "explorer")
@Validated
@ConstructorBinding
class ExplorerProperties(
    val mainnet: String,
    val pbUrl: String,
    val initialHistoricalDayCount: String,
    val spotlightTtlMs: String,
    val figmentApikey: String,
    val figmentUrl: String,
    val genesisVersionUrl: String,
    val upgradeVersionRegex: String,
    val upgradeGithubRepo: String,
    val hiddenApis: String,
    val swaggerUrl: String,
    val swaggerProtocol: String,
    val pricingUrl: String
) {

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
