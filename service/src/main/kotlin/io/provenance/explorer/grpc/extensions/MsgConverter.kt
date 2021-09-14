package io.provenance.explorer.grpc.extensions

import com.google.protobuf.Any
import cosmos.bank.v1beta1.Tx
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
import io.provenance.explorer.domain.core.MetadataAddress
import io.provenance.explorer.domain.core.blankToNull
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.toMAddress
import io.provenance.explorer.domain.core.toMAddressContractSpec
import io.provenance.explorer.domain.core.toMAddressScope
import io.provenance.explorer.domain.core.toMAddressScopeSpec
import io.provenance.explorer.domain.core.toMAddressSession
import io.provenance.explorer.domain.core.toUuid
import io.provenance.explorer.grpc.extensions.MdEvents.CSPC
import io.provenance.explorer.grpc.extensions.MdEvents.CSPU
import io.provenance.explorer.service.getDenomByAddress
import io.provenance.marker.v1.MsgActivateRequest
import io.provenance.marker.v1.MsgAddAccessRequest
import io.provenance.marker.v1.MsgAddMarkerRequest
import io.provenance.marker.v1.MsgBurnRequest
import io.provenance.marker.v1.MsgCancelRequest
import io.provenance.marker.v1.MsgDeleteAccessRequest
import io.provenance.marker.v1.MsgDeleteRequest
import io.provenance.marker.v1.MsgFinalizeRequest
import io.provenance.marker.v1.MsgMintRequest
import io.provenance.marker.v1.MsgSetDenomMetadataRequest
import io.provenance.marker.v1.MsgTransferRequest
import io.provenance.marker.v1.MsgWithdrawRequest
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

/**
 * Ginormous file meant to convert a Msg object to the proper format, and do stuff with it.
 */

fun Any.toMsgSend() = this.unpack(Tx.MsgSend::class.java)
fun Any.toMsgMultiSend() = this.unpack(Tx.MsgMultiSend::class.java)
fun Any.toMsgSubmitProposal() = this.unpack(cosmos.gov.v1beta1.Tx.MsgSubmitProposal::class.java)
fun Any.toMsgVote() = this.unpack(cosmos.gov.v1beta1.Tx.MsgVote::class.java)
fun Any.toMsgVoteWeighted() = this.unpack(cosmos.gov.v1beta1.Tx.MsgVoteWeighted::class.java)
fun Any.toMsgDeposit() = this.unpack(cosmos.gov.v1beta1.Tx.MsgDeposit::class.java)
fun Any.toMsgSetWithdrawAddress() = this.unpack(cosmos.distribution.v1beta1.Tx.MsgSetWithdrawAddress::class.java)
fun Any.toMsgWithdrawDelegatorReward() = this.unpack(cosmos.distribution.v1beta1.Tx.MsgWithdrawDelegatorReward::class.java)
fun Any.toMsgWithdrawValidatorCommission() = this.unpack(cosmos.distribution.v1beta1.Tx.MsgWithdrawValidatorCommission::class.java)
fun Any.toMsgFundCommunityPool() = this.unpack(cosmos.distribution.v1beta1.Tx.MsgFundCommunityPool::class.java)
fun Any.toMsgSubmitEvidence() = this.unpack(cosmos.evidence.v1beta1.Tx.MsgSubmitEvidence::class.java)
fun Any.toMsgUnjail() = this.unpack(cosmos.slashing.v1beta1.Tx.MsgUnjail::class.java)
fun Any.toMsgCreateValidator() = this.unpack(cosmos.staking.v1beta1.Tx.MsgCreateValidator::class.java)
fun Any.toMsgEditValidator() = this.unpack(cosmos.staking.v1beta1.Tx.MsgEditValidator::class.java)
fun Any.toMsgDelegate() = this.unpack(cosmos.staking.v1beta1.Tx.MsgDelegate::class.java)
fun Any.toMsgBeginRedelegate() = this.unpack(cosmos.staking.v1beta1.Tx.MsgBeginRedelegate::class.java)
fun Any.toMsgUndelegate() = this.unpack(cosmos.staking.v1beta1.Tx.MsgUndelegate::class.java)
fun Any.toMsgCreateVestingAccount() = this.unpack(cosmos.vesting.v1beta1.Tx.MsgCreateVestingAccount::class.java)
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
fun Any.toMsgAddScopeDataAccessRequest() = this.unpack(MsgAddScopeDataAccessRequest::class.java)
fun Any.toMsgDeleteScopeDataAccessRequest() = this.unpack(MsgDeleteScopeDataAccessRequest::class.java)
fun Any.toMsgAddScopeOwnerRequest() = this.unpack(MsgAddScopeOwnerRequest::class.java)
fun Any.toMsgDeleteScopeOwnerRequest() = this.unpack(MsgDeleteScopeOwnerRequest::class.java)
fun Any.toMsgUpdateAttributeRequest() = this.unpack(MsgUpdateAttributeRequest::class.java)
fun Any.toMsgDeleteDistinctAttributeRequest() = this.unpack(MsgDeleteDistinctAttributeRequest::class.java)
fun Any.toMsgAddContractSpecToScopeSpecRequest() = this.unpack(MsgAddContractSpecToScopeSpecRequest::class.java)
fun Any.toMsgDeleteContractSpecFromScopeSpecRequest() = this.unpack(MsgDeleteContractSpecFromScopeSpecRequest::class.java)
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
fun Any.toMsgGrant() = this.unpack(cosmos.authz.v1beta1.Tx.MsgGrant::class.java)
fun Any.toMsgExec() = this.unpack(cosmos.authz.v1beta1.Tx.MsgExec::class.java)
fun Any.toMsgRevoke() = this.unpack(cosmos.authz.v1beta1.Tx.MsgRevoke::class.java)
fun Any.toMsgGrantAllowance() = this.unpack(cosmos.feegrant.v1beta1.Tx.MsgGrantAllowance::class.java)
fun Any.toMsgRevokeAllowance() = this.unpack(cosmos.feegrant.v1beta1.Tx.MsgRevokeAllowance::class.java)

