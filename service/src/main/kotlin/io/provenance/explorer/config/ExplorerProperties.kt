package io.provenance.explorer.config

import io.provenance.explorer.model.base.Bech32
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.validation.annotation.Validated
import java.math.BigDecimal

@ConfigurationProperties(prefix = "explorer")
@Validated
@ConstructorBinding
class ExplorerProperties(
    val mainnet: String,
    val pbUrl: String,
    val flowApiUrl: String,
    val initialHistoricalDayCount: String,
    val genesisVersionUrl: String,
    val upgradeVersionRegex: String,
    val upgradeGithubRepo: String,
    val hiddenApis: String,
    val swaggerUrl: String,
    val swaggerProtocol: String,
    val pricingUrl: String,
    val feeBugRangeOneEleven: List<Int> // [0] is the beginning of the range, [1] is the end of the range, inclusive
) {

    fun initialHistoricalDays() = initialHistoricalDayCount.toInt()

    fun hiddenApis() = hiddenApis.toBoolean()

    fun oneElevenBugRange() =
        if (feeBugRangeOneEleven[0] == 0) {
            null
        } else {
            feeBugRangeOneEleven[0]..feeBugRangeOneEleven[1]
        }

    fun inOneElevenBugRange(height: Int) = oneElevenBugRange()?.contains(height) ?: false

    companion object {
        var UTILITY_TOKEN = "nhash"
        var UTILITY_TOKEN_DEFAULT_GAS_PRICE = 1905
        var UTILITY_TOKEN_BASE_DECIMAL_PLACES = 9

        // The number to divide the base value by to get the display value, or vice versa
        var UTILITY_TOKEN_BASE_MULTIPLIER = BigDecimal(1000000000)
        var VOTING_POWER_PADDING = 1000000
        var PROV_ACC_PREFIX = Bech32.PROVENANCE_TESTNET_ACCOUNT_PREFIX
        var PROV_VAL_OPER_PREFIX = Bech32.PROVENANCE_TESTNET_VALIDATOR_ACCOUNT_PREFIX
        var PROV_VAL_CONS_PREFIX = Bech32.PROVENANCE_TESTNET_CONSENSUS_ACCOUNT_PREFIX
    }

    @Value("\${explorer.utility-token}")
    fun setUtilityToken(utilityToken: String) {
        UTILITY_TOKEN = utilityToken
    }

    @Value("\${explorer.utility-token-default-gas-price}")
    fun setUtilityTokenDefaultGasPrice(utilityTokenDefaultGasPrice: Int) {
        UTILITY_TOKEN_DEFAULT_GAS_PRICE = utilityTokenDefaultGasPrice
    }

    @Value("\${explorer.utility-token-base-decimal-places}")
    fun setUtilityTokenBaseDecimalPlaces(utilityTokenBaseDecimalPlaces: Int) {
        UTILITY_TOKEN_BASE_DECIMAL_PLACES = utilityTokenBaseDecimalPlaces
        UTILITY_TOKEN_BASE_MULTIPLIER = BigDecimal("1e$UTILITY_TOKEN_BASE_DECIMAL_PLACES")
    }

    @Value("\${explorer.voting-power-padding}")
    fun setVotingPowerPadding(votingPowerPadding: Int) {
        VOTING_POWER_PADDING = votingPowerPadding
    }

    @Value("\${explorer.mainnet}")
    fun setAddressPrefixes(mainnet: String) {
        if (mainnet.toBoolean()) {
            PROV_ACC_PREFIX = Bech32.PROVENANCE_MAINNET_ACCOUNT_PREFIX
            PROV_VAL_OPER_PREFIX = Bech32.PROVENANCE_MAINNET_VALIDATOR_ACCOUNT_PREFIX
            PROV_VAL_CONS_PREFIX = Bech32.PROVENANCE_MAINNET_CONSENSUS_ACCOUNT_PREFIX
        } else {
            PROV_ACC_PREFIX = Bech32.PROVENANCE_TESTNET_ACCOUNT_PREFIX
            PROV_VAL_OPER_PREFIX = Bech32.PROVENANCE_TESTNET_VALIDATOR_ACCOUNT_PREFIX
            PROV_VAL_CONS_PREFIX = Bech32.PROVENANCE_TESTNET_CONSENSUS_ACCOUNT_PREFIX
        }
    }
}
