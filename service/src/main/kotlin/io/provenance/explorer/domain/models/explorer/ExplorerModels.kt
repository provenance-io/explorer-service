package io.provenance.explorer.domain.models.explorer

import cosmos.bank.v1beta1.QueryOuterClass as BankOuterClass
import cosmos.auth.v1beta1.QueryOuterClass as AuthOuterClass
import cosmos.distribution.v1beta1.QueryOuterClass as DistOuterClass
import cosmos.staking.v1beta1.QueryOuterClass as StakingOuterClass
import ibc.applications.transfer.v1.QueryOuterClass as TransferOuterClass
import ibc.core.client.v1.QueryOuterClass as ClientOuterClass
import io.provenance.attribute.v1.QueryParamsResponse as AttrResponse
import io.provenance.name.v1.QueryParamsResponse as NameResponse
import io.provenance.metadata.v1.QueryParamsResponse as MetadataResponse
import io.provenance.marker.v1.QueryParamsResponse as MarkerResponse
import cosmos.slashing.v1beta1.QueryOuterClass as SlashingOuterClass
import cosmos.gov.v1beta1.QueryOuterClass as GovClass
import java.math.BigDecimal
import java.math.BigInteger

data class BlockSummary(
    val height: Int,
    val hash: String,
    val time: String,
    val proposerAddress: String,
    val moniker: String,
    val icon: String,
    val votingPower: CountTotal,
    val validatorCount: CountTotal,
    val txNum: Int
)

data class Spotlight(
    val latestBlock: BlockSummary,
    val avgBlockTime: BigDecimal,
    val bondedTokens: CountStrTotal,
    val totalTxCount: BigInteger
)

data class GasStatistics(
    val time: String,
    val minGasPrice: Int,
    val maxGasPrice: Int,
    val averageGasPrice: BigDecimal
)

data class Params(
    val cosmos: CosmosParams,
    val prov: ProvParams,
)

data class CosmosParams(
    val authParams: AuthOuterClass.QueryParamsResponse,
    val bankParams: BankOuterClass.QueryParamsResponse,
    val distParams: DistOuterClass.QueryParamsResponse,
    val govParams: GovParams,
    // mint
    val slashingParams: SlashingOuterClass.QueryParamsResponse,
    val stakingParams: StakingOuterClass.QueryParamsResponse,
    val ibc: IBCParams,
)

data class GovParams(
    val voting: GovClass.QueryParamsResponse,
    val tallying: GovClass.QueryParamsResponse,
    val deposit: GovClass.QueryParamsResponse,
)

data class IBCParams(
    val transferParams: TransferOuterClass.QueryParamsResponse,
    val clientParams: ClientOuterClass.QueryClientParamsResponse,
)

data class ProvParams(
    val attribute: AttrResponse,
    val marker: MarkerResponse,
    val metadata: MetadataResponse,
    val name: NameResponse,
)