// ///////// ADDRESSES
fun Any.getAssociatedAddresses(): List<String> =
    when {
        typeUrl.endsWith("MsgSend") -> this.toMsgSend().let { listOf(it.fromAddress, it.toAddress) }
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
        typeUrl.endsWith("MsgSubmitProposal") -> this.toMsgSubmitProposal().let { listOf(it.proposer) }
        typeUrl.endsWith("MsgVote") -> this.toMsgVote().let { listOf(it.voter) }
        typeUrl.endsWith("MsgVoteWeighted") -> this.toMsgVoteWeighted().let { listOf(it.voter) }
        typeUrl.endsWith("MsgDeposit") -> this.toMsgDeposit().let { listOf(it.depositor) }
        typeUrl.endsWith("MsgUnjail") -> this.toMsgUnjail().let { listOf(it.validatorAddr) }
        typeUrl.endsWith("MsgCreateValidator") -> this.toMsgCreateValidator()
            .let { listOf(it.validatorAddress, it.delegatorAddress) }
        typeUrl.endsWith("MsgEditValidator") -> this.toMsgEditValidator().let { listOf(it.validatorAddress) }
        typeUrl.endsWith("MsgDelegate") -> this.toMsgDelegate().let { listOf(it.validatorAddress, it.delegatorAddress) }
        typeUrl.endsWith("MsgBeginRedelegate") -> this.toMsgBeginRedelegate()
            .let { listOf(it.delegatorAddress, it.validatorDstAddress, it.validatorSrcAddress) }
        typeUrl.endsWith("MsgUndelegate") -> this.toMsgUndelegate()
            .let { listOf(it.delegatorAddress, it.validatorAddress) }
        typeUrl.endsWith("MsgCreateVestingAccount") -> this.toMsgCreateVestingAccount()
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
        typeUrl.endsWith("MsgSetDenomMetadataRequest") -> this.toMsgSetDenomMetadataRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgBindNameRequest") -> this.toMsgBindNameRequest().let { listOf(it.parent.address, it.record.address) }
        typeUrl.endsWith("MsgDeleteNameRequest") -> this.toMsgDeleteNameRequest().let { listOf(it.record.address) }
        typeUrl.endsWith("MsgAddAttributeRequest") -> this.toMsgAddAttributeRequest().let { listOf(it.account, it.owner) }
        typeUrl.endsWith("MsgDeleteAttributeRequest") -> this.toMsgDeleteAttributeRequest().let { listOf(it.account, it.owner) }
        typeUrl.endsWith("MsgP8eMemorializeContractRequest") -> this.toMsgP8eMemorializeContractRequest().let { listOf(it.invoker) }
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
        typeUrl.endsWith("MsgDeleteOSLocatorRequest") -> this.toMsgDeleteOSLocatorRequest().let { listOf(it.locator.owner) }
        typeUrl.endsWith("MsgModifyOSLocatorRequest") -> this.toMsgModifyOSLocatorRequest().let { listOf(it.locator.owner) }
        typeUrl.endsWith("MsgStoreCode") -> this.toMsgStoreCode().let { listOf(it.sender) }
        typeUrl.endsWith("MsgInstantiateContract") -> this.toMsgInstantiateContract().let { listOf(it.sender, it.admin) }
        typeUrl.endsWith("MsgExecuteContract") -> this.toMsgExecuteContract().let { listOf(it.sender) }
        typeUrl.endsWith("MsgMigrateContract") -> this.toMsgMigrateContract().let { listOf(it.sender) }
        typeUrl.endsWith("MsgUpdateAdmin") -> this.toMsgUpdateAdmin().let { listOf(it.sender, it.newAdmin) }
        typeUrl.endsWith("MsgClearAdmin") -> this.toMsgClearAdmin().let { listOf(it.sender) }
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
        typeUrl.endsWith("MsgGrant") -> this.toMsgGrant().let { listOf(it.granter, it.grantee) }
        typeUrl.endsWith("MsgExec") -> this.toMsgExec().let { listOf(it.grantee) }
        typeUrl.endsWith("MsgRevoke") -> this.toMsgRevoke().let { listOf(it.granter, it.grantee) }
        typeUrl.endsWith("MsgGrantAllowance") -> this.toMsgGrantAllowance().let { listOf(it.granter, it.grantee) }
        typeUrl.endsWith("MsgRevokeAllowance") -> this.toMsgRevokeAllowance().let { listOf(it.granter, it.grantee) }

        else -> listOf<String>().also { logger().debug("This typeUrl is not yet supported as an address-based msg: $typeUrl") }
    }

