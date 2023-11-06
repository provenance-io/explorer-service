package io.provenance.explorer.grpc.extensions

import com.google.protobuf.Any
import cosmos.authz.v1beta1.Authz.CountAuthorization
import cosmos.authz.v1beta1.Authz.GenericAuthorization
import cosmos.bank.v1beta1.Tx
import cosmos.bank.v1beta1.msgSend
import cosmos.gov.v1.Tx.MsgSubmitProposal
import cosmos.gov.v1.Tx.MsgVoteWeighted
import cosmos.group.v1.Types.ProposalExecutorResult
import cosmos.group.v1.Types.ProposalStatus
import cosmos.nft.v1beta1.Tx.MsgSend
import cosmos.staking.v1beta1.Authz.AuthorizationType
import cosmos.staking.v1beta1.Tx.MsgCancelUnbondingDelegation
import cosmos.staking.v1beta1.msgBeginRedelegate
import cosmos.staking.v1beta1.msgDelegate
import cosmos.staking.v1beta1.msgUndelegate
import cosmos.upgrade.v1beta1.Tx.MsgCancelUpgrade
import cosmos.upgrade.v1beta1.Tx.MsgSoftwareUpgrade
import cosmos.upgrade.v1beta1.Upgrade.SoftwareUpgradeProposal
import cosmwasm.wasm.v1.msgExecuteContract
import cosmwasm.wasm.v1.msgInstantiateContract
import cosmwasm.wasm.v1.msgMigrateContract
import ibc.applications.fee.v1.Tx.MsgPayPacketFee
import ibc.applications.fee.v1.Tx.MsgPayPacketFeeAsync
import ibc.applications.fee.v1.Tx.MsgRegisterCounterpartyPayee
import ibc.applications.fee.v1.Tx.MsgRegisterPayee
import ibc.applications.transfer.v1.Tx.MsgTransfer
import ibc.core.channel.v1.Tx.MsgAcknowledgement
import ibc.core.channel.v1.Tx.MsgChannelCloseConfirm
import ibc.core.channel.v1.Tx.MsgChannelCloseInit
import ibc.core.channel.v1.Tx.MsgChannelOpenAck
import ibc.core.channel.v1.Tx.MsgChannelOpenConfirm
import ibc.core.channel.v1.Tx.MsgChannelOpenInit
import ibc.core.channel.v1.Tx.MsgChannelOpenTry
import ibc.core.channel.v1.Tx.MsgRecvPacket
import ibc.core.channel.v1.Tx.MsgTimeout
import ibc.core.channel.v1.Tx.MsgTimeoutOnClose
import ibc.core.client.v1.Tx.MsgCreateClient
import ibc.core.client.v1.Tx.MsgSubmitMisbehaviour
import ibc.core.client.v1.Tx.MsgUpdateClient
import ibc.core.client.v1.Tx.MsgUpgradeClient
import ibc.core.connection.v1.Tx.MsgConnectionOpenAck
import ibc.core.connection.v1.Tx.MsgConnectionOpenConfirm
import ibc.core.connection.v1.Tx.MsgConnectionOpenInit
import ibc.core.connection.v1.Tx.MsgConnectionOpenTry
import ibc.lightclients.localhost.v1.Localhost
import ibc.lightclients.solomachine.v1.Solomachine
import ibc.lightclients.tendermint.v1.Tendermint
import io.provenance.attribute.v1.MsgAddAttributeRequest
import io.provenance.attribute.v1.MsgDeleteAttributeRequest
import io.provenance.attribute.v1.MsgDeleteDistinctAttributeRequest
import io.provenance.attribute.v1.MsgUpdateAttributeRequest
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.extensions.getType
import io.provenance.explorer.domain.models.explorer.MsgProtoBreakout
import io.provenance.explorer.domain.models.explorer.TxIbcData
import io.provenance.explorer.grpc.extensions.MdEvents.AA
import io.provenance.explorer.grpc.extensions.MdEvents.AD
import io.provenance.explorer.grpc.extensions.MdEvents.ADD
import io.provenance.explorer.grpc.extensions.MdEvents.AU
import io.provenance.explorer.grpc.extensions.MdEvents.CRSC
import io.provenance.explorer.grpc.extensions.MdEvents.CRSD
import io.provenance.explorer.grpc.extensions.MdEvents.CRSU
import io.provenance.explorer.grpc.extensions.MdEvents.CSPC
import io.provenance.explorer.grpc.extensions.MdEvents.CSPD
import io.provenance.explorer.grpc.extensions.MdEvents.CSPU
import io.provenance.explorer.grpc.extensions.MdEvents.RC
import io.provenance.explorer.grpc.extensions.MdEvents.RD
import io.provenance.explorer.grpc.extensions.MdEvents.RU
import io.provenance.explorer.grpc.extensions.MdEvents.SC
import io.provenance.explorer.grpc.extensions.MdEvents.SD
import io.provenance.explorer.grpc.extensions.MdEvents.SEC
import io.provenance.explorer.grpc.extensions.MdEvents.SED
import io.provenance.explorer.grpc.extensions.MdEvents.SEU
import io.provenance.explorer.grpc.extensions.MdEvents.SSC
import io.provenance.explorer.grpc.extensions.MdEvents.SSD
import io.provenance.explorer.grpc.extensions.MdEvents.SSU
import io.provenance.explorer.grpc.extensions.MdEvents.SU
import io.provenance.explorer.model.base.MetadataAddress
import io.provenance.explorer.model.base.blankToNull
import io.provenance.explorer.model.base.isMAddress
import io.provenance.explorer.model.base.toMAddress
import io.provenance.explorer.model.base.toMAddressContractSpec
import io.provenance.explorer.model.base.toMAddressScope
import io.provenance.explorer.model.base.toMAddressScopeSpec
import io.provenance.explorer.model.base.toMAddressSession
import io.provenance.explorer.model.base.toUuidOrNull
import io.provenance.explorer.service.getDenomByAddress
import io.provenance.marker.v1.MsgActivateRequest
import io.provenance.marker.v1.MsgAddAccessRequest
import io.provenance.marker.v1.MsgAddMarkerRequest
import io.provenance.marker.v1.MsgBurnRequest
import io.provenance.marker.v1.MsgCancelRequest
import io.provenance.marker.v1.MsgDeleteAccessRequest
import io.provenance.marker.v1.MsgDeleteRequest
import io.provenance.marker.v1.MsgFinalizeRequest
import io.provenance.marker.v1.MsgGrantAllowanceRequest
import io.provenance.marker.v1.MsgIbcTransferRequest
import io.provenance.marker.v1.MsgMintRequest
import io.provenance.marker.v1.MsgSetDenomMetadataRequest
import io.provenance.marker.v1.MsgTransferRequest
import io.provenance.marker.v1.MsgWithdrawRequest
import io.provenance.marker.v1.msgTransferRequest
import io.provenance.metadata.v1.MsgAddContractSpecToScopeSpecRequest
import io.provenance.metadata.v1.MsgAddScopeDataAccessRequest
import io.provenance.metadata.v1.MsgAddScopeOwnerRequest
import io.provenance.metadata.v1.MsgBindOSLocatorRequest
import io.provenance.metadata.v1.MsgDeleteContractSpecFromScopeSpecRequest
import io.provenance.metadata.v1.MsgDeleteContractSpecificationRequest
import io.provenance.metadata.v1.MsgDeleteOSLocatorRequest
import io.provenance.metadata.v1.MsgDeleteRecordRequest
import io.provenance.metadata.v1.MsgDeleteRecordSpecificationRequest
import io.provenance.metadata.v1.MsgDeleteScopeDataAccessRequest
import io.provenance.metadata.v1.MsgDeleteScopeOwnerRequest
import io.provenance.metadata.v1.MsgDeleteScopeRequest
import io.provenance.metadata.v1.MsgDeleteScopeSpecificationRequest
import io.provenance.metadata.v1.MsgModifyOSLocatorRequest
import io.provenance.metadata.v1.MsgP8eMemorializeContractRequest
import io.provenance.metadata.v1.MsgWriteContractSpecificationRequest
import io.provenance.metadata.v1.MsgWriteP8eContractSpecRequest
import io.provenance.metadata.v1.MsgWriteRecordRequest
import io.provenance.metadata.v1.MsgWriteRecordSpecificationRequest
import io.provenance.metadata.v1.MsgWriteScopeRequest
import io.provenance.metadata.v1.MsgWriteScopeSpecificationRequest
import io.provenance.metadata.v1.MsgWriteSessionRequest
import io.provenance.metadata.v1.SessionIdComponents
import io.provenance.name.v1.MsgBindNameRequest
import io.provenance.name.v1.MsgDeleteNameRequest
import net.pearx.kasechange.toSnakeCase
import net.pearx.kasechange.universalWordSplitter

/**
 * Ginormous file meant to convert a Msg object to the proper format, and do stuff with it.
 */
fun Any.toMsgSend() = this.unpack(Tx.MsgSend::class.java)
fun Any.toMsgMultiSend() = this.unpack(Tx.MsgMultiSend::class.java)
fun Any.toMsgSubmitProposalOld() = this.unpack(cosmos.gov.v1beta1.Tx.MsgSubmitProposal::class.java)
fun Any.toMsgVoteOld() = this.unpack(cosmos.gov.v1beta1.Tx.MsgVote::class.java)
fun Any.toMsgVoteWeightedOld() = this.unpack(cosmos.gov.v1beta1.Tx.MsgVoteWeighted::class.java)
fun Any.toMsgDepositOld() = this.unpack(cosmos.gov.v1beta1.Tx.MsgDeposit::class.java)
fun Any.toMsgSoftwareUpgrade() = this.unpack(MsgSoftwareUpgrade::class.java)
fun Any.toMsgCancelUpgrade() = this.unpack(MsgCancelUpgrade::class.java)
fun Any.toMsgSubmitProposal() = this.unpack(MsgSubmitProposal::class.java)
fun Any.toMsgExecLegacyContent() = this.unpack(cosmos.gov.v1.Tx.MsgExecLegacyContent::class.java)
fun Any.toMsgVote() = this.unpack(cosmos.gov.v1.Tx.MsgVote::class.java)
fun Any.toMsgVoteWeighted() = this.unpack(MsgVoteWeighted::class.java)
fun Any.toMsgDeposit() = this.unpack(cosmos.gov.v1.Tx.MsgDeposit::class.java)
fun Any.toMsgSendNft() = this.unpack(MsgSend::class.java)

