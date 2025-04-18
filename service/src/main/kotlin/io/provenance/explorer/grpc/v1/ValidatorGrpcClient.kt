package io.provenance.explorer.grpc.v1

import cosmos.base.tendermint.v1beta1.Query
import cosmos.base.tendermint.v1beta1.ServiceGrpc
import cosmos.base.tendermint.v1beta1.getLatestValidatorSetRequest
import cosmos.base.tendermint.v1beta1.getValidatorSetByHeightRequest
import cosmos.distribution.v1beta1.queryDelegatorWithdrawAddressRequest
import cosmos.distribution.v1beta1.queryValidatorCommissionRequest
import cosmos.distribution.v1beta1.queryValidatorOutstandingRewardsRequest
import cosmos.slashing.v1beta1.Slashing
import cosmos.slashing.v1beta1.queryParamsRequest
import cosmos.slashing.v1beta1.querySigningInfosRequest
import cosmos.staking.v1beta1.Staking
import cosmos.staking.v1beta1.queryDelegationRequest
import cosmos.staking.v1beta1.queryValidatorDelegationsRequest
import cosmos.staking.v1beta1.queryValidatorRequest
import cosmos.staking.v1beta1.queryValidatorUnbondingDelegationsRequest
import cosmos.staking.v1beta1.queryValidatorsRequest
import cosmos.staking.v1beta1.validator
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.addBlockHeightToQuery
import io.provenance.explorer.grpc.extensions.getPagination
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit
import cosmos.distribution.v1beta1.QueryGrpc as DistGrpc
import cosmos.slashing.v1beta1.QueryGrpc as SlashingGrpc
import cosmos.staking.v1beta1.QueryGrpc as StakingGrpc

@Component
class ValidatorGrpcClient(channelUri: URI) {

    private val tmClient: ServiceGrpc.ServiceBlockingStub
    private val stakingClient: StakingGrpc.QueryBlockingStub
    private val distClient: DistGrpc.QueryBlockingStub
    private val slashingClient: SlashingGrpc.QueryBlockingStub

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

        tmClient = ServiceGrpc.newBlockingStub(channel)
        stakingClient = StakingGrpc.newBlockingStub(channel)
        distClient = DistGrpc.newBlockingStub(channel)
        slashingClient = SlashingGrpc.newBlockingStub(channel)
    }

    fun getLatestValidators(): Query.GetLatestValidatorSetResponse {
        val limit = getStakingParams().params.maxValidators
        return tmClient.getLatestValidatorSet(
            getLatestValidatorSetRequest { pagination = getPagination(0, limit) }
        )
    }

    fun getValidatorsAtHeight(height: Int): Query.GetValidatorSetByHeightResponse {
        val limit = getStakingParams().params.maxValidators
        return tmClient.getValidatorSetByHeight(
            getValidatorSetByHeightRequest {
                this.height = height.toLong()
                pagination = getPagination(0, limit)
            }
        )
    }

    fun getStakingValidators(): MutableList<Staking.Validator> {
        var offset = 0
        val limit = 100

        val (total, validators) =
            stakingClient.validators(queryValidatorsRequest { pagination = getPagination(offset, limit) })
                .let { (it.pagination?.total ?: it.validatorsCount.toLong()) to it.validatorsList.toMutableList() }

        while (validators.count() < total) {
            offset += limit
            stakingClient.validators(queryValidatorsRequest { pagination = getPagination(offset, limit) })
                .let { validators.addAll(it.validatorsList) }
        }
        return validators
    }

    fun getStakingValidator(address: String, height: Int? = null): Staking.Validator {
        val client = stakingClient
        if (height != null) {
            client.addBlockHeightToQuery(height)
        }
        return client.validator(queryValidatorRequest { validatorAddr = address }).validator
    }

    fun getStakingValidatorOrNull(address: String, height: Int? = null): Staking.Validator? =
        try {
            val client = stakingClient
            if (height != null) {
                client.addBlockHeightToQuery(height)
            }
            client.validator(queryValidatorRequest { validatorAddr = address }).validator
        } catch (e: Exception) {
            null
        }

    fun getStakingValidatorDelegations(address: String, offset: Int, limit: Int) =
        stakingClient.validatorDelegations(
            queryValidatorDelegationsRequest {
                validatorAddr = address
                pagination = getPagination(offset, limit)
            }
        )

    fun getValidatorSelfDelegations(valAddress: String, delAddress: String) =
        stakingClient.delegation(
            queryDelegationRequest {
                validatorAddr = valAddress
                delegatorAddr = delAddress
            }
        )

    fun getStakingValidatorUnbondingDels(address: String, offset: Int, limit: Int) =
        stakingClient.validatorUnbondingDelegations(
            queryValidatorUnbondingDelegationsRequest {
                validatorAddr = address
                pagination = getPagination(offset, limit)
            }
        )

    fun getValidatorCommission(address: String) =
        distClient.validatorCommission(queryValidatorCommissionRequest { validatorAddress = address }).commission

    fun getValidatorRewards(address: String) =
        distClient.validatorOutstandingRewards(
            queryValidatorOutstandingRewardsRequest {
                validatorAddress = address
            }
        ).rewards

    fun getDelegatorWithdrawalAddress(delegator: String) =
        distClient.delegatorWithdrawAddress(
            queryDelegatorWithdrawAddressRequest {
                delegatorAddress = delegator
            }
        ).withdrawAddress

    fun getSigningInfos(): MutableList<Slashing.ValidatorSigningInfo> {
        var offset = 0
        val limit = 100

        val (total, infos) =
            slashingClient.signingInfos(querySigningInfosRequest { pagination = getPagination(offset, limit) })
                .let { (it.pagination?.total ?: it.infoCount.toLong()) to it.infoList.toMutableList() }

        while (infos.count() < total) {
            offset += limit
            slashingClient.signingInfos(querySigningInfosRequest { pagination = getPagination(offset, limit) })
                .let { infos.addAll(it.infoList) }
        }
        return infos
    }

    fun getSlashingParams() = slashingClient.params(queryParamsRequest { })

    fun getDistParams() = distClient.params(cosmos.distribution.v1beta1.queryParamsRequest { })

    fun getStakingParams() = stakingClient.params(cosmos.staking.v1beta1.queryParamsRequest { })
}