// ///////// DENOMS
fun Any.getAssociatedDenoms(): List<String> =
    when {
        typeUrl.endsWith("MsgSend") -> this.toMsgSend().let { it.amountList.map { am -> am.denom } }
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
        typeUrl.endsWith("MsgInstantiateContract") -> this.toMsgInstantiateContract().let { it.fundsList.map { c -> c.denom } }
        typeUrl.endsWith("MsgExecuteContract") -> this.toMsgExecuteContract().let { it.fundsList.map { c -> c.denom } }
        typeUrl.endsWith("MsgTransfer") -> this.toMsgTransfer().let { listOf(it.token.denom) }

        else -> listOf<String>()
            .also { logger().debug("This typeUrl is not yet supported as an asset-based msg: $typeUrl") }
    }

// ///////// IBC
fun Any.isIbcTransferMsg() = typeUrl.endsWith("MsgTransfer")

enum class IbcEventType { DENOM, ADDRESS }

enum class IbcDenomEvents(val eventType: IbcEventType, val event: String, val idField: String) {
    RECV_PACKET_DENOM(IbcEventType.DENOM, "denomination_trace", "denom"),
    RECV_PACKET_ADDR(IbcEventType.ADDRESS, "fungible_token_packet", "receiver"),
    ACKNOWLEDGE_DENOM(IbcEventType.DENOM, "fungible_token_packet", "denom")
}