fun Any.toMsgSetWithdrawAddress() = this.unpack(cosmos.distribution.v1beta1.Tx.MsgSetWithdrawAddress::class.java)
fun Any.toMsgWithdrawDelegatorReward() =
    this.unpack(cosmos.distribution.v1beta1.Tx.MsgWithdrawDelegatorReward::class.java)

fun Any.toMsgWithdrawValidatorCommission() =
    this.unpack(cosmos.distribution.v1beta1.Tx.MsgWithdrawValidatorCommission::class.java)

fun Any.toMsgFundCommunityPool() = this.unpack(cosmos.distribution.v1beta1.Tx.MsgFundCommunityPool::class.java)
fun Any.toMsgSubmitEvidence() = this.unpack(cosmos.evidence.v1beta1.Tx.MsgSubmitEvidence::class.java)
fun Any.toMsgUnjail() = this.unpack(cosmos.slashing.v1beta1.Tx.MsgUnjail::class.java)
fun Any.toMsgCreateValidator() = this.unpack(cosmos.staking.v1beta1.Tx.MsgCreateValidator::class.java)
fun Any.toMsgEditValidator() = this.unpack(cosmos.staking.v1beta1.Tx.MsgEditValidator::class.java)
fun Any.toMsgDelegate() = this.unpack(cosmos.staking.v1beta1.Tx.MsgDelegate::class.java)
fun Any.toMsgBeginRedelegate() = this.unpack(cosmos.staking.v1beta1.Tx.MsgBeginRedelegate::class.java)
fun Any.toMsgUndelegate() = this.unpack(cosmos.staking.v1beta1.Tx.MsgUndelegate::class.java)
fun Any.toMsgCancelUnbondingDelegation() = this.unpack(MsgCancelUnbondingDelegation::class.java)
fun Any.toMsgCreateVestingAccount() = this.unpack(cosmos.vesting.v1beta1.Tx.MsgCreateVestingAccount::class.java)
fun Any.toMsgCreatePermanentLockedAccount() =
    this.unpack(cosmos.vesting.v1beta1.Tx.MsgCreatePermanentLockedAccount::class.java)

fun Any.toMsgCreatePeriodicVestingAccount() =
    this.unpack(cosmos.vesting.v1beta1.Tx.MsgCreatePeriodicVestingAccount::class.java)

fun Any.toMsgWithdrawRequest() = this.unpack(MsgWithdrawRequest::class.java)
fun Any.toMsgAddMarkerRequest() = this.unpack(MsgAddMarkerRequest::class.java)
fun Any.toMsgAddAccessRequest() = this.unpack(MsgAddAccessRequest::class.java)
fun Any.toMsgDeleteAccessRequest() = this.unpack(MsgDeleteAccessRequest::class.java)
fun Any.toMsgFinalizeRequest() = this.unpack(MsgFinalizeRequest::class.java)
fun Any.toMsgActivateRequest() = this.unpack(MsgActivateRequest::class.java)
fun Any.toMsgCancelRequest() = this.unpack(MsgCancelRequest::class.java)
fun Any.toMsgDeleteRequest() = this.unpack(MsgDeleteRequest::class.java)
fun Any.toMsgMintRequest() = this.unpack(MsgMintRequest::class.java)
fun Any.toMsgBurnRequest() = this.unpack(MsgBurnRequest::class.java)
fun Any.toMsgTransferRequest() = this.unpack(MsgTransferRequest::class.java)
fun Any.toMsgSetDenomMetadataRequest() = this.unpack(MsgSetDenomMetadataRequest::class.java)
fun Any.toMsgBindNameRequest() = this.unpack(MsgBindNameRequest::class.java)
fun Any.toMsgDeleteNameRequest() = this.unpack(MsgDeleteNameRequest::class.java)
fun Any.toMsgAddAttributeRequest() = this.unpack(MsgAddAttributeRequest::class.java)
fun Any.toMsgDeleteAttributeRequest() = this.unpack(MsgDeleteAttributeRequest::class.java)
fun Any.toMsgP8eMemorializeContractRequest() = this.unpack(MsgP8eMemorializeContractRequest::class.java)
fun Any.toMsgWriteP8eContractSpecRequest() = this.unpack(MsgWriteP8eContractSpecRequest::class.java)
fun Any.toMsgWriteScopeRequest() = this.unpack(MsgWriteScopeRequest::class.java)
fun Any.toMsgDeleteScopeRequest() = this.unpack(MsgDeleteScopeRequest::class.java)
fun Any.toMsgWriteSessionRequest() = this.unpack(MsgWriteSessionRequest::class.java)
fun Any.toMsgWriteRecordRequest() = this.unpack(MsgWriteRecordRequest::class.java)
fun Any.toMsgDeleteRecordRequest() = this.unpack(MsgDeleteRecordRequest::class.java)
fun Any.toMsgWriteScopeSpecificationRequest() = this.unpack(MsgWriteScopeSpecificationRequest::class.java)
fun Any.toMsgDeleteScopeSpecificationRequest() = this.unpack(MsgDeleteScopeSpecificationRequest::class.java)
fun Any.toMsgWriteContractSpecificationRequest() = this.unpack(MsgWriteContractSpecificationRequest::class.java)
fun Any.toMsgDeleteContractSpecificationRequest() = this.unpack(MsgDeleteContractSpecificationRequest::class.java)
fun Any.toMsgWriteRecordSpecificationRequest() = this.unpack(MsgWriteRecordSpecificationRequest::class.java)
fun Any.toMsgDeleteRecordSpecificationRequest() = this.unpack(MsgDeleteRecordSpecificationRequest::class.java)
fun Any.toMsgBindOSLocatorRequest() = this.unpack(MsgBindOSLocatorRequest::class.java)
fun Any.toMsgDeleteOSLocatorRequest() = this.unpack(MsgDeleteOSLocatorRequest::class.java)
fun Any.toMsgModifyOSLocatorRequest() = this.unpack(MsgModifyOSLocatorRequest::class.java)
fun Any.toMsgStoreCode() = this.unpack(cosmwasm.wasm.v1.Tx.MsgStoreCode::class.java)
fun Any.toMsgInstantiateContract() = this.unpack(cosmwasm.wasm.v1.Tx.MsgInstantiateContract::class.java)
fun Any.toMsgExecuteContract() = this.unpack(cosmwasm.wasm.v1.Tx.MsgExecuteContract::class.java)
fun Any.toMsgMigrateContract() = this.unpack(cosmwasm.wasm.v1.Tx.MsgMigrateContract::class.java)
fun Any.toMsgUpdateAdmin() = this.unpack(cosmwasm.wasm.v1.Tx.MsgUpdateAdmin::class.java)
fun Any.toMsgClearAdmin() = this.unpack(cosmwasm.wasm.v1.Tx.MsgClearAdmin::class.java)
fun Any.toMsgInstantiateContract2() = this.unpack(cosmwasm.wasm.v1.Tx.MsgInstantiateContract2::class.java)
fun Any.toMsgStoreCodeOld() = this.unpack(cosmwasm.wasm.v1beta1.Tx.MsgStoreCode::class.java)
fun Any.toMsgInstantiateContractOld() = this.unpack(cosmwasm.wasm.v1beta1.Tx.MsgInstantiateContract::class.java)
fun Any.toMsgExecuteContractOld() = this.unpack(cosmwasm.wasm.v1beta1.Tx.MsgExecuteContract::class.java)
fun Any.toMsgMigrateContractOld() = this.unpack(cosmwasm.wasm.v1beta1.Tx.MsgMigrateContract::class.java)
fun Any.toMsgUpdateAdminOld() = this.unpack(cosmwasm.wasm.v1beta1.Tx.MsgUpdateAdmin::class.java)
fun Any.toMsgClearAdminOld() = this.unpack(cosmwasm.wasm.v1beta1.Tx.MsgClearAdmin::class.java)

fun Any.toMsgAddScopeDataAccessRequest() = this.unpack(MsgAddScopeDataAccessRequest::class.java)
fun Any.toMsgDeleteScopeDataAccessRequest() = this.unpack(MsgDeleteScopeDataAccessRequest::class.java)
fun Any.toMsgAddScopeOwnerRequest() = this.unpack(MsgAddScopeOwnerRequest::class.java)
fun Any.toMsgDeleteScopeOwnerRequest() = this.unpack(MsgDeleteScopeOwnerRequest::class.java)
fun Any.toMsgUpdateAttributeRequest() = this.unpack(MsgUpdateAttributeRequest::class.java)
fun Any.toMsgDeleteDistinctAttributeRequest() = this.unpack(MsgDeleteDistinctAttributeRequest::class.java)
fun Any.toMsgAddContractSpecToScopeSpecRequest() = this.unpack(MsgAddContractSpecToScopeSpecRequest::class.java)
fun Any.toMsgDeleteContractSpecFromScopeSpecRequest() =
    this.unpack(MsgDeleteContractSpecFromScopeSpecRequest::class.java)

