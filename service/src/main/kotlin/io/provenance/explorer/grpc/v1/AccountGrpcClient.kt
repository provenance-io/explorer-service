package io.provenance.explorer.grpc.v1

import cosmos.auth.v1beta1.queryAccountRequest
import cosmos.bank.v1beta1.queryAllBalancesRequest
import cosmos.bank.v1beta1.queryDenomMetadataRequest
import cosmos.bank.v1beta1.queryDenomMetadataResponse
import cosmos.bank.v1beta1.queryDenomsMetadataRequest
import cosmos.bank.v1beta1.queryParamsRequest
import cosmos.bank.v1beta1.querySupplyOfRequest
import cosmos.distribution.v1beta1.queryDelegationTotalRewardsRequest
import cosmos.staking.v1beta1.queryDelegatorDelegationsRequest
import cosmos.staking.v1beta1.queryDelegatorDelegationsResponse
import cosmos.staking.v1beta1.queryDelegatorUnbondingDelegationsRequest
import cosmos.staking.v1beta1.queryRedelegationsRequest
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.getPagination
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import cosmos.auth.v1beta1.QueryGrpcKt as AuthQueryGrpc
import cosmos.bank.v1beta1.QueryGrpcKt as BankQueryGrpc
import cosmos.distribution.v1beta1.QueryGrpcKt as DistGrpc
import cosmos.mint.v1beta1.QueryGrpcKt as MintGrpc
import cosmos.staking.v1beta1.QueryGrpcKt as StakingGrpc

@Component
class AccountGrpcClient(channelUri: URI) {

    private val authClient: AuthQueryGrpc.QueryCoroutineStub
    private val bankClient: BankQueryGrpc.QueryCoroutineStub
    private val stakingClient: StakingGrpc.QueryCoroutineStub
    private val distClient: DistGrpc.QueryCoroutineStub
    private val mintClient: MintGrpc.QueryCoroutineStub

    init {
        val channel =
            ManagedChannelBuilder.forAddress(channelUri.host, channelUri.port)
                .also {
                    if (channelUri.scheme == "grpcs") {
                        it.useTransportSecurity()
                    } else {
                        it.usePlaintext()
                    }
                }
                .idleTimeout(60, TimeUnit.SECONDS)
                .keepAliveTime(10, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .intercept(GrpcLoggingInterceptor())
                .build()

        authClient = AuthQueryGrpc.QueryCoroutineStub(channel)
        bankClient = BankQueryGrpc.QueryCoroutineStub(channel)
        stakingClient = StakingGrpc.QueryCoroutineStub(channel)
        distClient = DistGrpc.QueryCoroutineStub(channel)
        mintClient = MintGrpc.QueryCoroutineStub(channel)
    }

    suspend fun getAccountInfo(address: String) =
        try {
            authClient.account(queryAccountRequest { this.address = address }).account
        } catch (e: Exception) {
            null
        }

    suspend fun getAccountBalances(address: String, offset: Int, limit: Int) =
        bankClient.allBalances(
            queryAllBalancesRequest {
                this.address = address
                this.pagination = getPagination(offset, limit)
            }
        )

    suspend fun getCurrentSupply(denom: String) =
        bankClient.supplyOf(querySupplyOfRequest { this.denom = denom }).amount

    suspend fun getDenomMetadata(denom: String) =
        try {
            bankClient.denomMetadata(queryDenomMetadataRequest { this.denom = denom })
        } catch (e: Exception) {
            queryDenomMetadataResponse { }
        }

    suspend fun getAllDenomMetadata() =
        bankClient.denomsMetadata(queryDenomsMetadataRequest { this.pagination = getPagination(0, 100) })

    suspend fun getDelegations(address: String, offset: Int, limit: Int) =
        try {
            stakingClient.delegatorDelegations(
                queryDelegatorDelegationsRequest {
                    this.delegatorAddr = address
                    this.pagination = getPagination(offset, limit)
                }
            )
        } catch (e: Exception) {
            queryDelegatorDelegationsResponse { }
        }

    suspend fun getUnbondingDelegations(address: String, offset: Int, limit: Int) =
        stakingClient.delegatorUnbondingDelegations(
            queryDelegatorUnbondingDelegationsRequest {
                this.delegatorAddr = address
                this.pagination = getPagination(offset, limit)
            }
        )

    suspend fun getRedelegations(address: String, offset: Int, limit: Int) =
        stakingClient.redelegations(
            queryRedelegationsRequest {
                this.delegatorAddr = address
                this.pagination = getPagination(offset, limit)
            }
        )

    suspend fun getRewards(delAddr: String) =
        distClient.delegationTotalRewards(queryDelegationTotalRewardsRequest { this.delegatorAddress = delAddr })

    suspend fun getBankParams() = bankClient.params(queryParamsRequest { })

    suspend fun getAuthParams() = authClient.params(cosmos.auth.v1beta1.queryParamsRequest { })

    suspend fun getMintParams() = mintClient.params(cosmos.mint.v1beta1.queryParamsRequest { })
}
