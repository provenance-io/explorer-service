package io.provenance.explorer.domain.models.explorer

import com.google.protobuf.Descriptors
import com.google.protobuf.Duration
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
import cosmos.mint.v1beta1.QueryOuterClass as MintOuterClass
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

//data class Params(
//    val govParams: GovParams
////    val deposit: String, //cosmos.gov.v1beta1.Gov.DepositParams //GovClass.QueryParamsResponse,
////    val depositParams: String,
////    val depositVal: Duration
////    val depositParams: Map<Descriptors.FieldDescriptor, Object> // cosmos.gov.v1beta1.Gov.DepositParams,
//)

data class DepositParams(
    val isInitialized: Boolean,
    val maxDepositPeriod: com.google.protobuf.Duration,
    val minDepositCount: Int,
    val minDepositList: List<cosmos.base.v1beta1.CoinOuterClass.Coin>,
)

data class Params(
    val cosmos: CosmosParams,
    val prov: ProvParams,
)

data class CosmosParams(
    val authParams: String, //AuthOuterClass.QueryParamsResponse,
    val bankParams: String, //BankOuterClass.QueryParamsResponse,
    val distParams: String, //DistOuterClass.QueryParamsResponse,
    val govParams: GovParams,
    val mint: String, //MintOuterClass.QueryParamsResponse,
    val slashingParams: String, //SlashingOuterClass.QueryParamsResponse,
    val stakingParams: String, //StakingOuterClass.QueryParamsResponse,
    val ibc: IBCParams,
)

data class GovParams(
    val voting: String, //GovClass.QueryParamsResponse,
    val tallying: String, //GovClass.QueryParamsResponse,
    val deposit: String, //GovClass.QueryParamsResponse,
)

data class IBCParams(
    val transferParams: String, //TransferOuterClass.QueryParamsResponse,
    val clientParams: String, //ClientOuterClass.QueryClientParamsResponse,
)

data class ProvParams(
    val attribute: String, //AttrResponse,
    val marker: String, //MarkerResponse,
    val metadata: String, //MetadataResponse,
    val name: String, //NameResponse,
)