fun Any.toMsgIbcTransferRequest() = this.unpack(MsgIbcTransferRequest::class.java)
fun Any.toMsgTransfer() = this.unpack(MsgTransfer::class.java)
fun Any.toMsgChannelOpenInit() = this.unpack(MsgChannelOpenInit::class.java)
fun Any.toMsgChannelOpenTry() = this.unpack(MsgChannelOpenTry::class.java)
fun Any.toMsgChannelOpenAck() = this.unpack(MsgChannelOpenAck::class.java)
fun Any.toMsgChannelOpenConfirm() = this.unpack(MsgChannelOpenConfirm::class.java)
fun Any.toMsgChannelCloseInit() = this.unpack(MsgChannelCloseInit::class.java)
fun Any.toMsgChannelCloseConfirm() = this.unpack(MsgChannelCloseConfirm::class.java)
fun Any.toMsgRecvPacket() = this.unpack(MsgRecvPacket::class.java)
fun Any.toMsgTimeout() = this.unpack(MsgTimeout::class.java)
fun Any.toMsgTimeoutOnClose() = this.unpack(MsgTimeoutOnClose::class.java)
fun Any.toMsgAcknowledgement() = this.unpack(MsgAcknowledgement::class.java)
fun Any.toMsgCreateClient() = this.unpack(MsgCreateClient::class.java)
fun Any.toMsgUpdateClient() = this.unpack(MsgUpdateClient::class.java)
fun Any.toMsgUpgradeClient() = this.unpack(MsgUpgradeClient::class.java)
fun Any.toMsgSubmitMisbehaviour() = this.unpack(MsgSubmitMisbehaviour::class.java)
fun Any.toMsgConnectionOpenInit() = this.unpack(MsgConnectionOpenInit::class.java)
fun Any.toMsgConnectionOpenTry() = this.unpack(MsgConnectionOpenTry::class.java)
fun Any.toMsgConnectionOpenAck() = this.unpack(MsgConnectionOpenAck::class.java)
fun Any.toMsgConnectionOpenConfirm() = this.unpack(MsgConnectionOpenConfirm::class.java)
fun Any.toTendermintClientState() = this.unpack(Tendermint.ClientState::class.java)
fun Any.toLocalhostClientState() = this.unpack(Localhost.ClientState::class.java)
fun Any.toSoloMachineClientState() = this.unpack(Solomachine.ClientState::class.java)
fun Any.toMsgRegisterPayee() = this.unpack(MsgRegisterPayee::class.java)
fun Any.toMsgRegisterCounterpartyPayee() = this.unpack(MsgRegisterCounterpartyPayee::class.java)
fun Any.toMsgPayPacketFee() = this.unpack(MsgPayPacketFee::class.java)
fun Any.toMsgPayPacketFeeAsync() = this.unpack(MsgPayPacketFeeAsync::class.java)

fun Any.toMsgGrant() = this.unpack(cosmos.authz.v1beta1.Tx.MsgGrant::class.java)
fun Any.toMsgExec() = this.unpack(cosmos.authz.v1beta1.Tx.MsgExec::class.java)
fun Any.toMsgRevoke() = this.unpack(cosmos.authz.v1beta1.Tx.MsgRevoke::class.java)
fun Any.toMsgGrantAllowance() = this.unpack(cosmos.feegrant.v1beta1.Tx.MsgGrantAllowance::class.java)
fun Any.toMsgRevokeAllowance() = this.unpack(cosmos.feegrant.v1beta1.Tx.MsgRevokeAllowance::class.java)
fun Any.toMsgGrantAllowanceRequest() = this.unpack(MsgGrantAllowanceRequest::class.java)

fun Any.toMsgCreateGroup() = this.unpack(cosmos.group.v1.Tx.MsgCreateGroup::class.java)
fun Any.toMsgUpdateGroupMembers() = this.unpack(cosmos.group.v1.Tx.MsgUpdateGroupMembers::class.java)
fun Any.toMsgUpdateGroupAdmin() = this.unpack(cosmos.group.v1.Tx.MsgUpdateGroupAdmin::class.java)
fun Any.toMsgUpdateGroupMetadata() = this.unpack(cosmos.group.v1.Tx.MsgUpdateGroupMetadata::class.java)
fun Any.toMsgCreateGroupPolicy() = this.unpack(cosmos.group.v1.Tx.MsgCreateGroupPolicy::class.java)
fun Any.toMsgCreateGroupWithPolicy() = this.unpack(cosmos.group.v1.Tx.MsgCreateGroupWithPolicy::class.java)
fun Any.toMsgUpdateGroupPolicyAdmin() = this.unpack(cosmos.group.v1.Tx.MsgUpdateGroupPolicyAdmin::class.java)
fun Any.toMsgUpdateGroupPolicyDecisionPolicy() =
    this.unpack(cosmos.group.v1.Tx.MsgUpdateGroupPolicyDecisionPolicy::class.java)

fun Any.toMsgUpdateGroupPolicyMetadata() = this.unpack(cosmos.group.v1.Tx.MsgUpdateGroupPolicyMetadata::class.java)
fun Any.toMsgSubmitProposalGroup() = this.unpack(cosmos.group.v1.Tx.MsgSubmitProposal::class.java)
fun Any.toMsgWithdrawProposalGroup() = this.unpack(cosmos.group.v1.Tx.MsgWithdrawProposal::class.java)
fun Any.toMsgVoteGroup() = this.unpack(cosmos.group.v1.Tx.MsgVote::class.java)
fun Any.toMsgExecGroup() = this.unpack(cosmos.group.v1.Tx.MsgExec::class.java)
fun Any.toMsgLeaveGroup() = this.unpack(cosmos.group.v1.Tx.MsgLeaveGroup::class.java)

