package io.provenance.explorer.grpc.extensions

import com.google.protobuf.Any
import cosmos.auth.v1beta1.Auth
import cosmos.auth.v1beta1.Auth.ModuleAccount
import cosmos.base.query.v1beta1.pageRequest
import cosmos.distribution.v1beta1.Distribution
import cosmos.gov.v1.Gov
import cosmos.mint.v1beta1.Mint
import cosmos.slashing.v1beta1.Slashing
import cosmos.vesting.v1beta1.Vesting
import ibc.applications.interchain_accounts.v1.Account.InterchainAccount
import io.grpc.Metadata
import io.grpc.stub.AbstractStub
import io.grpc.stub.MetadataUtils
import io.provenance.explorer.config.ExplorerProperties.Companion.PROV_ACC_PREFIX
import io.provenance.explorer.config.ExplorerProperties.Companion.PROV_VAL_OPER_PREFIX
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.extensions.diff
import io.provenance.explorer.domain.extensions.toCoinStrList
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toPercentage
import io.provenance.explorer.domain.extensions.toPercentageOld
import io.provenance.explorer.domain.models.explorer.toCoinStr
import io.provenance.explorer.model.AccountVestingInfo
import io.provenance.explorer.model.DistParams
import io.provenance.explorer.model.MintParams
import io.provenance.explorer.model.MsgBasedFee
import io.provenance.explorer.model.PeriodicVestingInfo
import io.provenance.explorer.model.SlashingParams
import io.provenance.explorer.model.TallyingParams
import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.PeriodInSeconds
import io.provenance.explorer.service.prettyRole
import io.provenance.marker.v1.Access
import io.provenance.marker.v1.MarkerAccount
import io.provenance.marker.v1.MarkerStatus
import io.provenance.msgfees.v1.MsgFee
import org.joda.time.DateTime

//region GRPC query

const val BLOCK_HEIGHT = "x-cosmos-block-height"

fun <S : AbstractStub<S>> S.addBlockHeightToQuery(blockHeight: String): S =
    Metadata()
        .also { it.put(Metadata.Key.of(BLOCK_HEIGHT, Metadata.ASCII_STRING_MARSHALLER), blockHeight) }
        .let { MetadataUtils.attachHeaders(this, it) }

fun getPagination(offset: Int, limit: Int) =
    pageRequest {
        this.offset = offset.toLong()
        this.limit = limit.toLong()
        this.countTotal = true
    }

//endregion

//region Marker Extensions
fun String.getTypeShortName() = this.split(".").last()

fun Any.toMarker(): MarkerAccount =
    when (this.typeUrl.getTypeShortName()) {
        MarkerAccount::class.java.simpleName -> this.unpack(MarkerAccount::class.java)
        else -> {
            throw ResourceNotFoundException("This marker type has not been mapped yet")
        }
    }

fun MarkerAccount.isMintable() = this.accessControlList.any { it.permissionsList.contains(Access.ACCESS_MINT) }

fun Array<Access>.filterRoles() = this.filter { it.roleFilter() }
fun List<Access>.filterRoles() = this.filter { it.roleFilter() }
fun Access.roleFilter() = this != Access.UNRECOGNIZED && this != Access.ACCESS_UNSPECIFIED

fun MarkerAccount.getManagingAccounts(): MutableMap<String, List<String>> {
    val managers = this.accessControlList.associate { addr ->
        addr.address to addr.permissionsList.filterRoles().map { it.name.prettyRole() }
    }.toMutableMap()

    return when {
        this.status.number >= MarkerStatus.MARKER_STATUS_ACTIVE.number -> managers
        else -> {
            if (this.manager.isNotBlank()) {
                managers[this.manager] = Access.values().filterRoles().map { it.name.prettyRole() }
            }
            managers
        }
    }
}

//endregion

//region Account Extensions
fun Any.toModuleAccount() = this.unpack(ModuleAccount::class.java)
fun Any.toBaseAccount() = this.unpack(Auth.BaseAccount::class.java)
fun Any.toMarkerAccount() = this.unpack(MarkerAccount::class.java)
fun Any.toContinuousVestingAccount() = this.unpack(Vesting.ContinuousVestingAccount::class.java)
fun Any.toDelayedVestingAccount() = this.unpack(Vesting.DelayedVestingAccount::class.java)
fun Any.toPeriodicVestingAccount() = this.unpack(Vesting.PeriodicVestingAccount::class.java)
fun Any.toPermanentLockedAccount() = this.unpack(Vesting.PermanentLockedAccount::class.java)
fun Any.toInterchainAccount() = this.unpack(InterchainAccount::class.java)

fun Any.getModuleAccName() =
    if (this.typeUrl.getTypeShortName() == Auth.ModuleAccount::class.java.simpleName) {
        this.toModuleAccount().name
    } else {
        null
    }

fun String.isStandardAddress() =
    this.startsWith(PROV_ACC_PREFIX) && !this.startsWith(PROV_VAL_OPER_PREFIX)
fun String.isValidatorAddress() = this.startsWith(PROV_VAL_OPER_PREFIX)

