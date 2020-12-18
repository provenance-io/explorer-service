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

    //tp or pb
    fun provenanceAccountPrefix() = if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_ACCOUNT_PREFIX else Bech32.PROVENANCE_TESTNET_ACCOUNT_PREFIX

    //valcons
    fun provenanceValidatorConsensusPrefix() = if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_CONSENSUS_ACCOUNT_PREFIX else Bech32.PROVENANCE_TESTNET_CONSENSUS_ACCOUNT_PREFIX

    //valconspub
    fun provenanceValidatorConsensusPubKeyPrefix() = if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_CONSENSUS_PUBKEY_PREFIX else Bech32.PROVENANCE_TESTNET_CONSENSUS_PUBKEY_PREFIX
//TODO fix once shared lib is updated
    //valoper
//    fun provenanceValidatorOperatorPrefix() = if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_VALIDATOR_ACCOUNT_PREFIX else Bech32.PROVENANCE_TESTNET_VALIDATOR_ACCOUNT_PREFIX
    fun provenanceValidatorOperatorPrefix() = if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_PREFIX + "valoper" else Bech32.PROVENANCE_TESTNET_PREFIX + "valoper"

    //valoperpub
//    fun provenanceValidatorOperatorPubKeyPrefix() = if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_VALIDATOR_PUBKEY_PREFIX else Bech32.PROVENANCE_TESTNET_VALIDATOR_PUBKEY_PREFIX
    fun provenanceValidatorOperatorPubKeyPrefix() = if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_PREFIX + "valoperpub" else Bech32.PROVENANCE_TESTNET_PREFIX + "valoperpub"
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