//region ADDRESSES
fun Any.getAssociatedAddresses(): List<String> =
    when {
        typeUrl.endsWith("bank.v1beta1.MsgSend") -> this.toMsgSend().let { listOf(it.fromAddress, it.toAddress) }
        typeUrl.endsWith("MsgMultiSend") -> this.toMsgMultiSend()
            .let { it.inputsList.map { inp -> inp.address } + it.outputsList.map { out -> out.address } }
        typeUrl.endsWith("MsgSetWithdrawAddress") -> this.toMsgSetWithdrawAddress()
            .let { listOf(it.delegatorAddress, it.withdrawAddress) }
        typeUrl.endsWith("MsgWithdrawDelegatorReward") -> this.toMsgWithdrawDelegatorReward()
            .let { listOf(it.delegatorAddress, it.validatorAddress) }
        typeUrl.endsWith("MsgWithdrawValidatorCommission") -> this.toMsgWithdrawValidatorCommission()
            .let { listOf(it.validatorAddress) }
        typeUrl.endsWith("MsgFundCommunityPool") -> this.toMsgFundCommunityPool().let { listOf(it.depositor) }
        typeUrl.endsWith("MsgSubmitEvidence") -> this.toMsgSubmitEvidence().let { listOf(it.submitter) }
        typeUrl.endsWith("gov.v1beta1.MsgSubmitProposal") -> this.toMsgSubmitProposalOld().let { listOf(it.proposer) }
        typeUrl.endsWith("gov.v1.MsgSubmitProposal") -> this.toMsgSubmitProposal()
            .let { listOf(it.proposer) + it.messagesList.flatMap { m -> m.getAssociatedAddresses() } }
        typeUrl.endsWith("MsgSoftwareUpgrade") -> this.toMsgSoftwareUpgrade().let { listOf(it.authority) }
        typeUrl.endsWith("MsgCancelUpgrade") -> this.toMsgCancelUpgrade().let { listOf(it.authority) }
        typeUrl.endsWith("gov.v1beta1.MsgVote") -> this.toMsgVoteOld().let { listOf(it.voter) }
        typeUrl.endsWith("gov.v1.MsgVote") -> this.toMsgVote().let { listOf(it.voter) }
        typeUrl.endsWith("gov.v1beta1.MsgVoteWeighted") -> this.toMsgVoteWeightedOld().let { listOf(it.voter) }
        typeUrl.endsWith("gov.v1.MsgVoteWeighted") -> this.toMsgVoteWeighted().let { listOf(it.voter) }
        typeUrl.endsWith("gov.v1beta1.MsgDeposit") -> this.toMsgDepositOld().let { listOf(it.depositor) }
        typeUrl.endsWith("gov.v1.MsgDeposit") -> this.toMsgDeposit().let { listOf(it.depositor) }
        typeUrl.endsWith("MsgUnjail") -> this.toMsgUnjail().let { listOf(it.validatorAddr) }
        typeUrl.endsWith("MsgCreateValidator") -> this.toMsgCreateValidator()
            .let { listOf(it.validatorAddress, it.delegatorAddress) }
        typeUrl.endsWith("MsgEditValidator") -> this.toMsgEditValidator().let { listOf(it.validatorAddress) }
        typeUrl.endsWith("MsgDelegate") -> this.toMsgDelegate().let { listOf(it.validatorAddress, it.delegatorAddress) }
        typeUrl.endsWith("MsgBeginRedelegate") -> this.toMsgBeginRedelegate()
            .let { listOf(it.delegatorAddress, it.validatorDstAddress, it.validatorSrcAddress) }
        typeUrl.endsWith("MsgUndelegate") -> this.toMsgUndelegate()
            .let { listOf(it.delegatorAddress, it.validatorAddress) }
        typeUrl.endsWith("MsgCancelUnbondingDelegation") -> this.toMsgCancelUnbondingDelegation()
            .let { listOf(it.delegatorAddress, it.validatorAddress) }
        typeUrl.endsWith("MsgCreateVestingAccount") -> this.toMsgCreateVestingAccount()
            .let { listOf(it.fromAddress, it.toAddress) }
        typeUrl.endsWith("MsgCreatePermanentLockedAccount") -> this.toMsgCreatePermanentLockedAccount()
            .let { listOf(it.fromAddress, it.toAddress) }
        typeUrl.endsWith("MsgCreatePeriodicVestingAccount") -> this.toMsgCreatePeriodicVestingAccount()
            .let { listOf(it.fromAddress, it.toAddress) }
        typeUrl.endsWith("MsgWithdrawRequest") -> this.toMsgWithdrawRequest()
            .let { listOf(it.toAddress, it.administrator) }
        typeUrl.endsWith("MsgAddMarkerRequest") -> this.toMsgAddMarkerRequest()
            .let { listOf(it.fromAddress, it.manager) + it.accessListList.map { acc -> acc.address } }
        typeUrl.endsWith("MsgAddAccessRequest") -> this.toMsgAddAccessRequest()
            .let { it.accessList.map { l -> l.address } + it.administrator }
        typeUrl.endsWith("MsgDeleteAccessRequest") -> this.toMsgDeleteAccessRequest()
            .let { listOf(it.administrator, it.removedAddress) }
        typeUrl.endsWith("MsgFinalizeRequest") -> this.toMsgFinalizeRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgActivateRequest") -> this.toMsgActivateRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgCancelRequest") -> this.toMsgCancelRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgDeleteRequest") -> this.toMsgDeleteRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgMintRequest") -> this.toMsgMintRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgBurnRequest") -> this.toMsgBurnRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgTransferRequest") -> this.toMsgTransferRequest()
            .let { listOf(it.administrator, it.toAddress, it.fromAddress) }
        typeUrl.endsWith("MsgSetDenomMetadataRequest") -> this.toMsgSetDenomMetadataRequest()
            .let { listOf(it.administrator) }
        typeUrl.endsWith("MsgBindNameRequest") -> this.toMsgBindNameRequest()
            .let { listOf(it.parent.address, it.record.address) }
        typeUrl.endsWith("MsgDeleteNameRequest") -> this.toMsgDeleteNameRequest().let { listOf(it.record.address) }
        typeUrl.endsWith("MsgAddAttributeRequest") -> this.toMsgAddAttributeRequest()
            .let { listOf(it.account, it.owner) }
        typeUrl.endsWith("MsgDeleteAttributeRequest") -> this.toMsgDeleteAttributeRequest()
            .let { listOf(it.account, it.owner) }
        typeUrl.endsWith("MsgP8eMemorializeContractRequest") -> this.toMsgP8eMemorializeContractRequest()
            .let { listOf(it.invoker) }
        typeUrl.endsWith("MsgWriteP8eContractSpecRequest") -> this.toMsgWriteP8eContractSpecRequest().signersList
        typeUrl.endsWith("MsgWriteScopeRequest") -> this.toMsgWriteScopeRequest()
            .let { it.signersList + it.scope.ownersList.map { o -> o.address } + listOf(it.scope.valueOwnerAddress) }
        typeUrl.endsWith("MsgDeleteScopeRequest") -> this.toMsgDeleteScopeRequest().signersList
        typeUrl.endsWith("MsgWriteSessionRequest") -> this.toMsgWriteSessionRequest()
            .let { it.signersList + it.session.partiesList.map { o -> o.address } }
        typeUrl.endsWith("MsgWriteRecordRequest") -> this.toMsgWriteRecordRequest()
            .let { it.signersList + it.partiesList.map { o -> o.address } }
        typeUrl.endsWith("MsgDeleteRecordRequest") -> this.toMsgDeleteRecordRequest().signersList
        typeUrl.endsWith("MsgWriteScopeSpecificationRequest") -> this.toMsgWriteScopeSpecificationRequest()
            .let { it.signersList + it.specification.ownerAddressesList }
        typeUrl.endsWith("MsgDeleteScopeSpecificationRequest") -> this.toMsgDeleteScopeSpecificationRequest().signersList
        typeUrl.endsWith("MsgWriteContractSpecificationRequest") -> this.toMsgWriteContractSpecificationRequest()
            .let { it.signersList + it.specification.ownerAddressesList }
        typeUrl.endsWith("MsgDeleteContractSpecificationRequest") -> this.toMsgDeleteContractSpecificationRequest().signersList
        typeUrl.endsWith("MsgWriteRecordSpecificationRequest") -> this.toMsgWriteRecordSpecificationRequest().signersList
        typeUrl.endsWith("MsgDeleteRecordSpecificationRequest") -> this.toMsgDeleteRecordSpecificationRequest().signersList
        typeUrl.endsWith("MsgBindOSLocatorRequest") -> this.toMsgBindOSLocatorRequest().let { listOf(it.locator.owner) }
        typeUrl.endsWith("MsgDeleteOSLocatorRequest") -> this.toMsgDeleteOSLocatorRequest()
            .let { listOf(it.locator.owner) }
        typeUrl.endsWith("MsgModifyOSLocatorRequest") -> this.toMsgModifyOSLocatorRequest()
            .let { listOf(it.locator.owner) }
        typeUrl.endsWith("v1.MsgStoreCode") -> this.toMsgStoreCode().let { listOf(it.sender) }
        typeUrl.endsWith("v1.MsgInstantiateContract") -> this.toMsgInstantiateContract()
            .let { listOf(it.sender, it.admin) }
        typeUrl.endsWith("v1.MsgInstantiateContract2") -> this.toMsgInstantiateContract2()
            .let { listOf(it.sender, it.admin) }
        typeUrl.endsWith("v1.MsgExecuteContract") -> this.toMsgExecuteContract().let { listOf(it.sender) }
        typeUrl.endsWith("v1.MsgMigrateContract") -> this.toMsgMigrateContract().let { listOf(it.sender) }
        typeUrl.endsWith("v1.MsgUpdateAdmin") -> this.toMsgUpdateAdmin().let { listOf(it.sender, it.newAdmin) }
        typeUrl.endsWith("v1.MsgClearAdmin") -> this.toMsgClearAdmin().let { listOf(it.sender) }
        typeUrl.endsWith("v1beta1.MsgStoreCode") -> this.toMsgStoreCodeOld().let { listOf(it.sender) }
        typeUrl.endsWith("v1beta1.MsgInstantiateContract") -> this.toMsgInstantiateContractOld()
            .let { listOf(it.sender, it.admin) }
        typeUrl.endsWith("v1beta1.MsgExecuteContract") -> this.toMsgExecuteContractOld().let { listOf(it.sender) }
        typeUrl.endsWith("v1beta1.MsgMigrateContract") -> this.toMsgMigrateContractOld().let { listOf(it.sender) }
        typeUrl.endsWith("v1beta1.MsgUpdateAdmin") -> this.toMsgUpdateAdminOld().let { listOf(it.sender, it.newAdmin) }
        typeUrl.endsWith("v1beta1.MsgClearAdmin") -> this.toMsgClearAdminOld().let { listOf(it.sender) }
        typeUrl.endsWith("MsgAddScopeDataAccessRequest") -> this.toMsgAddScopeDataAccessRequest()
            .let { it.signersList + it.dataAccessList }
        typeUrl.endsWith("MsgDeleteScopeDataAccessRequest") -> this.toMsgDeleteScopeDataAccessRequest()
            .let { it.signersList + it.dataAccessList }
        typeUrl.endsWith("MsgAddScopeOwnerRequest") -> this.toMsgAddScopeOwnerRequest()
            .let { it.ownersList.map { o -> o.address } + it.signersList }
        typeUrl.endsWith("MsgDeleteScopeOwnerRequest") -> this.toMsgDeleteScopeOwnerRequest()
            .let { it.ownersList + it.signersList }
        typeUrl.endsWith("MsgUpdateAttributeRequest") -> this.toMsgUpdateAttributeRequest()
            .let { listOf(it.account, it.owner) }
        typeUrl.endsWith("MsgDeleteDistinctAttributeRequest") -> this.toMsgDeleteDistinctAttributeRequest()
            .let { listOf(it.account, it.owner) }
        typeUrl.endsWith("MsgAddContractSpecToScopeSpecRequest") -> this.toMsgAddContractSpecToScopeSpecRequest().signersList
        typeUrl.endsWith("MsgDeleteContractSpecFromScopeSpecRequest") -> this.toMsgDeleteContractSpecFromScopeSpecRequest().signersList
        typeUrl.endsWith("MsgIbcTransferRequest") -> this.toMsgIbcTransferRequest()
            .let { listOf(it.administrator, it.transfer.sender) }
        typeUrl.endsWith("MsgTransfer") -> this.toMsgTransfer().let { listOf(it.sender) }
        typeUrl.endsWith("MsgChannelOpenInit") -> this.toMsgChannelOpenInit().let { listOf(it.signer) }
        typeUrl.endsWith("MsgChannelOpenTry") -> this.toMsgChannelOpenTry().let { listOf(it.signer) }
        typeUrl.endsWith("MsgChannelOpenAck") -> this.toMsgChannelOpenAck().let { listOf(it.signer) }
        typeUrl.endsWith("MsgChannelOpenConfirm") -> this.toMsgChannelOpenConfirm().let { listOf(it.signer) }
        typeUrl.endsWith("MsgChannelCloseInit") -> this.toMsgChannelCloseInit().let { listOf(it.signer) }
        typeUrl.endsWith("MsgChannelCloseConfirm") -> this.toMsgChannelCloseConfirm().let { listOf(it.signer) }
        typeUrl.endsWith("MsgRecvPacket") -> this.toMsgRecvPacket().let { listOf(it.signer) }
        typeUrl.endsWith("MsgTimeout") -> this.toMsgTimeout().let { listOf(it.signer) }
        typeUrl.endsWith("MsgTimeoutOnClose") -> this.toMsgTimeoutOnClose().let { listOf(it.signer) }
        typeUrl.endsWith("MsgAcknowledgement") -> this.toMsgAcknowledgement().let { listOf(it.signer) }
        typeUrl.endsWith("MsgCreateClient") -> this.toMsgCreateClient().let { listOf(it.signer) }
        typeUrl.endsWith("MsgUpdateClient") -> this.toMsgUpdateClient().let { listOf(it.signer) }
        typeUrl.endsWith("MsgUpgradeClient") -> this.toMsgUpgradeClient().let { listOf(it.signer) }
        typeUrl.endsWith("MsgSubmitMisbehaviour") -> this.toMsgSubmitMisbehaviour().let { listOf(it.signer) }
        typeUrl.endsWith("MsgConnectionOpenInit") -> this.toMsgConnectionOpenInit().let { listOf(it.signer) }
        typeUrl.endsWith("MsgConnectionOpenTry") -> this.toMsgConnectionOpenTry().let { listOf(it.signer) }
        typeUrl.endsWith("MsgConnectionOpenAck") -> this.toMsgConnectionOpenAck().let { listOf(it.signer) }
        typeUrl.endsWith("MsgConnectionOpenConfirm") -> this.toMsgConnectionOpenConfirm().let { listOf(it.signer) }
        typeUrl.endsWith("MsgRegisterPayee") -> this.toMsgRegisterPayee().let { listOf(it.relayer, it.payee) }
        typeUrl.endsWith("MsgRegisterCounterpartyPayee") -> this.toMsgRegisterCounterpartyPayee()
            .let { listOf(it.relayer) }
        typeUrl.endsWith("MsgPayPacketFee") -> this.toMsgPayPacketFee().let { listOf(it.signer) + it.relayersList }
        typeUrl.endsWith("MsgPayPacketFeeAsync") -> this.toMsgPayPacketFeeAsync()
            .let { listOf(it.packetFee.refundAddress) + it.packetFee.relayersList }
        typeUrl.endsWith("MsgGrant") -> this.toMsgGrant().let { listOf(it.granter, it.grantee) }
        typeUrl.endsWith("authz.v1beta1.MsgExec") -> this.toMsgExec()
            .let { exec -> exec.msgsList.flatMap { it.getAssociatedAddresses() } + listOf(exec.grantee) }
        typeUrl.endsWith("MsgRevoke") -> this.toMsgRevoke().let { listOf(it.granter, it.grantee) }
        typeUrl.endsWith("MsgGrantAllowance") -> this.toMsgGrantAllowance().let { listOf(it.granter, it.grantee) }
        typeUrl.endsWith("MsgRevokeAllowance") -> this.toMsgRevokeAllowance().let { listOf(it.granter, it.grantee) }
        typeUrl.endsWith("MsgGrantAllowanceRequest") -> this.toMsgGrantAllowanceRequest()
            .let { listOf(it.administrator, it.grantee) }
        typeUrl.endsWith("group.v1.MsgCreateGroup") -> this.toMsgCreateGroup()
            .let { listOf(it.admin) + it.membersList.map { mem -> mem.address } }
        typeUrl.endsWith("group.v1.MsgUpdateGroupMembers") -> this.toMsgUpdateGroupMembers()
            .let { listOf(it.admin) + it.memberUpdatesList.map { mem -> mem.address } }
        typeUrl.endsWith("group.v1.MsgUpdateGroupAdmin") -> this.toMsgUpdateGroupAdmin()
            .let { listOf(it.admin, it.newAdmin) }
        typeUrl.endsWith("group.v1.MsgUpdateGroupMetadata") -> listOf(this.toMsgUpdateGroupMetadata().admin)
        typeUrl.endsWith("group.v1.MsgCreateGroupPolicy") -> listOf(this.toMsgCreateGroupPolicy().admin)
        typeUrl.endsWith("group.v1.MsgCreateGroupWithPolicy") -> this.toMsgCreateGroupWithPolicy()
            .let { listOf(it.admin) + it.membersList.map { mem -> mem.address } }
        typeUrl.endsWith("group.v1.MsgUpdateGroupPolicyAdmin") -> this.toMsgUpdateGroupPolicyAdmin()
            .let { listOf(it.admin, it.groupPolicyAddress, it.newAdmin) }
        typeUrl.endsWith("group.v1.MsgUpdateGroupPolicyDecisionPolicy") -> this.toMsgUpdateGroupPolicyDecisionPolicy()
            .let { listOf(it.admin, it.groupPolicyAddress) }
        typeUrl.endsWith("group.v1.MsgUpdateGroupPolicyMetadata") -> this.toMsgUpdateGroupPolicyMetadata()
            .let { listOf(it.admin, it.groupPolicyAddress) }
        typeUrl.endsWith("group.v1.MsgSubmitProposal") -> this.toMsgSubmitProposalGroup()
            .let { listOf(it.groupPolicyAddress) + it.proposersList + it.messagesList.flatMap { msg -> msg.getAssociatedAddresses() } }
        typeUrl.endsWith("group.v1.MsgWithdrawProposal") -> listOf(this.toMsgWithdrawProposalGroup().address)
        typeUrl.endsWith("group.v1.MsgVote") -> listOf(this.toMsgVoteGroup().voter)
        typeUrl.endsWith("group.v1.MsgExec") -> listOf(this.toMsgExecGroup().executor)
        typeUrl.endsWith("group.v1.MsgLeaveGroup") -> listOf(this.toMsgLeaveGroup().address)
        typeUrl.endsWith("nft.v1beta1.MsgSend") -> this.toMsgSendNft().let { listOf(it.sender, it.receiver) }

        else -> listOf<String>().also { logger().debug("This typeUrl is not yet supported as an address-based msg: $typeUrl") }
    }

