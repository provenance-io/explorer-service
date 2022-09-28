package io.provenance.explorer.service

import com.google.protobuf.Any
import cosmos.feegrant.v1beta1.Feegrant
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.extensions.getShortType
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toCoinStrList
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toNormalCase
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.Allowance
import io.provenance.explorer.domain.models.explorer.AllowedMsgAllowance
import io.provenance.explorer.domain.models.explorer.AuthzGrant
import io.provenance.explorer.domain.models.explorer.BasicAllowance
import io.provenance.explorer.domain.models.explorer.CountAuth
import io.provenance.explorer.domain.models.explorer.FeegrantData
import io.provenance.explorer.domain.models.explorer.GenericAuth
import io.provenance.explorer.domain.models.explorer.GrantData
import io.provenance.explorer.domain.models.explorer.MarkerTransferAuth
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.PeriodicAllowance
import io.provenance.explorer.domain.models.explorer.SendAuth
import io.provenance.explorer.domain.models.explorer.StakeAuth
import io.provenance.explorer.domain.models.explorer.toCoinStr
import io.provenance.explorer.domain.models.explorer.toDto
import io.provenance.explorer.grpc.v1.AuthzGrpcClient
import io.provenance.explorer.grpc.v1.FeegrantGrpcClient
import io.provenance.marker.v1.MarkerTransferAuthorization
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import cosmos.authz.v1beta1.Authz as AuthzAuthz
import cosmos.bank.v1beta1.Authz as BankAuthz
import cosmos.staking.v1beta1.Authz as StakingAuthz

@Service
class GrantService(
    private val authzClient: AuthzGrpcClient,
    private val feegrantClient: FeegrantGrpcClient
) {
    protected val logger = logger(GrantService::class)

    // Authz grants
    fun getGrantsForGranterPaginated(granter: String, page: Int, limit: Int) = runBlocking {
        authzClient.getGrantsForGranter(granter, page.toOffset(limit), limit).let { res ->
            val list = res.grantsList.map {
                GrantData(
                    it.granter,
                    it.grantee,
                    it.expiration.toDateTime(),
                    it.authorization.getShortType().toNormalCase(),
                    it.authorization.toAuthzGrant()
                )
            }
            PagedResults(res.pagination.total.pageCountOfResults(limit), list, res.pagination.total)
        }
    }

    // Authz grants
    fun getGrantsForGranteePaginated(grantee: String, page: Int, limit: Int) = runBlocking {
        authzClient.getGrantsForGrantee(grantee, page.toOffset(limit), limit).let { res ->
            val list = res.grantsList.map {
                GrantData(
                    it.granter,
                    it.grantee,
                    it.expiration.toDateTime(),
                    it.authorization.getShortType().toNormalCase(),
                    it.authorization.toAuthzGrant()
                )
            }
            PagedResults(res.pagination.total.pageCountOfResults(limit), list, res.pagination.total)
        }
    }

    // Feegrant grants
    fun getAllowancesForGranteePaginated(grantee: String, page: Int, limit: Int) = runBlocking {
        feegrantClient.getAllowancesForGrantee(grantee, page.toOffset(limit), limit).let { res ->
            val list = res.allowancesList.map {
                FeegrantData(
                    it.granter,
                    it.grantee,
                    it.allowance.getShortType().toNormalCase(),
                    it.allowance.toFeegrantAllowance()
                )
            }
            PagedResults(res.pagination.total.pageCountOfResults(limit), list, res.pagination.total)
        }
    }

    // Feegrant grants
    fun getAllowancesByGranterPaginated(granter: String, page: Int, limit: Int): Nothing =
        feegrantClient.getGrantsByGranter(granter, page.toOffset(limit), limit)
}

fun Any.toFeegrantAllowance(): Allowance? =
    when {
        this.typeUrl.endsWith("v1beta1.BasicAllowance") ->
            this.unpack(Feegrant.BasicAllowance::class.java)
                .let { BasicAllowance(it.spendLimitList.toCoinStrList(), it.expiration.toDateTime()) }
        this.typeUrl.endsWith("v1beta1.PeriodicAllowance") ->
            this.unpack(Feegrant.PeriodicAllowance::class.java)
                .let {
                    PeriodicAllowance(
                        it.basic.toDto(),
                        "${it.period.seconds}s",
                        it.periodSpendLimitList.toCoinStrList(),
                        it.periodCanSpendList.toCoinStrList(),
                        it.periodReset.toDateTime()
                    )
                }
        this.typeUrl.endsWith("v1beta1.AllowedMsgAllowance") ->
            this.unpack(Feegrant.AllowedMsgAllowance::class.java)
                .let { AllowedMsgAllowance(it.allowance.toFeegrantAllowance()!!, it.allowedMessagesList) }
        else -> null.also { logger().error("Invalid feegrant type: ${this.typeUrl}") }
    }

fun Any.toAuthzGrant(): AuthzGrant? =
    when {
        this.typeUrl.endsWith("v1beta1.GenericAuthorization") ->
            this.unpack(AuthzAuthz.GenericAuthorization::class.java).let { GenericAuth(it.msg) }
        this.typeUrl.endsWith("v1beta1.CountAuthorization") ->
            this.unpack(AuthzAuthz.CountAuthorization::class.java).let { CountAuth(it.msg, it.allowedAuthorizations) }
        this.typeUrl.endsWith("v1beta1.SendAuthorization") ->
            this.unpack(BankAuthz.SendAuthorization::class.java).let { SendAuth(it.spendLimitList.toCoinStrList()) }
        this.typeUrl.endsWith("v1beta1.StakeAuthorization") ->
            this.unpack(StakingAuthz.StakeAuthorization::class.java)
                .let {
                    StakeAuth(
                        it.authorizationType.name.toNormalCase(),
                        if (it.hasAllowList()) it.allowList.addressList else null,
                        if (it.hasDenyList()) it.denyList.addressList else null,
                        if (it.hasMaxTokens()) it.maxTokens.toCoinStr() else null
                    )
                }
        this.typeUrl.endsWith("v1.MarkerTransferAuthorization") ->
            this.unpack(MarkerTransferAuthorization::class.java)
                .let { MarkerTransferAuth(it.transferLimitList.toCoinStrList()) }
        else -> null.also { logger().error("Invalid authz grant type: ${this.typeUrl}") }
    }