// Ref https://docs.cosmos.network/master/modules/auth/05_vesting.html#continuousvestingaccount
// for info on how the periods are calced
fun Any.toVestingData(initialDate: DateTime?, continuousPeriod: PeriodInSeconds): AccountVestingInfo {
    val now = DateTime.now().millis / 1000
    when (this.typeUrl.getTypeShortName()) {
        Vesting.ContinuousVestingAccount::class.java.simpleName ->
            // Given the PeriodInSeconds, chunk the totalTime and calculate how much coin will be vested at the end
            // of the given period. Continuous technically vests every second, but that can be cumbersome to fetch.
            this.toContinuousVestingAccount().let { acc ->
                val totalTime = acc.baseVestingAccount.endTime - acc.startTime
                var runningTime = acc.startTime // returns the actual vestingTime
                var prevCoins = emptyList<CoinStr>() // reset for every period with the latest percentage amounts
                val periods = (acc.startTime until acc.baseVestingAccount.endTime)
                    .chunked(continuousPeriod.seconds)
                    .mapNotNull { list ->
                        if (list.last() == acc.startTime) return@mapNotNull null
                        runningTime += list.size // Updated to show the actual vestingTime for the period
                        val elapsedTime = runningTime - acc.startTime // elapsed time up to the vestingTime
                        // Calcs the total percentage up to the current vestingTime of vested coins
                        val newCoin = acc.baseVestingAccount.originalVestingList.map { it.toPercentage(elapsedTime, totalTime) }

                        PeriodicVestingInfo(
                            list.size.toLong(), // How long the period is in seconds
                            prevCoins.diff(newCoin), // diffs the old percentages with the new percentages to get the period's values
                            runningTime.toDateTime(), // vestingTime for the period
                            runningTime <= now // compared to NOW, is it vested
                        ).also { prevCoins = newCoin }
                    }
                return AccountVestingInfo(
                    now.toDateTime(),
                    acc.baseVestingAccount.endTime.toDateTime(),
                    acc.baseVestingAccount.originalVestingList.toCoinStrList(),
                    acc.startTime.toDateTime(),
                    periods
                )
            }
        Vesting.DelayedVestingAccount::class.java.simpleName ->
            // Delayed is a one and done period, timed to the endTime
            this.toDelayedVestingAccount().let {
                val period = PeriodicVestingInfo(
                    0L,
                    it.baseVestingAccount.originalVestingList.toCoinStrList(),
                    it.baseVestingAccount.endTime.toDateTime(),
                    it.baseVestingAccount.endTime < now
                )
                return AccountVestingInfo(
                    now.toDateTime(),
                    it.baseVestingAccount.endTime.toDateTime(),
                    it.baseVestingAccount.originalVestingList.toCoinStrList(),
                    initialDate ?: now.toDateTime(),
                    listOf(period)
                )
            }
        Vesting.PeriodicVestingAccount::class.java.simpleName ->
            // Periodic repackages the given Period objects
            this.toPeriodicVestingAccount().let { acc ->
                var runningTime = acc.startTime
                val periods = acc.vestingPeriodsList.map {
                    runningTime += it.length
                    PeriodicVestingInfo(
                        it.length,
                        it.amountList.toCoinStrList(),
                        runningTime.toDateTime(),
                        runningTime < now
                    )
                }

                return AccountVestingInfo(
                    now.toDateTime(),
                    acc.baseVestingAccount.endTime.toDateTime(),
                    acc.baseVestingAccount.originalVestingList.toCoinStrList(),
                    acc.startTime.toDateTime(),
                    periods
                )
            }
        Vesting.PermanentLockedAccount::class.java.simpleName ->
            this.toPermanentLockedAccount().let {
                return AccountVestingInfo(
                    now.toDateTime(),
                    it.baseVestingAccount.endTime.toDateTime(),
                    it.baseVestingAccount.originalVestingList.toCoinStrList(),
                    initialDate ?: now.toDateTime()
                )
            }

        else -> throw ResourceNotFoundException("This Vesting Account type has not been mapped: ${this.typeUrl.getTypeShortName()}")
    }
}

fun Any.isVesting() =
    this.typeUrl.getTypeShortName() == Vesting.ContinuousVestingAccount::class.java.simpleName ||
        this.typeUrl.getTypeShortName() == Vesting.DelayedVestingAccount::class.java.simpleName ||
        this.typeUrl.getTypeShortName() == Vesting.PeriodicVestingAccount::class.java.simpleName ||
        this.typeUrl.getTypeShortName() == Vesting.PermanentLockedAccount::class.java.simpleName

fun Any.isIca() = this.typeUrl.getTypeShortName() == InterchainAccount::class.java.simpleName

//endregion

//region To DTOs

fun Distribution.Params.toDto() = DistParams(
    this.communityTax.toPercentageOld(),
    this.baseProposerReward.toPercentageOld(),
    this.bonusProposerReward.toPercentageOld(),
    this.withdrawAddrEnabled
)

fun Gov.TallyParams.toDto() = TallyingParams(
    this.quorum.toPercentage(),
    this.threshold.toPercentage(),
    this.vetoThreshold.toPercentage()
)

fun Mint.Params.toDto() = MintParams(
    this.mintDenom,
    this.inflationRateChange.toPercentageOld(),
    this.inflationMax.toPercentageOld(),
    this.inflationMin.toPercentageOld(),
    this.goalBonded.toPercentageOld(),
    this.blocksPerYear
)

fun Slashing.Params.toDto() = SlashingParams(
    this.signedBlocksWindow,
    this.minSignedPerWindow.toString(Charsets.UTF_8).toPercentageOld(),
    "${this.downtimeJailDuration.seconds}s",
    this.slashFractionDoubleSign.toString(Charsets.UTF_8).toPercentageOld(),
    this.slashFractionDowntime.toString(Charsets.UTF_8).toPercentageOld()
)

fun MsgFee.toDto() = MsgBasedFee(this.msgTypeUrl, this.additionalFee.toCoinStr())

//endregion