fun Any.getIbcDenomEvents() =
    when {
        typeUrl.endsWith("MsgRecvPacket") -> listOf(IbcDenomEvents.RECV_PACKET_DENOM, IbcDenomEvents.RECV_PACKET_ADDR)
        typeUrl.endsWith("MsgAcknowledgement") -> listOf(IbcDenomEvents.ACKNOWLEDGE_DENOM)
        else -> listOf<IbcDenomEvents>()
            .also { logger().debug("This typeUrl is not yet supported in as an ibc denom event-based msg: $typeUrl") }
    }

// Returns Pair(Event type, Pair(port, channel))
fun Any.getIbcChannelEvents() =
    when {
        typeUrl.endsWith("MsgTransfer") -> "send_packet" to Pair("packet_src_port", "packet_src_channel")
        typeUrl.endsWith("MsgChannelOpenInit") -> "channel_open_init" to Pair("port_id", "channel_id")
        typeUrl.endsWith("MsgChannelOpenTry") -> "channel_open_try" to Pair("port_id", "channel_id")
        typeUrl.endsWith("MsgChannelOpenAck") -> "channel_open_ack" to Pair("port_id", "channel_id")
        typeUrl.endsWith("MsgChannelOpenConfirm") -> "channel_open_confirm" to Pair("port_id", "channel_id")
        typeUrl.endsWith("MsgChannelCloseInit") -> "channel_close_init" to Pair("port_id", "channel_id")
        typeUrl.endsWith("MsgChannelCloseConfirm") -> "channel_close_confirm" to Pair("port_id", "channel_id")
        typeUrl.endsWith("MsgRecvPacket") -> "recv_packet" to Pair("packet_dst_port", "packet_dst_channel")
        typeUrl.endsWith("MsgTimeout") -> "timeout_packet" to Pair("packet_src_port", "packet_src_channel")
        typeUrl.endsWith("MsgAcknowledgement") -> "acknowledge_packet" to Pair("packet_src_port", "packet_src_channel")
        else ->
            null.also { logger().debug("This typeUrl is not yet supported in as an ibc channel event-based msg: $typeUrl") }
    }

// The only case where a channel is not in the events, because no events get emitted
fun Any.isIbcTimeoutOnClose() = typeUrl.endsWith("MsgTimeoutOnClose")

fun Any.getIbcLedgerMsgs() =
    when {
        typeUrl.endsWith("MsgTransfer") ||
            typeUrl.endsWith("MsgRecvPacket")
            || typeUrl.endsWith("MsgAcknowledgement") -> this
        else -> null.also { logger().debug("This typeUrl is not yet supported in as an ibc ledger msg: $typeUrl") }
    }

// ///////// METADATA (NFT/SCOPES)
enum class MdEvents(val event: String, val idField: String) {
    // Contract Spec
    CSPC("provenance.metadata.v1.EventContractSpecificationCreated", "contract_specification_addr"),
    CSPU("provenance.metadata.v1.EventContractSpecificationUpdated", "contract_specification_addr")
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
        else -> listOf<MdEvents>()
            .also { logger().debug("This typeUrl is not yet supported in as an metadata-event-based msg: $typeUrl") }
    }

fun MetadataAddress.toList() = listOf(this)

