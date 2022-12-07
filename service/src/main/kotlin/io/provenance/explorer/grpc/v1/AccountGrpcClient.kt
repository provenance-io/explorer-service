package io.provenance.explorer.grpc.v1

import cosmos.auth.v1beta1.queryAccountRequest
import cosmos.bank.v1beta1.Bank
import cosmos.bank.v1beta1.queryAllBalancesRequest
import cosmos.bank.v1beta1.queryAllBalancesResponse
import cosmos.bank.v1beta1.queryBalanceRequest
import cosmos.bank.v1beta1.queryDenomMetadataRequest
import cosmos.bank.v1beta1.queryDenomMetadataResponse
import cosmos.bank.v1beta1.queryDenomOwnersRequest
import cosmos.bank.v1beta1.queryDenomOwnersResponse
import cosmos.bank.v1beta1.queryDenomsMetadataRequest
import cosmos.bank.v1beta1.queryParamsRequest
import cosmos.bank.v1beta1.querySpendableBalancesRequest
import cosmos.bank.v1beta1.querySupplyOfRequest
import cosmos.base.v1beta1.CoinOuterClass
import cosmos.distribution.v1beta1.queryCommunityPoolRequest
import cosmos.distribution.v1beta1.queryDelegationTotalRewardsRequest
import cosmos.distribution.v1beta1.queryDelegationTotalRewardsResponse
import cosmos.staking.v1beta1.pool
import cosmos.staking.v1beta1.queryDelegatorDelegationsRequest
import cosmos.staking.v1beta1.queryDelegatorDelegationsResponse
import cosmos.staking.v1beta1.queryDelegatorUnbondingDelegationsRequest
import cosmos.staking.v1beta1.queryDelegatorUnbondingDelegationsResponse
import cosmos.staking.v1beta1.queryPoolRequest
import cosmos.staking.v1beta1.queryPoolResponse
import cosmos.staking.v1beta1.queryRedelegationsRequest
import cosmos.staking.v1beta1.queryRedelegationsResponse
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ExplorerProperties.Companion.UNDER_MAINTENANCE
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.domain.extensions.defaultCoin
import io.provenance.explorer.domain.extensions.toDecimalStringOld
import io.provenance.explorer.grpc.extensions.addBlockHeightToQuery
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
                .idleTimeout(5, TimeUnit.MINUTES)
                .keepAliveTime(60, TimeUnit.SECONDS)
                .keepAliveTimeout(20, TimeUnit.SECONDS)
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
        if (UNDER_MAINTENANCE) queryAllBalancesResponse { }
        else
            bankClient.allBalances(
                queryAllBalancesRequest {
                    this.address = address
                    this.pagination = getPagination(offset, limit)
                }
            )

    suspend fun getAccountBalancesAll(address: String): MutableList<CoinOuterClass.Coin> {
        if (UNDER_MAINTENANCE) return mutableListOf()
        var (offset, limit) = 0 to 100

        val results =
            bankClient.allBalances(
                queryAllBalancesRequest {
                    this.address = address
                    this.pagination = getPagination(offset, limit)
                }
            )

        val total = results.pagination?.total ?: results.balancesCount.toLong()
        val balances = results.balancesList.toMutableList()

        while (balances.count() < total) {
            offset += limit
            bankClient.allBalances(
                queryAllBalancesRequest {
                    this.address = address
                    this.pagination = getPagination(offset, limit)
                }
            ).let { balances.addAll(it.balancesList) }
        }
        return balances
    }

    suspend fun getAccountBalanceForDenom(address: String, denom: String) =
        if (UNDER_MAINTENANCE) defaultCoin(denom)
        else
            bankClient.balance(
                queryBalanceRequest {
                    this.address = address
                    this.denom = denom
                }
            ).balance

    suspend fun getAccountBalancesAllAtHeight(address: String, height: Int): MutableList<CoinOuterClass.Coin> {
        var (offset, limit) = 0 to 100

        val results =
            bankClient
                .addBlockHeightToQuery(height.toString())
                .allBalances(
                    queryAllBalancesRequest {
                        this.address = address
                        this.pagination = getPagination(offset, limit)
                    }
                )

        val total = results.pagination?.total ?: results.balancesCount.toLong()
        val balances = results.balancesList.toMutableList()

        while (balances.count() < total) {
            offset += limit
            bankClient
                .addBlockHeightToQuery(height.toString())
                .allBalances(
                    queryAllBalancesRequest {
                        this.address = address
                        this.pagination = getPagination(offset, limit)
                    }
                ).let { balances.addAll(it.balancesList) }
        }
        return balances
    }

    suspend fun getAccountBalanceForDenomAtHeight(address: String, denom: String, height: Int) =
        bankClient
            .addBlockHeightToQuery(height.toString())
            .balance(
                queryBalanceRequest {
                    this.address = address
                    this.denom = denom
                }
            ).balance

    suspend fun getSpendableBalancesAll(address: String): MutableList<CoinOuterClass.Coin> {
        if (UNDER_MAINTENANCE) return mutableListOf()
        var (offset, limit) = 0 to 100

        val results =
            bankClient.spendableBalances(
                querySpendableBalancesRequest {
                    this.address = address
                    this.pagination = getPagination(offset, limit)
                }
            )

        val total = results.pagination?.total ?: results.balancesCount.toLong()
        val balances = results.balancesList.toMutableList()

        while (balances.count() < total) {
            offset += limit
            bankClient.spendableBalances(
                querySpendableBalancesRequest {
                    this.address = address
                    this.pagination = getPagination(offset, limit)
                }
            ).let { balances.addAll(it.balancesList) }
        }
        return balances
    }

    suspend fun getSpendableBalanceDenom(address: String, denom: String): CoinOuterClass.Coin? {
        if (ExplorerProperties.UNDER_MAINTENANCE) return defaultCoin(denom)
        var (offset, limit) = 0 to 100
        var balance: CoinOuterClass.Coin?
        var noInfo = false
        do {
            val req = bankClient.spendableBalances(
                querySpendableBalancesRequest {
                    this.address = address
                    this.pagination = getPagination(offset, limit)
                }
            ).balancesList
            if (req.size == 0) noInfo = true
            balance = req.firstOrNull { it.denom == denom }
            offset += limit
        } while (!noInfo && balance == null)
        return balance
    }

    suspend fun getCurrentSupply(denom: String) =
        if (UNDER_MAINTENANCE) defaultCoin(denom)
        else bankClient.supplyOf(querySupplyOfRequest { this.denom = denom }).amount

    suspend fun getDenomMetadata(denom: String) =
        try {
            bankClient.denomMetadata(queryDenomMetadataRequest { this.denom = denom })
        } catch (e: Exception) {
            queryDenomMetadataResponse { }
        }

    suspend fun getAllDenomMetadata(): MutableList<Bank.Metadata> {
        if (UNDER_MAINTENANCE) return mutableListOf()
        var (offset, limit) = 0 to 100

        val results =
            bankClient.denomsMetadata(queryDenomsMetadataRequest { this.pagination = getPagination(offset, limit) })

        val total = results.pagination?.total ?: results.metadatasCount.toLong()
        val metadatas = results.metadatasList.toMutableList()

        while (metadatas.count() < total) {
            offset += limit
            bankClient.denomsMetadata(queryDenomsMetadataRequest { this.pagination = getPagination(offset, limit) })
                .let { metadatas.addAll(it.metadatasList) }
        }
        return metadatas
    }

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
        try {
            stakingClient.delegatorUnbondingDelegations(
                queryDelegatorUnbondingDelegationsRequest {
                    this.delegatorAddr = address
                    this.pagination = getPagination(offset, limit)
                }
            )
        } catch (e: Exception) {
            queryDelegatorUnbondingDelegationsResponse { }
        }

    suspend fun getRedelegations(address: String, offset: Int, limit: Int) =
        try {
            stakingClient.redelegations(
                queryRedelegationsRequest {
                    this.delegatorAddr = address
                    this.pagination = getPagination(offset, limit)
                }
            )
        } catch (e: Exception) {
            queryRedelegationsResponse { }
        }

    suspend fun getStakingPool() =
        if (UNDER_MAINTENANCE) queryPoolResponse { this.pool = pool { this.bondedTokens = "0" } }
        else
            stakingClient.pool(queryPoolRequest { })

    suspend fun getRewards(delAddr: String) =
        try {
            distClient.delegationTotalRewards(queryDelegationTotalRewardsRequest { this.delegatorAddress = delAddr })
        } catch (e: Exception) {
            queryDelegationTotalRewardsResponse { }
        }

    suspend fun getCommunityPoolAmount(denom: String): String =
        if (UNDER_MAINTENANCE) "0"
        else
            distClient.communityPool(queryCommunityPoolRequest { }).poolList.filter { it.denom == denom }[0]?.amount!!.toDecimalStringOld()

    suspend fun getMarkerBalance(address: String, denom: String): String =
        if (UNDER_MAINTENANCE) "0"
        else
            bankClient.balance(
                queryBalanceRequest {
                    this.address = address
                    this.denom = denom
                }
            ).balance.amount

    suspend fun getBankParams() = bankClient.params(queryParamsRequest { })

    suspend fun getAuthParams() = authClient.params(cosmos.auth.v1beta1.queryParamsRequest { })

    suspend fun getMintParams() = mintClient.params(cosmos.mint.v1beta1.queryParamsRequest { })

    suspend fun getDenomHolders(denom: String, offset: Int, count: Int) =
        try {
            bankClient.denomOwners(
                queryDenomOwnersRequest {
                    this.denom = denom
                    this.pagination = getPagination(offset, count)
                }
            )
        } catch (e: Exception) {
            queryDenomOwnersResponse { }
        }
}
