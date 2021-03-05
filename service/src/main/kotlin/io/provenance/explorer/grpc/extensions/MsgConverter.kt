package io.provenance.explorer.grpc.extensions

import com.google.protobuf.Any
import cosmos.bank.v1beta1.Tx
import io.provenance.explorer.domain.core.logger
import io.provenance.marker.v1.MsgWithdrawRequest

/**
 * Ginormous file meant to convert a Msg object to the proper format, and do stuff with it.
 */

fun Any.toMsgSend() = this.unpack(Tx.MsgSend::class.java)
fun Any.toMsgMultiSend() = this.unpack(Tx.MsgMultiSend::class.java)
fun Any.toMsgSetWithdrawAddress() = this.unpack(cosmos.distribution.v1beta1.Tx.MsgSetWithdrawAddress::class.java)
fun Any.toMsgWithdrawDelegatorReward() = this.unpack(cosmos.distribution.v1beta1.Tx.MsgWithdrawDelegatorReward::class.java)
fun Any.toMsgWithdrawValidatorCommission() = this.unpack(cosmos.distribution.v1beta1.Tx.MsgWithdrawValidatorCommission::class.java)
fun Any.toMsgFundCommunityPool() = this.unpack(cosmos.distribution.v1beta1.Tx.MsgFundCommunityPool::class.java)
fun Any.toMsgSubmitEvidence() = this.unpack(cosmos.evidence.v1beta1.Tx.MsgSubmitEvidence::class.java)
fun Any.toMsgGrantFeeAllowance() = this.unpack(cosmos.feegrant.v1beta1.Tx.MsgGrantFeeAllowance::class.java)
fun Any.toMsgRevokeFeeAllowance() = this.unpack(cosmos.feegrant.v1beta1.Tx.MsgRevokeFeeAllowance::class.java)
fun Any.toMsgSubmitProposal() = this.unpack(cosmos.gov.v1beta1.Tx.MsgSubmitProposal::class.java)
fun Any.toMsgVote() = this.unpack(cosmos.gov.v1beta1.Tx.MsgVote::class.java)
fun Any.toMsgVoteWeighted() = this.unpack(cosmos.gov.v1beta1.Tx.MsgVoteWeighted::class.java)
fun Any.toMsgDeposit() = this.unpack(cosmos.gov.v1beta1.Tx.MsgDeposit::class.java)
fun Any.toMsgUnjail() = this.unpack(cosmos.slashing.v1beta1.Tx.MsgUnjail::class.java)
fun Any.toMsgCreateValidator() = this.unpack(cosmos.staking.v1beta1.Tx.MsgCreateValidator::class.java)
fun Any.toMsgEditValidator() = this.unpack(cosmos.staking.v1beta1.Tx.MsgEditValidator::class.java)
fun Any.toMsgDelegate() = this.unpack(cosmos.staking.v1beta1.Tx.MsgDelegate::class.java)
fun Any.toMsgBeginRedelegate() = this.unpack(cosmos.staking.v1beta1.Tx.MsgBeginRedelegate::class.java)
fun Any.toMsgUndelegate() = this.unpack(cosmos.staking.v1beta1.Tx.MsgUndelegate::class.java)
fun Any.toMsgCreateVestingAccount() = this.unpack(cosmos.vesting.v1beta1.Tx.MsgCreateVestingAccount::class.java)
fun Any.toMsgWithdrawRequest() = this.unpack(MsgWithdrawRequest::class.java)


