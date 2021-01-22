package io.provenance.explorer.config

import io.provenance.explorer.domain.Bech32
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import javax.validation.constraints.NotNull

@ConfigurationProperties(prefix = "provenance-explorer")
@Validated
class ExplorerProperties {

    @NotNull
    lateinit var mainnet: String

    @NotNull
    lateinit var tendermintClientTimeoutMs: String

    @NotNull
    lateinit var tendermintUrl: String

    @NotNull
    lateinit var pbUrl: String

    @NotNull
    lateinit var pbClientTimeoutMs: String

    @NotNull
    lateinit var initialHistoricalDayCount: String

    @NotNull
    lateinit var minimumGasPrice: String

    @NotNull
    lateinit var spotlightTtlMs: String

    @NotNull
    lateinit var stakingValidatorTtlMs: String

    @NotNull
    lateinit var stakingValidatorDelegationsTtlMs: String

    fun tendermintClientTimeoutMs() = tendermintClientTimeoutMs.toInt()

    fun pbClientTimeoutMs() = pbClientTimeoutMs.toInt()

    fun initialHistoricalDays() = initialHistoricalDayCount.toInt()

    fun minGasPrice() = minimumGasPrice.toBigDecimal()

    fun spotlightTtlMs() = spotlightTtlMs.toLong()

    fun stakingValidatorTtlMs() = stakingValidatorTtlMs.toLong()

    fun stakingValidatorDelegationsTtlMs() = stakingValidatorDelegationsTtlMs.toLong()

    //tp or pb
    fun provenanceAccountPrefix() = if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_ACCOUNT_PREFIX else Bech32.PROVENANCE_TESTNET_ACCOUNT_PREFIX

    //valcons
    fun provenanceValidatorConsensusPrefix() = if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_CONSENSUS_ACCOUNT_PREFIX else Bech32.PROVENANCE_TESTNET_CONSENSUS_ACCOUNT_PREFIX

    //valconspub
    fun provenanceValidatorConsensusPubKeyPrefix() = if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_CONSENSUS_PUBKEY_PREFIX else Bech32.PROVENANCE_TESTNET_CONSENSUS_PUBKEY_PREFIX

    //valoper
    fun provenanceValidatorOperatorPrefix() = if (mainnet.toBoolean()) Bech32.PROVENANCE_MAINNET_VALIDATOR_ACCOUNT_PREFIX else Bech32.PROVENANCE_TESTNET_VALIDATOR_ACCOUNT_PREFIX
}