//endregion

//region DENOMS
fun Any.getAssociatedDenoms(): List<String> =
    when {
        typeUrl.endsWith("bank.v1beta1.MsgSend") -> this.toMsgSend().let { it.amountList.map { am -> am.denom } }
        typeUrl.endsWith("MsgMultiSend") -> this.toMsgMultiSend()
            .let {
                it.inputsList.flatMap { inp -> inp.coinsList.map { c -> c.denom } } +
                    it.outputsList.flatMap { out -> out.coinsList.map { c -> c.denom } }
            }
        typeUrl.endsWith("MsgWithdrawRequest") -> this.toMsgWithdrawRequest().let { listOf(it.denom) }
        typeUrl.endsWith("MsgAddMarkerRequest") -> this.toMsgAddMarkerRequest().let { listOf(it.amount.denom) }
        typeUrl.endsWith("MsgAddAccessRequest") -> this.toMsgAddAccessRequest().let { listOf(it.denom) }
        typeUrl.endsWith("MsgDeleteAccessRequest") -> this.toMsgDeleteAccessRequest().let { listOf(it.denom) }
        typeUrl.endsWith("MsgFinalizeRequest") -> this.toMsgFinalizeRequest().let { listOf(it.denom) }
        typeUrl.endsWith("MsgActivateRequest") -> this.toMsgActivateRequest().let { listOf(it.denom) }
        typeUrl.endsWith("MsgCancelRequest") -> this.toMsgCancelRequest().let { listOf(it.denom) }
        typeUrl.endsWith("MsgDeleteRequest") -> this.toMsgDeleteRequest().let { listOf(it.denom) }
        typeUrl.endsWith("MsgMintRequest") -> this.toMsgMintRequest().let { listOf(it.amount.denom) }
        typeUrl.endsWith("MsgBurnRequest") -> this.toMsgBurnRequest().let { listOf(it.amount.denom) }
        typeUrl.endsWith("MsgTransferRequest") -> this.toMsgTransferRequest().let { listOf(it.amount.denom) }
        typeUrl.endsWith("MsgSetDenomMetadataRequest") -> this.toMsgSetDenomMetadataRequest()
            .let { listOf(it.metadata.base) }
        typeUrl.endsWith("MsgAddAttributeRequest") -> this.toMsgAddAttributeRequest()
            .let { it.account.getDenomByAddress()?.let { denom -> listOf(denom) } ?: listOf() }
        typeUrl.endsWith("MsgDeleteAttributeRequest") -> this.toMsgDeleteAttributeRequest()
            .let { it.account.getDenomByAddress()?.let { denom -> listOf(denom) } ?: listOf() }
        typeUrl.endsWith("v1.MsgInstantiateContract") -> this.toMsgInstantiateContract()
            .let { it.fundsList.map { c -> c.denom } }
        typeUrl.endsWith("v1.MsgInstantiateContract2") -> this.toMsgInstantiateContract2()
            .let { it.fundsList.map { c -> c.denom } }
        typeUrl.endsWith("v1.MsgExecuteContract") -> this.toMsgExecuteContract()
            .let { it.fundsList.map { c -> c.denom } }
        typeUrl.endsWith("v1beta1.MsgInstantiateContract") -> this.toMsgInstantiateContractOld()
            .let { it.fundsList.map { c -> c.denom } }
        typeUrl.endsWith("v1beta1.MsgExecuteContract") -> this.toMsgExecuteContractOld()
            .let { it.fundsList.map { c -> c.denom } }
        typeUrl.endsWith("MsgIbcTransferRequest") -> this.toMsgIbcTransferRequest()
            .let { listOf(it.transfer.token.denom) }
        typeUrl.endsWith("MsgTransfer") -> this.toMsgTransfer().let { listOf(it.token.denom) }
        typeUrl.endsWith("MsgCreateVestingAccount") -> this.toMsgCreateVestingAccount()
            .let { it.amountList.map { c -> c.denom } }
        typeUrl.endsWith("MsgCreatePermanentLockedAccount") -> this.toMsgCreatePermanentLockedAccount()
            .let { it.amountList.map { c -> c.denom } }
        typeUrl.endsWith("MsgCreatePeriodicVestingAccount") -> this.toMsgCreatePeriodicVestingAccount()
            .let { it.vestingPeriodsList.flatMap { p -> p.amountList.map { c -> c.denom } } }
        typeUrl.endsWith("authz.v1beta1.MsgExec") -> this.toMsgExec().msgsList.flatMap { it.getAssociatedDenoms() }
        typeUrl.endsWith("MsgGrantAllowanceRequest") -> this.toMsgGrantAllowanceRequest().let { listOf(it.denom) }
        typeUrl.endsWith("MsgPayPacketFee") -> this.toMsgPayPacketFee().fee.let { fee ->
            fee.recvFeeList.map { it.denom } + fee.ackFeeList.map { it.denom } + fee.timeoutFeeList.map { it.denom }
        }
        typeUrl.endsWith("MsgPayPacketFeeAsync") -> this.toMsgPayPacketFeeAsync().packetFee.fee.let { fee ->
            fee.recvFeeList.map { it.denom } + fee.ackFeeList.map { it.denom } + fee.timeoutFeeList.map { it.denom }
        }

        else -> listOf<String>()
            .also { logger().debug("This typeUrl is not yet supported as an asset-based msg: $typeUrl") }
    }

//endregion

//region IBC
fun Any.isIbcTransferMsg() = typeUrl.endsWith("MsgTransfer")