fun Any.getAssociatedAddresses(): List<String> =
    when {
        typeUrl.contains("MsgSend") -> this.toMsgSend().let { listOf(it.fromAddress, it.toAddress) }
        typeUrl.contains("MsgMultiSend") -> this.toMsgMultiSend()
            .let {
                val ins = it.inputsList.map { inp -> inp.address }.toMutableList()
                val outs = it.outputsList.map { out -> out.address }.toMutableList()
                ins.addAll(outs)
                ins
            }
        typeUrl.contains("MsgSetWithdrawAddress") -> this.toMsgSetWithdrawAddress()
            .let { listOf(it.delegatorAddress, it.withdrawAddress) }
        typeUrl.contains("MsgWithdrawDelegatorReward") -> this.toMsgWithdrawDelegatorReward()
            .let { listOf(it.delegatorAddress, it.validatorAddress) }
        typeUrl.contains("MsgWithdrawValidatorCommission") -> this.toMsgWithdrawValidatorCommission()
            .let { listOf(it.validatorAddress) }
        typeUrl.contains("MsgFundCommunityPool") -> this.toMsgFundCommunityPool().let { listOf(it.depositor) }
        typeUrl.contains("MsgSubmitEvidence") -> this.toMsgSubmitEvidence().let { listOf(it.submitter) }
        typeUrl.contains("MsgGrantFeeAllowance") -> this.toMsgGrantFeeAllowance().let { listOf(it.grantee, it.granter) }
        typeUrl.contains("MsgRevokeFeeAllowance") -> this.toMsgRevokeFeeAllowance().let { listOf(it.grantee, it.granter) }
        typeUrl.contains("MsgSubmitProposal") -> this.toMsgSubmitProposal().let { listOf(it.proposer) }
        typeUrl.contains("MsgVote") -> this.toMsgVote().let { listOf(it.voter) }
        typeUrl.contains("MsgVoteWeighted") -> this.toMsgVoteWeighted().let { listOf(it.voter) }
        typeUrl.contains("MsgDeposit") -> this.toMsgDeposit().let { listOf(it.depositor) }
        typeUrl.contains("MsgUnjail") -> this.toMsgUnjail().let { listOf(it.validatorAddr) }
        typeUrl.contains("MsgCreateValidator") -> this.toMsgCreateValidator()
            .let { listOf(it.validatorAddress, it.delegatorAddress) }
        typeUrl.contains("MsgEditValidator") -> this.toMsgEditValidator().let { listOf(it.validatorAddress) }
        typeUrl.contains("MsgDelegate") -> this.toMsgDelegate().let { listOf(it.validatorAddress, it.delegatorAddress) }
        typeUrl.contains("MsgBeginRedelegate") -> this.toMsgBeginRedelegate()
            .let { listOf(it.delegatorAddress, it.validatorDstAddress, it.validatorSrcAddress) }
        typeUrl.contains("MsgUndelegate") -> this.toMsgUndelegate().let { listOf(it.delegatorAddress, it.validatorAddress) }
        typeUrl.contains("MsgCreateVestingAccount") -> this.toMsgCreateVestingAccount().let { listOf(it.fromAddress, it.toAddress) }
        typeUrl.contains("MsgWithdrawRequest") -> this.toMsgWithdrawRequest().let { listOf(it.toAddress, it.administrator)}

        else -> listOf<String>().also { logger().error("This typeUrl is not yet supported in tx messages: $typeUrl") }
    }

fun Any.toTxSummaryMsg(): List<String> =
    when {
        typeUrl.contains("MsgSend") -> this.toMsgSend().let { listOf(it.fromAddress, it.toAddress) }
        typeUrl.contains("MsgMultiSend") -> this.toMsgMultiSend()
            .let {
                val ins = it.inputsList.map { inp -> inp.address }.toMutableList()
                val outs = it.outputsList.map { out -> out.address }.toMutableList()
                ins.addAll(outs)
                ins
            }
        typeUrl.contains("MsgSetWithdrawAddress") -> this.toMsgSetWithdrawAddress()
            .let { listOf(it.delegatorAddress, it.withdrawAddress) }
        typeUrl.contains("MsgWithdrawDelegatorReward") -> this.toMsgWithdrawDelegatorReward()
            .let { listOf(it.delegatorAddress, it.validatorAddress) }
        typeUrl.contains("MsgWithdrawValidatorCommission") -> this.toMsgWithdrawValidatorCommission()
            .let { listOf(it.validatorAddress) }
        typeUrl.contains("MsgFundCommunityPool") -> this.toMsgFundCommunityPool().let { listOf(it.depositor) }
        typeUrl.contains("MsgSubmitEvidence") -> this.toMsgSubmitEvidence().let { listOf(it.submitter) }
        typeUrl.contains("MsgGrantFeeAllowance") -> this.toMsgGrantFeeAllowance().let { listOf(it.grantee, it.granter) }
        typeUrl.contains("MsgRevokeFeeAllowance") -> this.toMsgRevokeFeeAllowance().let { listOf(it.grantee, it.granter) }
        typeUrl.contains("MsgSubmitProposal") -> this.toMsgSubmitProposal().let { listOf(it.proposer) }
        typeUrl.contains("MsgVote") -> this.toMsgVote().let { listOf(it.voter) }
        typeUrl.contains("MsgVoteWeighted") -> this.toMsgVoteWeighted().let { listOf(it.voter) }
        typeUrl.contains("MsgDeposit") -> this.toMsgDeposit().let { listOf(it.depositor) }
        typeUrl.contains("MsgUnjail") -> this.toMsgUnjail().let { listOf(it.validatorAddr) }
        typeUrl.contains("MsgCreateValidator") -> this.toMsgCreateValidator()
            .let { listOf(it.validatorAddress, it.delegatorAddress) }
        typeUrl.contains("MsgEditValidator") -> this.toMsgEditValidator().let { listOf(it.validatorAddress) }
        typeUrl.contains("MsgDelegate") -> this.toMsgDelegate().let { listOf(it.validatorAddress, it.delegatorAddress) }
        typeUrl.contains("MsgBeginRedelegate") -> this.toMsgBeginRedelegate()
            .let { listOf(it.delegatorAddress, it.validatorDstAddress, it.validatorSrcAddress) }
        typeUrl.contains("MsgUndelegate") -> this.toMsgUndelegate().let { listOf(it.delegatorAddress, it.validatorAddress) }
        typeUrl.contains("MsgCreateVestingAccount") -> this.toMsgCreateVestingAccount().let { listOf(it.fromAddress, it.toAddress) }

        else -> listOf<String>().also { logger().error("This typeUrl is not yet supported in tx messages: $typeUrl") }
    }