fun Any.getAssociatedMetadata() =
    when {
        typeUrl.endsWith("MsgWriteScopeRequest") -> this.toMsgWriteScopeRequest()
            .let { (it.scopeUuid?.blankToNull()?.toMAddressScope() ?: it.scope.scopeId?.toMAddress())?.toList() ?: listOf(null) }
        typeUrl.endsWith("MsgDeleteScopeRequest") -> this.toMsgDeleteScopeRequest().scopeId.toMAddress().toList()
        typeUrl.endsWith("MsgAddScopeDataAccessRequest") -> this.toMsgAddScopeDataAccessRequest().scopeId.toMAddress().toList()
        typeUrl.endsWith("MsgDeleteScopeDataAccessRequest") -> this.toMsgDeleteScopeDataAccessRequest().scopeId.toMAddress().toList()
        typeUrl.endsWith("MsgAddScopeOwnerRequest") -> this.toMsgAddScopeOwnerRequest().scopeId.toMAddress().toList()
        typeUrl.endsWith("MsgDeleteScopeOwnerRequest") -> this.toMsgDeleteScopeOwnerRequest().scopeId.toMAddress().toList()
        typeUrl.endsWith("MsgWriteSessionRequest") -> this.toMsgWriteSessionRequest()
            .let { it.sessionIdComponents.toMAddress() ?: it.session.sessionId.toMAddress() }.toList()
        typeUrl.endsWith("MsgWriteRecordRequest") -> this.toMsgWriteRecordRequest()
            .let { it.sessionIdComponents.toMAddress() ?: it.record.sessionId.toMAddress() }.toList()
        typeUrl.endsWith("MsgDeleteRecordRequest") -> this.toMsgDeleteRecordRequest().recordId.toMAddress().toList()
        typeUrl.endsWith("MsgWriteScopeSpecificationRequest") -> this.toMsgWriteScopeSpecificationRequest()
            .let { (it.specUuid?.blankToNull()?.toMAddressScopeSpec() ?: it.specification.specificationId?.toMAddress())?.toList() ?: listOf(null) }
        typeUrl.endsWith("MsgDeleteScopeSpecificationRequest") ->
            this.toMsgDeleteScopeSpecificationRequest().specificationId.toMAddress().toList()
        typeUrl.endsWith("MsgWriteContractSpecificationRequest") -> this.toMsgWriteContractSpecificationRequest()
            .let { (it.specUuid?.blankToNull()?.toMAddressContractSpec() ?: it.specification.specificationId?.toMAddress())?.toList() ?: listOf(null) }
        typeUrl.endsWith("MsgDeleteContractSpecificationRequest") ->
            this.toMsgDeleteContractSpecificationRequest().specificationId.toMAddress().toList()
        typeUrl.endsWith("MsgWriteRecordSpecificationRequest") -> this.toMsgWriteRecordSpecificationRequest()
            .let { (it.contractSpecUuid?.blankToNull()?.toMAddressContractSpec() ?: it.specification.specificationId?.toMAddress())?.toList() ?: listOf(null) }
        typeUrl.endsWith("MsgDeleteRecordSpecificationRequest") ->
            this.toMsgDeleteRecordSpecificationRequest().specificationId.toMAddress().toList()
        typeUrl.endsWith("MsgP8eMemorializeContractRequest") -> this.toMsgP8eMemorializeContractRequest().scopeId.toMAddressScope().toList()
        typeUrl.endsWith("MsgAddContractSpecToScopeSpecRequest") ->
            this.toMsgAddContractSpecToScopeSpecRequest().let { listOf(it.contractSpecificationId.toMAddress(), it.scopeSpecificationId.toMAddress()) }
        typeUrl.endsWith("MsgDeleteContractSpecFromScopeSpecRequest") ->
            this.toMsgDeleteContractSpecFromScopeSpecRequest().let { listOf(it.contractSpecificationId.toMAddress(), it.scopeSpecificationId.toMAddress()) }
        else -> listOf(null)
            .also { logger().debug("This typeUrl is not yet supported in as an metadata-based msg: $typeUrl") }
    }

fun SessionIdComponents?.toMAddress() =
    if (this != null && this.sessionUuid != null && (this.scopeUuid != null || this.scopeAddr != null)) {
        val scope = this.scopeUuid.toUuid() ?: this.scopeAddr.toMAddress().getPrimaryUuid()
        this.sessionUuid.toMAddressSession(scope)
    } else null

// ///////// GOVERNANCE
enum class GovMsgType { PROPOSAL, VOTE, DEPOSIT }

fun Any.getAssociatedGovMsgs() =
    when {
        typeUrl.endsWith("MsgSubmitProposal") -> GovMsgType.PROPOSAL to this
        typeUrl.endsWith("MsgVote") -> GovMsgType.VOTE to this
        typeUrl.endsWith("MsgDeposit") -> GovMsgType.DEPOSIT to this
        else -> null.also { logger().debug("This typeUrl is not a governance-based msg: $typeUrl") }
    }