fun Any.getTxIbcClientChannel() =
    when {
        typeUrl.endsWith("MsgCreateClient") -> TxIbcData(null, null, null, "create_client", "client_id", null, null)
        typeUrl.endsWith("MsgUpdateClient") -> this.toMsgUpdateClient()
            .let { TxIbcData(it.clientId, null, null, "update_client", "client_id", null, null) }
        typeUrl.endsWith("MsgConnectionOpenInit") -> this.toMsgConnectionOpenInit()
            .let { TxIbcData(it.clientId, null, null, "connection_open_init", "client_id", null, null) }
        typeUrl.endsWith("MsgConnectionOpenTry") -> this.toMsgConnectionOpenTry()
            .let { TxIbcData(it.clientId, null, null, "connection_open_try", "client_id", null, null) }
        typeUrl.endsWith("MsgConnectionOpenAck") -> TxIbcData(
            null,
            null,
            null,
            "connection_open_ack",
            "client_id",
            null,
            null
        )
        typeUrl.endsWith("MsgConnectionOpenConfirm") -> TxIbcData(
            null,
            null,
            null,
            "connection_open_confirm",
            "client_id",
            null,
            null
        )
        typeUrl.endsWith("MsgChannelOpenInit") -> this.toMsgChannelOpenInit()
            .let { TxIbcData(null, it.portId, null, "channel_open_init", null, "port_id", "channel_id") }
        typeUrl.endsWith("MsgChannelOpenTry") -> this.toMsgChannelOpenTry()
            .let { TxIbcData(null, it.portId, null, "channel_open_try", null, "port_id", "channel_id") }
        typeUrl.endsWith("MsgChannelOpenAck") -> this.toMsgChannelOpenAck()
            .let { TxIbcData(null, it.portId, it.channelId, "channel_open_ack", null, "port_id", "channel_id") }
        typeUrl.endsWith("MsgChannelOpenConfirm") -> this.toMsgChannelOpenConfirm()
            .let { TxIbcData(null, it.portId, it.channelId, "channel_open_confirm", null, "port_id", "channel_id") }
        typeUrl.endsWith("MsgChannelCloseInit") -> this.toMsgChannelCloseInit()
            .let { TxIbcData(null, it.portId, it.channelId, "channel_close_init", null, "port_id", "channel_id") }
        typeUrl.endsWith("MsgChannelCloseConfirm") -> this.toMsgChannelCloseConfirm()
            .let { TxIbcData(null, it.portId, it.channelId, "channel_close_confirm", null, "port_id", "channel_id") }
        typeUrl.endsWith("MsgTransfer") -> this.toMsgTransfer().let {
            TxIbcData(
                null,
                it.sourcePort,
                it.sourceChannel,
                "send_packet",
                null,
                "packet_src_port",
                "packet_src_channel"
            )
        }
        typeUrl.endsWith("MsgIbcTransferRequest") -> this.toMsgIbcTransferRequest().let {
            TxIbcData(
                null,
                it.transfer.sourcePort,
                it.transfer.sourceChannel,
                "send_packet",
                null,
                "packet_src_port",
                "packet_src_channel"
            )
        }
        typeUrl.endsWith("MsgRecvPacket") -> this.toMsgRecvPacket().let {
            TxIbcData(
                null,
                it.packet.destinationPort,
                it.packet.destinationChannel,
                "recv_packet",
                null,
                "packet_dst_port",
                "packet_dst_channel"
            )
        }
        typeUrl.endsWith("MsgTimeout") -> this.toMsgTimeout().let {
            TxIbcData(
                null,
                it.packet.sourcePort,
                it.packet.sourceChannel,
                "timeout_packet",
                null,
                "packet_src_port",
                "packet_src_channel"
            )
        }
        typeUrl.endsWith("MsgTimeoutOnClose") -> this.toMsgTimeoutOnClose().let {
            TxIbcData(
                null,
                it.packet.sourcePort,
                it.packet.sourceChannel,
                "timeout_packet",
                null,
                "packet_src_port",
                "packet_src_channel"
            )
        }
        typeUrl.endsWith("MsgAcknowledgement") -> this.toMsgAcknowledgement().let {
            TxIbcData(
                null,
                it.packet.sourcePort,
                it.packet.sourceChannel,
                "acknowledge_packet",
                null,
                "packet_src_port",
                "packet_src_channel"
            )
        }
        else ->
            null.also { logger().debug("This typeUrl is not yet supported in as an ibc tx msg: $typeUrl") }
    }

fun Any.getIbcLedgerMsgs() =
    when {
        typeUrl.endsWith("MsgIbcTransferRequest") ||
            typeUrl.endsWith("MsgTransfer") ||
            typeUrl.endsWith("MsgRecvPacket") ||
            typeUrl.endsWith("MsgAcknowledgement") ||
            typeUrl.endsWith("MsgTimeout")
            || typeUrl.endsWith("MsgTimeoutOnClose") -> this
        else -> null.also { logger().debug("This typeUrl is not yet supported in as an ibc ledger msg: $typeUrl") }
    }

//endregion

//region METADATA (NFT/SCOPES)
enum class MdEvents(val event: String, val idField: String) {
    // Contract Spec
    CSPC("provenance.metadata.v1.EventContractSpecificationCreated", "contract_specification_addr"),
    CSPU("provenance.metadata.v1.EventContractSpecificationUpdated", "contract_specification_addr"),
    CSPD("provenance.metadata.v1.EventContractSpecificationDeleted", "contract_specification_addr"),
    CRSC("provenance.metadata.v1.EventRecordSpecificationCreated", "contract_specification_addr"),
    CRSU("provenance.metadata.v1.EventRecordSpecificationUpdated", "contract_specification_addr"),
    CRSD("provenance.metadata.v1.EventRecordSpecificationDeleted", "contract_specification_addr"),

    // Scope
    SC("provenance.metadata.v1.EventScopeCreated", "scope_addr"),
    SU("provenance.metadata.v1.EventScopeUpdated", "scope_addr"),
    SD("provenance.metadata.v1.EventScopeDeleted", "scope_addr"),
    AA("provenance.attribute.v1.EventAttributeAdd", "account"),
    AU("provenance.attribute.v1.EventAttributeUpdate", "account"),
    AD("provenance.attribute.v1.EventAttributeDelete", "account"),
    ADD("provenance.attribute.v1.EventAttributeDistinctDelete", "account"),
    SEC("provenance.metadata.v1.EventSessionCreated", "scope_addr"),
    SEU("provenance.metadata.v1.EventSessionUpdated", "scope_addr"),
    SED("provenance.metadata.v1.EventSessionDeleted", "scope_addr"),
    RC("provenance.metadata.v1.EventRecordCreated", "scope_addr"),
    RU("provenance.metadata.v1.EventRecordUpdated", "scope_addr"),
    RD("provenance.metadata.v1.EventRecordDeleted", "scope_addr"),

    // Scope Spec
    SSC("provenance.metadata.v1.EventScopeSpecificationCreated", "scope_specification_addr"),
    SSU("provenance.metadata.v1.EventScopeSpecificationUpdated", "scope_specification_addr"),
    SSD("provenance.metadata.v1.EventScopeSpecificationDeleted", "scope_specification_addr")
}

fun Any.isMetadataDeletionMsg() =
    when {
        typeUrl.endsWith("MsgDeleteScopeRequest") ||
            typeUrl.endsWith("MsgDeleteScopeSpecificationRequest")
            || typeUrl.endsWith("MsgDeleteContractSpecificationRequest") -> true
        else -> false
    }

fun Any.getAssociatedMetadataEvents() =
    when {
        typeUrl.endsWith("MsgWriteP8eContractSpecRequest") -> listOf(CSPC, CSPU)
        typeUrl.endsWith("v1beta1.MsgExecuteContract")
            || typeUrl.endsWith("v1.MsgExecuteContract") -> listOf(
            AA, AU, AD, ADD, SC, SU, SD, SEC, SEU, SED, RC,
            RU, RD, CSPC, CSPU, CSPD, CRSC, CRSU, CRSD, SSC, SSU, SSD
        )
        else -> listOf<MdEvents>()
            .also { logger().debug("This typeUrl is not yet supported in as an metadata-event-based msg: $typeUrl") }
    }

fun MetadataAddress.toList() = listOf(this)

fun Any.getAssociatedMetadata(): List<MetadataAddress?> =
    when {
        typeUrl.endsWith("MsgWriteScopeRequest") -> this.toMsgWriteScopeRequest()
            .let {
                (it.scopeUuid?.blankToNull()?.toMAddressScope() ?: it.scope.scopeId?.toMAddress())?.toList() ?: listOf(
                    null
                )
            }
        typeUrl.endsWith("MsgDeleteScopeRequest") -> this.toMsgDeleteScopeRequest().scopeId.toMAddress().toList()
        typeUrl.endsWith("MsgAddScopeDataAccessRequest") -> this.toMsgAddScopeDataAccessRequest().scopeId.toMAddress()
            .toList()
        typeUrl.endsWith("MsgDeleteScopeDataAccessRequest") -> this.toMsgDeleteScopeDataAccessRequest().scopeId.toMAddress()
            .toList()
        typeUrl.endsWith("MsgAddScopeOwnerRequest") -> this.toMsgAddScopeOwnerRequest().scopeId.toMAddress().toList()
        typeUrl.endsWith("MsgDeleteScopeOwnerRequest") -> this.toMsgDeleteScopeOwnerRequest().scopeId.toMAddress()
            .toList()
        typeUrl.endsWith("MsgWriteSessionRequest") -> this.toMsgWriteSessionRequest()
            .let { it.sessionIdComponents.toMAddress() ?: it.session.sessionId.toMAddress() }.toList()
        typeUrl.endsWith("MsgWriteRecordRequest") -> this.toMsgWriteRecordRequest()
            .let { it.sessionIdComponents.toMAddress() ?: it.record.sessionId.toMAddress() }.toList()
        typeUrl.endsWith("MsgDeleteRecordRequest") -> this.toMsgDeleteRecordRequest().recordId.toMAddress().toList()
        typeUrl.endsWith("MsgWriteScopeSpecificationRequest") -> this.toMsgWriteScopeSpecificationRequest()
            .let {
                (
                    it.specUuid?.blankToNull()?.toMAddressScopeSpec()
                        ?: it.specification.specificationId?.toMAddress()
                    )?.toList() ?: listOf(null)
            }
        typeUrl.endsWith("MsgDeleteScopeSpecificationRequest") ->
            this.toMsgDeleteScopeSpecificationRequest().specificationId.toMAddress().toList()
        typeUrl.endsWith("MsgWriteContractSpecificationRequest") -> this.toMsgWriteContractSpecificationRequest()
            .let {
                (
                    it.specUuid?.blankToNull()?.toMAddressContractSpec()
                        ?: it.specification.specificationId?.toMAddress()
                    )?.toList() ?: listOf(null)
            }
        typeUrl.endsWith("MsgDeleteContractSpecificationRequest") ->
            this.toMsgDeleteContractSpecificationRequest().specificationId.toMAddress().toList()
        typeUrl.endsWith("MsgWriteRecordSpecificationRequest") -> this.toMsgWriteRecordSpecificationRequest()
            .let {
                (
                    it.contractSpecUuid?.blankToNull()?.toMAddressContractSpec()
                        ?: it.specification.specificationId?.toMAddress()
                    )?.toList() ?: listOf(null)
            }
        typeUrl.endsWith("MsgDeleteRecordSpecificationRequest") ->
            this.toMsgDeleteRecordSpecificationRequest().specificationId.toMAddress().toList()
        typeUrl.endsWith("MsgP8eMemorializeContractRequest") -> this.toMsgP8eMemorializeContractRequest().scopeId.toMAddressScope()
            .toList()
        typeUrl.endsWith("MsgAddContractSpecToScopeSpecRequest") ->
            this.toMsgAddContractSpecToScopeSpecRequest()
                .let { listOf(it.contractSpecificationId.toMAddress(), it.scopeSpecificationId.toMAddress()) }
        typeUrl.endsWith("MsgDeleteContractSpecFromScopeSpecRequest") ->
            this.toMsgDeleteContractSpecFromScopeSpecRequest()
                .let { listOf(it.contractSpecificationId.toMAddress(), it.scopeSpecificationId.toMAddress()) }
        typeUrl.endsWith("MsgAddAttributeRequest") -> this.toMsgAddAttributeRequest()
            .let { if (it.account.isMAddress()) it.account.toMAddress().toList() else listOf(null) }
        typeUrl.endsWith("MsgDeleteAttributeRequest") -> this.toMsgDeleteAttributeRequest()
            .let { if (it.account.isMAddress()) it.account.toMAddress().toList() else listOf(null) }
        typeUrl.endsWith("MsgUpdateAttributeRequest") -> this.toMsgUpdateAttributeRequest()
            .let { if (it.account.isMAddress()) it.account.toMAddress().toList() else listOf(null) }
        typeUrl.endsWith("MsgDeleteDistinctAttributeRequest") -> this.toMsgDeleteDistinctAttributeRequest()
            .let { if (it.account.isMAddress()) it.account.toMAddress().toList() else listOf(null) }
        typeUrl.endsWith("authz.v1beta1.MsgExec") -> this.toMsgExec().msgsList.flatMap { it.getAssociatedMetadata() }
        else -> listOf(null)
            .also { logger().debug("This typeUrl is not yet supported in as an metadata-based msg: $typeUrl") }
    }

