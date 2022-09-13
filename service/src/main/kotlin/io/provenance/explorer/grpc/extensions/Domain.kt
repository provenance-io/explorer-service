package io.provenance.explorer.grpc.extensions

import com.google.protobuf.Any
import cosmos.auth.v1beta1.Auth
import cosmos.base.query.v1beta1.Pagination
import cosmos.base.query.v1beta1.pageRequest
import cosmos.distribution.v1beta1.Distribution
import cosmos.gov.v1beta1.Gov
import cosmos.gov.v1beta1.Tx
import cosmos.mint.v1beta1.Mint
import cosmos.slashing.v1beta1.Slashing
import cosmos.vesting.v1beta1.Vesting
import io.grpc.Metadata
import io.grpc.stub.AbstractStub
import io.grpc.stub.MetadataUtils
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.extensions.diff
import io.provenance.explorer.domain.extensions.edPubKeyToBech32
import io.provenance.explorer.domain.extensions.secp256k1PubKeyToBech32
import io.provenance.explorer.domain.extensions.secp256r1PubKeyToBech32
import io.provenance.explorer.domain.extensions.toCoinStrList
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toPercentage
import io.provenance.explorer.domain.models.explorer.AccountVestingInfo
import io.provenance.explorer.domain.models.explorer.CoinStr
import io.provenance.explorer.domain.models.explorer.DistParams
import io.provenance.explorer.domain.models.explorer.MintParams
import io.provenance.explorer.domain.models.explorer.MsgBasedFee
import io.provenance.explorer.domain.models.explorer.PeriodInSeconds
import io.provenance.explorer.domain.models.explorer.PeriodicVestingInfo
import io.provenance.explorer.domain.models.explorer.SlashingParams
import io.provenance.explorer.domain.models.explorer.TallyingParams
import io.provenance.explorer.domain.models.explorer.toData
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

fun getPaginationBuilder(offset: Int, limit: Int) =
    Pagination.PageRequest.newBuilder().setOffset(offset.toLong()).setLimit(limit.toLong()).setCountTotal(true)

fun getPaginationBuilderNoCount(offset: Int, limit: Int) =
    Pagination.PageRequest.newBuilder().setOffset(offset.toLong()).setLimit(limit.toLong())

fun getPagination(offset: Int, limit: Int) =
    pageRequest {
        this.offset = offset.toLong()
        this.limit = limit.toLong()
        this.countTotal = true
    }

fun getPaginationNoCount(offset: Int, limit: Int) =
    pageRequest {
        this.offset = offset.toLong()
        this.limit = limit.toLong()
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
            if (this.manager.isNotBlank())
                managers[this.manager] = Access.values().filterRoles().map { it.name.prettyRole() }
            managers
        }
    }
}

//endregion

//region Account Extensions
fun Any.getModuleAccName() =
    if (this.typeUrl.getTypeShortName() == Auth.ModuleAccount::class.java.simpleName)
        this.unpack(Auth.ModuleAccount::class.java).name
    else null

fun Any.toAddress(hrpPrefix: String) =
    when {
        typeUrl.contains("secp256k1") ->
            this.unpack(cosmos.crypto.secp256k1.Keys.PubKey::class.java).key.secp256k1PubKeyToBech32(hrpPrefix)
        typeUrl.contains("secp256r1") ->
            this.unpack(cosmos.crypto.secp256r1.Keys.PubKey::class.java).key.secp256r1PubKeyToBech32(
                hrpPrefix,
                typeUrl.removeFirstSlash()
            )
        typeUrl.contains("ed25519") ->
            this.unpack(cosmos.crypto.ed25519.Keys.PubKey::class.java).key.edPubKeyToBech32(hrpPrefix)
        else -> null.also { logger().error("This typeUrl is not supported as a consensus address: $typeUrl") }
    }

fun String.isStandardAddress(props: ExplorerProperties) =
    this.startsWith(props.provAccPrefix()) && !this.startsWith(props.provValOperPrefix())
fun String.isValidatorAddress(props: ExplorerProperties) = this.startsWith(props.provValOperPrefix())

// Ref https://docs.cosmos.network/master/modules/auth/05_vesting.html#continuousvestingaccount
// for info on how the periods are calced
fun Any.toVestingData(initialDate: DateTime?, continuousPeriod: PeriodInSeconds): AccountVestingInfo {
    val now = DateTime.now().millis / 1000
    when (this.typeUrl.getTypeShortName()) {
        Vesting.ContinuousVestingAccount::class.java.simpleName ->
            // Given the PeriodInSeconds, chunk the totalTime and calculate how much coin will be vested at the end
            // of the given period. Continuous technically vests every second, but that can be cumbersome to fetch.
            this.unpack(Vesting.ContinuousVestingAccount::class.java).let { acc ->
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
            this.unpack(Vesting.DelayedVestingAccount::class.java).let {
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
            this.unpack(Vesting.PeriodicVestingAccount::class.java).let { acc ->
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
            this.unpack(Vesting.PermanentLockedAccount::class.java).let {
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

//endregion

//region To DTOs

fun Distribution.Params.toDto() = DistParams(
    this.communityTax.toPercentage(),
    this.baseProposerReward.toPercentage(),
    this.bonusProposerReward.toPercentage(),
    this.withdrawAddrEnabled,
)

fun Gov.TallyParams.toDto() = TallyingParams(
    this.quorum.toString(Charsets.UTF_8).toPercentage(),
    this.threshold.toString(Charsets.UTF_8).toPercentage(),
    this.vetoThreshold.toString(Charsets.UTF_8).toPercentage(),
)

fun Mint.Params.toDto() = MintParams(
    this.mintDenom,
    this.inflationRateChange.toPercentage(),
    this.inflationMax.toPercentage(),
    this.inflationMin.toPercentage(),
    this.goalBonded.toPercentage(),
    this.blocksPerYear,
)

fun Slashing.Params.toDto() = SlashingParams(
    this.signedBlocksWindow,
    this.minSignedPerWindow.toString(Charsets.UTF_8).toPercentage(),
    "${this.downtimeJailDuration.seconds}s",
    this.slashFractionDoubleSign.toString(Charsets.UTF_8).toPercentage(),
    this.slashFractionDowntime.toString(Charsets.UTF_8).toPercentage(),
)

fun MsgFee.toDto() = MsgBasedFee(this.msgTypeUrl, this.additionalFee.toData())

fun Tx.MsgVote.toWeightedVote() =
    Gov.WeightedVoteOption.newBuilder().setOption(this.option).setWeight("1000000000000000000").build()

//endregion