fun SessionIdComponents?.toMAddress() =
    if (this != null &&
        this != SessionIdComponents.getDefaultInstance() &&
        this.sessionUuid != null &&
        (this.scopeUuid != null || this.scopeAddr != null)
    ) {
        val scope = this.scopeUuid.toUuidOrNull() ?: this.scopeAddr.toMAddress().getPrimaryUuid()
        this.sessionUuid.toMAddressSession(scope)
    } else {
        null
    }

//endregion

//region GOVERNANCE
fun Any.toSoftwareUpgradeProposal() = this.unpack(SoftwareUpgradeProposal::class.java)

enum class GovMsgType { PROPOSAL, VOTE, WEIGHTED, DEPOSIT }

fun Any.getAssociatedGovMsgs(): List<Pair<GovMsgType, Any>>? =
    when {
        typeUrl.endsWith("gov.v1beta1.MsgSubmitProposal")
            || typeUrl.endsWith("gov.v1.MsgSubmitProposal") -> listOf(GovMsgType.PROPOSAL to this)
        typeUrl.endsWith("gov.v1beta1.MsgVote")
            || typeUrl.endsWith("gov.v1.MsgVote") -> listOf(GovMsgType.VOTE to this)
        typeUrl.endsWith("gov.v1beta1.MsgVoteWeighted")
            || typeUrl.endsWith("gov.v1.MsgVoteWeighted") -> listOf(GovMsgType.WEIGHTED to this)
        typeUrl.endsWith("gov.v1beta1.MsgDeposit")
            || typeUrl.endsWith("gov.v1.MsgDeposit") -> listOf(GovMsgType.DEPOSIT to this)
        typeUrl.endsWith("authz.v1beta1.MsgExec") -> this.toMsgExec().msgsList.mapNotNull { it.getAssociatedGovMsgs() }.flatten()
        else -> null.also { logger().debug("This typeUrl is not a governance-based msg: $typeUrl") }
    }

//endregion

//region SMART CONTRACTS
fun Any.getAssociatedSmContractMsgs(): List<Pair<SmContractValue, kotlin.Any>>? =
    when {
        typeUrl.endsWith("v1.MsgStoreCode") -> null
        typeUrl.endsWith("v1beta1.MsgStoreCode") -> null
        typeUrl.endsWith("v1.MsgInstantiateContract") -> this.toMsgInstantiateContract()
            .let { listOf(SmContractValue.CODE to it.codeId) }
        typeUrl.endsWith("v1.MsgInstantiateContract2") -> this.toMsgInstantiateContract2()
            .let { listOf(SmContractValue.CODE to it.codeId) }
        typeUrl.endsWith("v1beta1.MsgInstantiateContract") -> this.toMsgInstantiateContractOld()
            .let { listOf(SmContractValue.CODE to it.codeId) }
        typeUrl.endsWith("v1.MsgExecuteContract") -> this.toMsgExecuteContract()
            .let { listOf(SmContractValue.CONTRACT to it.contract) }
        typeUrl.endsWith("v1beta1.MsgExecuteContract") -> this.toMsgExecuteContractOld()
            .let { listOf(SmContractValue.CONTRACT to it.contract) }
        typeUrl.endsWith("v1.MsgMigrateContract") -> this.toMsgMigrateContract()
            .let { listOf(SmContractValue.CODE to it.codeId, SmContractValue.CONTRACT to it.contract) }
        typeUrl.endsWith("v1beta1.MsgMigrateContract") -> this.toMsgMigrateContractOld()
            .let { listOf(SmContractValue.CODE to it.codeId, SmContractValue.CONTRACT to it.contract) }
        typeUrl.endsWith("v1.MsgUpdateAdmin") -> this.toMsgUpdateAdmin()
            .let { listOf(SmContractValue.CONTRACT to it.contract) }
        typeUrl.endsWith("v1beta1.MsgUpdateAdmin") -> this.toMsgUpdateAdminOld()
            .let { listOf(SmContractValue.CONTRACT to it.contract) }
        typeUrl.endsWith("v1.MsgClearAdmin") -> this.toMsgClearAdmin()
            .let { listOf(SmContractValue.CONTRACT to it.contract) }
        typeUrl.endsWith("v1beta1.MsgClearAdmin") -> this.toMsgClearAdminOld()
            .let { listOf(SmContractValue.CONTRACT to it.contract) }
        typeUrl.endsWith("authz.v1beta1.MsgExec") -> this.toMsgExec().msgsList.mapNotNull { it.getAssociatedSmContractMsgs() }
            .flatten()

        else -> null.also { logger().debug("This typeUrl is not a smart-contract-based msg: $typeUrl") }
    }

enum class SmContractValue { CODE, CONTRACT }

enum class SmContractEventKeys(val eventType: String, val eventKey: Map<String, SmContractValue>) {
    V1BETA1("message", mapOf("code_id" to SmContractValue.CODE, "contract_address" to SmContractValue.CONTRACT)),
    STORE_CODE_V1("store_code", mapOf("code_id" to SmContractValue.CODE)),
    INSTANTIATE_V1(
        "instantiate",
        mapOf("_contract_address" to SmContractValue.CONTRACT, "code_id" to SmContractValue.CODE)
    ),
    EXECUTE_V1("execute", mapOf("_contract_address" to SmContractValue.CONTRACT))
}

fun getSmContractEventByEvent(event: String) = SmContractEventKeys.values().firstOrNull { it.eventType == event }

//endregion

//region NAMES

enum class NameEvents(val msg: String, val event: String) {
    NAME_BIND("/provenance.name.v1.MsgBindNameRequest", "provenance.name.v1.EventNameBound"),
    NAME_DELETE("/provenance.name.v1.MsgDeleteNameRequest", "provenance.name.v1.EventNameUnbound")
}

fun getNameMsgTypes() = NameEvents.values().map { it.msg }

fun getNameEventTypes() = NameEvents.values().map { it.event }

fun Any.getNameMsgs() =
    when {
        getNameMsgTypes().contains(typeUrl) -> this
        else -> null.also { logger().debug("This typeUrl is not yet supported in as a Name msg: $typeUrl") }
    }

//endregion

//region GROUPS

fun Any.getAssociatedGroups() =
    when {
        typeUrl.endsWith("group.v1.MsgUpdateGroupMembers") -> this.toMsgUpdateGroupMembers().groupId
        typeUrl.endsWith("group.v1.MsgUpdateGroupAdmin") -> this.toMsgUpdateGroupAdmin().groupId
        typeUrl.endsWith("group.v1.MsgUpdateGroupMetadata") -> this.toMsgUpdateGroupMetadata().groupId
        typeUrl.endsWith("group.v1.MsgCreateGroupPolicy") -> this.toMsgCreateGroupPolicy().groupId
        typeUrl.endsWith("group.v1.MsgLeaveGroup") -> this.toMsgLeaveGroup().groupId
        else -> null.also { logger().debug("This typeUrl is not a group-based msg: $typeUrl") }
    }

enum class GroupEvents(val event: String, vararg val idField: String) {
    GROUP_CREATE("cosmos.group.v1.EventCreateGroup", "group_id"),
    GROUP_UPDATE("cosmos.group.v1.EventUpdateGroup", "group_id"),
    GROUP_LEAVE("cosmos.group.v1.EventLeaveGroup", "group_id")
}

fun getGroupEventByEvent(event: String) = GroupEvents.values().firstOrNull { it.event == event }

fun Any.getAssociatedGroupPolicies() =
    when {
        typeUrl.endsWith("group.v1.MsgUpdateGroupPolicyAdmin") -> this.toMsgUpdateGroupPolicyAdmin().groupPolicyAddress
        typeUrl.endsWith("group.v1.MsgUpdateGroupPolicyDecisionPolicy") -> this.toMsgUpdateGroupPolicyDecisionPolicy().groupPolicyAddress
        typeUrl.endsWith("group.v1.MsgUpdateGroupPolicyMetadata") -> this.toMsgUpdateGroupPolicyMetadata().groupPolicyAddress
        else -> null.also { logger().debug("This typeUrl is not a group-policy-based msg: $typeUrl") }
    }

enum class GroupPolicyEvents(val event: String, vararg val idField: String) {
    GROUP_POLICY_CREATE("cosmos.group.v1.EventCreateGroupPolicy", "address"),
    GROUP_POLICY_UPDATE("cosmos.group.v1.EventUpdateGroupPolicy", "address")
}

fun getGroupPolicyEventByEvent(event: String) = GroupPolicyEvents.values().firstOrNull { it.event == event }

enum class GroupGovMsgType { PROPOSAL, VOTE, EXEC, WITHDRAW }

fun Any.getAssociatedGroupProposals() =
    when {
        typeUrl.endsWith("group.v1.MsgSubmitProposal") -> GroupGovMsgType.PROPOSAL to this
        typeUrl.endsWith("group.v1.MsgVote") -> GroupGovMsgType.VOTE to this
        typeUrl.endsWith("group.v1.MsgExec") -> GroupGovMsgType.EXEC to this
        typeUrl.endsWith("group.v1.MsgWithdrawProposal") -> GroupGovMsgType.WITHDRAW to this
        else -> null.also { logger().debug("This typeUrl is not a group-governance-based msg: $typeUrl") }
    }

enum class GroupProposalEvents(val event: String, vararg val idField: String) {
    GROUP_SUBMIT_PROPOSAL("cosmos.group.v1.EventSubmitProposal", "proposal_id"),
    GROUP_VOTE("cosmos.group.v1.EventVote", "proposal_id"),
    GROUP_EXEC("cosmos.group.v1.EventExec", "proposal_id"),
    GROUP_WITHDRAW_PROPOSAL("cosmos.group.v1.EventWithdrawProposal", "proposal_id")
}

fun getGroupProposalEventByEvent(event: String) = GroupProposalEvents.values().firstOrNull { it.event == event }

fun String.getGroupsProposalStatus() = ProposalStatus.valueOf(this)
fun String.getGroupsExecutorResult() = ProposalExecutorResult.valueOf(this)

//endregion

//region DENOM EVENTS
enum class DenomEvents(val event: String, val idField: String, val parse: Boolean = false) {
    TRANSFER("transfer", "amount", true),
    MARKER_TRANSFER("provenance.marker.v1.EventMarkerTransfer", "denom"),
    MARKER_ADD("provenance.marker.v1.EventMarkerAdd", "denom"),
    MARKER_MINT("provenance.marker.v1.EventMarkerMint", "denom"),
    MARKER_WITHDRAW("provenance.marker.v1.EventMarkerWithdraw", "denom"),
    MARKER_ACTIVATE("provenance.marker.v1.EventMarkerActivate", "denom"),
    MARKER_ADD_ACCESS("provenance.marker.v1.EventMarkerAddAccess", "denom"),
    MARKER_DELETE_ACCESS("provenance.marker.v1.EventMarkerDeleteAccess", "denom"),
    MARKER_FINALIZE("provenance.marker.v1.EventMarkerFinalize", "denom"),
    MARKER_CANCEL("provenance.marker.v1.EventMarkerCancel", "denom"),
    MARKER_BURN("provenance.marker.v1.EventMarkerBurn", "denom"),
    MARKER_DELETE("provenance.marker.v1.EventMarkerDelete", "denom"),
    IBC_ACKNOWLEDGE("fungible_token_packet", "denom"),
    IBC_RECV_PACKET("denomination_trace", "denom")
}

fun getDenomEventByEvent(event: String) = DenomEvents.values().firstOrNull { it.event == event }

fun String.denomEventRegexParse() =
    if (this.isNotBlank()) {
        this.split(",").map { it.denomAmountToPair().second }
    } else {
        emptyList()
    }

fun String.denomAmountToPair() =
    if (this.isNotBlank()) {
        Regex("^([0-9]+)(.*)\$").matchEntire(this)!!.let { it.groups[1]!!.value to it.groups[2]!!.value }
    } else {
        Pair("", "")
    }

//endregion

//region ADDRESS EVENTS
enum class AddressEvents(val event: String, vararg val idField: String) {
    TRANSFER("transfer", "sender", "recipient"),
    MARKER_TRANSFER("provenance.marker.v1.EventMarkerTransfer", "administrator", "to_address", "from_address"),
    MARKER_ADD("provenance.marker.v1.EventMarkerAdd", "manager"),
    MARKER_MINT("provenance.marker.v1.EventMarkerMint", "administrator"),
    MARKER_WITHDRAW("provenance.marker.v1.EventMarkerWithdraw", "administrator", "to_address"),
    MARKER_ACTIVATE("provenance.marker.v1.EventMarkerActivate", "administrator"),
    MARKER_ADD_ACCESS("provenance.marker.v1.EventMarkerAddAccess", "administrator"),
    MARKER_DELETE_ACCESS("provenance.marker.v1.EventMarkerDeleteAccess", "administrator", "remove_address"),
    MARKER_FINALIZE("provenance.marker.v1.EventMarkerFinalize", "administrator"),
    MARKER_CANCEL("provenance.marker.v1.EventMarkerCancel", "administrator"),
    MARKER_BURN("provenance.marker.v1.EventMarkerBurn", "administrator"),
    MARKER_DELETE("provenance.marker.v1.EventMarkerDelete", "administrator"),
    IBC_RECV_PACKET("fungible_token_packet", "receiver"),
    NAME_BOUND("provenance.name.v1.EventNameBound", "address"),
    NAME_BOUND_OLD("name_bound", "address"),
    GRANT("cosmos.authz.v1beta1.EventGrant", "granter", "grantee"),
    REVOKE("cosmos.authz.v1beta1.EventRevoke", "granter", "grantee"),
    ATTRIBUTE_ADD("provenance.attribute.v1.EventAttributeAdd", "account", "owner"),
    ATTRIBUTE_UPDATE("provenance.attribute.v1.EventAttributeUpdate", "account", "owner"),
    ATTRIBUTE_DELETE("provenance.attribute.v1.EventAttributeDelete", "account", "owner"),
    ATTRIBUTE_DISTINCT_DELETE("provenance.attribute.v1.EventAttributeDistinctDelete", "account", "owner"),
    GROUP_POLICY_CREATE("cosmos.group.v1.EventCreateGroupPolicy", "address"),
    GROUP_POLICY_UPDATE("cosmos.group.v1.EventUpdateGroupPolicy", "address"),
    GROUP_LEAVE("cosmos.group.v1.EventLeaveGroup", "address")
}

fun getAddressEventByEvent(event: String) = AddressEvents.values().firstOrNull { it.event == event }

fun String.scrubQuotes() = this.removeSurrounding("\"")

//endregion

//region MSG TO DEFINED EVENT
// This links a msg type to a specific event it always emits. Helps to identify actions within an ExecuteContract msg
// When this is updated, update update_tx_fees() and update_market_rate() procedures as well
// THIS IS FOR OLDER TXS WHEN MSG FEES WERE FIRST INTRODUCED. SHOULD NOT BE USED FOR FUTURE CASES
enum class MsgToDefinedEvent(val msg: String, val definedEvent: String, val uniqueField: String) {
    ATTRIBUTE_ADD(
        "/provenance.attribute.v1.MsgAddAttributeRequest",
        "provenance.attribute.v1.EventAttributeAdd",
        "account"
    ),
    SCOPE_WRITE(
        "/provenance.metadata.v1.MsgWriteScopeRequest",
        "provenance.metadata.v1.EventScopeCreated",
        "scope_addr"
    ),
    NAME_BIND("/provenance.name.v1.MsgBindNameRequest", "provenance.name.v1.EventNameBound", "address"),
    PROPOSAL_SUBMIT_V1BETA1("/cosmos.gov.v1beta1.MsgSubmitProposal", "submit_proposal", "proposal_id"),
    MARKER_ADD("/provenance.marker.v1.MsgAddMarkerRequest", "provenance.marker.v1.EventMarkerAdd", "denom")
}

fun getDefinedEventsByMsg(msg: String) = MsgToDefinedEvent.values().firstOrNull { it.msg == msg }
fun getDefinedEventsByEvent(event: String) = MsgToDefinedEvent.values().firstOrNull { it.definedEvent == event }
fun getByDefinedEvent() = MsgToDefinedEvent.values().associateBy { it.definedEvent }

fun getContractTypeUrlList() =
    setOf(msgExecuteContract { }.getType(), msgInstantiateContract { }.getType(), msgMigrateContract { }.getType())

//endregion

//region MSG TO SUB MSGS
fun String.toShortType() = this.split("Msg")[1].removeSuffix("Request")

fun String.getMsgType(): MsgProtoBreakout {
    val module = if (!this.startsWith("/ibc")) {
        this.split(".")[1]
    } else {
        this.split(".").let { list -> "${list[0].drop(1)}_${list[2]}" }
    }
    val type = this.toShortType().let {
        if (this.startsWith("/cosmos.group.v1") && !this.contains("Group")) "${it}ForGroup" else it // handles
    }.toSnakeCase(universalWordSplitter(false))
    return MsgProtoBreakout(this, module, type)
}

fun Any.getMsgSubTypes(): List<String?> =
    when {
        typeUrl.endsWith("authz.v1beta1.MsgExec") -> this.toMsgExec().msgsList.mapNotNull { it.typeUrl }
        typeUrl.endsWith("MsgGrant") -> listOf(this.toMsgGrant().grant.authorization.getGrantAuthType())
        typeUrl.endsWith("MsgRevoke") -> listOf(this.toMsgRevoke().msgTypeUrl)
        else -> listOf<String?>().also { logger().debug("This typeUrl is not a smart-contract-based msg: $typeUrl") }
    }

fun Any.getGrantAuthType() =
    when {
        typeUrl.endsWith("GenericAuthorization") -> this.unpack(GenericAuthorization::class.java).msg
        typeUrl.endsWith("CountAuthorization") -> this.unpack(CountAuthorization::class.java).msg
        typeUrl.endsWith("v1beta1.SendAuthorization") -> msgSend { }.getType()
        typeUrl.endsWith("v1beta1.StakeAuthorization") ->
            this.unpack(cosmos.staking.v1beta1.Authz.StakeAuthorization::class.java).authorizationType.let {
                when (it) {
                    AuthorizationType.AUTHORIZATION_TYPE_DELEGATE -> msgDelegate { }.getType()
                    AuthorizationType.AUTHORIZATION_TYPE_UNDELEGATE -> msgUndelegate { }.getType()
                    AuthorizationType.AUTHORIZATION_TYPE_REDELEGATE -> msgBeginRedelegate { }.getType()
                    else -> null
                }
            }
        typeUrl.endsWith("v1.MarkerTransferAuthorization") -> msgTransferRequest { }.getType()
        else -> null.also { logger().debug("This typeUrl is not known to have a msg subtype: $typeUrl") }
    }

//endregion
