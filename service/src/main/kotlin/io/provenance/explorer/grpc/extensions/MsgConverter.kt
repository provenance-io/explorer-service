package io.provenance.explorer.grpc.extensions

import com.google.protobuf.Any
import cosmos.bank.v1beta1.Tx
import io.provenance.attribute.v1.MsgAddAttributeRequest
import io.provenance.attribute.v1.MsgDeleteAttributeRequest
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
import io.provenance.metadata.v1.MsgAddScopeDataAccessRequest
import io.provenance.metadata.v1.MsgAddScopeOwnerRequest
import io.provenance.metadata.v1.MsgBindOSLocatorRequest
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
fun Any.toMsgStoreCode() = this.unpack(cosmwasm.wasm.v1beta1.Tx.MsgStoreCode::class.java)
fun Any.toMsgInstantiateContract() = this.unpack(cosmwasm.wasm.v1beta1.Tx.MsgInstantiateContract::class.java)
fun Any.toMsgExecuteContract() = this.unpack(cosmwasm.wasm.v1beta1.Tx.MsgExecuteContract::class.java)
fun Any.toMsgMigrateContract() = this.unpack(cosmwasm.wasm.v1beta1.Tx.MsgMigrateContract::class.java)
fun Any.toMsgUpdateAdmin() = this.unpack(cosmwasm.wasm.v1beta1.Tx.MsgUpdateAdmin::class.java)
fun Any.toMsgClearAdmin() = this.unpack(cosmwasm.wasm.v1beta1.Tx.MsgClearAdmin::class.java)
fun Any.toMsgAddScopeDataAccessRequest() = this.unpack(MsgAddScopeDataAccessRequest::class.java)
fun Any.toMsgDeleteScopeDataAccessRequest() = this.unpack(MsgDeleteScopeDataAccessRequest::class.java)
fun Any.toMsgAddScopeOwnerRequest() = this.unpack(MsgAddScopeOwnerRequest::class.java)
fun Any.toMsgDeleteScopeOwnerRequest() = this.unpack(MsgDeleteScopeOwnerRequest::class.java)

fun Any.getAssociatedAddresses(): List<String> =
    when {
        typeUrl.contains("MsgSend") -> this.toMsgSend().let { listOf(it.fromAddress, it.toAddress) }
        typeUrl.contains("MsgMultiSend") -> this.toMsgMultiSend()
            .let { it.inputsList.map { inp -> inp.address } + it.outputsList.map { out -> out.address } }
        typeUrl.contains("MsgSetWithdrawAddress") -> this.toMsgSetWithdrawAddress()
            .let { listOf(it.delegatorAddress, it.withdrawAddress) }
        typeUrl.contains("MsgWithdrawDelegatorReward") -> this.toMsgWithdrawDelegatorReward()
            .let { listOf(it.delegatorAddress, it.validatorAddress) }
        typeUrl.contains("MsgWithdrawValidatorCommission") -> this.toMsgWithdrawValidatorCommission()
            .let { listOf(it.validatorAddress) }
        typeUrl.contains("MsgFundCommunityPool") -> this.toMsgFundCommunityPool().let { listOf(it.depositor) }
        typeUrl.contains("MsgSubmitEvidence") -> this.toMsgSubmitEvidence().let { listOf(it.submitter) }
        typeUrl.contains("MsgSubmitProposal") -> this.toMsgSubmitProposal().let { listOf(it.proposer) }
        typeUrl.contains("MsgVote") -> this.toMsgVote().let { listOf(it.voter) }
        typeUrl.contains("MsgDeposit") -> this.toMsgDeposit().let { listOf(it.depositor) }
        typeUrl.contains("MsgUnjail") -> this.toMsgUnjail().let { listOf(it.validatorAddr) }
        typeUrl.contains("MsgCreateValidator") -> this.toMsgCreateValidator()
            .let { listOf(it.validatorAddress, it.delegatorAddress) }
        typeUrl.contains("MsgEditValidator") -> this.toMsgEditValidator().let { listOf(it.validatorAddress) }
        typeUrl.contains("MsgDelegate") -> this.toMsgDelegate().let { listOf(it.validatorAddress, it.delegatorAddress) }
        typeUrl.contains("MsgBeginRedelegate") -> this.toMsgBeginRedelegate()
            .let { listOf(it.delegatorAddress, it.validatorDstAddress, it.validatorSrcAddress) }
        typeUrl.contains("MsgUndelegate") -> this.toMsgUndelegate()
            .let { listOf(it.delegatorAddress, it.validatorAddress) }
        typeUrl.contains("MsgCreateVestingAccount") -> this.toMsgCreateVestingAccount()
            .let { listOf(it.fromAddress, it.toAddress) }
        typeUrl.contains("MsgWithdrawRequest") -> this.toMsgWithdrawRequest()
            .let { listOf(it.toAddress, it.administrator)}
        typeUrl.contains("MsgAddMarkerRequest") -> this.toMsgAddMarkerRequest()
            .let { listOf(it.fromAddress, it.manager) + it.accessListList.map { acc -> acc.address }}
        typeUrl.contains("MsgAddAccessRequest") -> this.toMsgAddAccessRequest()
            .let { it.accessList.map { l -> l.address } + it.administrator }
        typeUrl.contains("MsgDeleteAccessRequest") -> this.toMsgDeleteAccessRequest()
            .let { listOf(it.administrator, it.removedAddress)}
        typeUrl.contains("MsgFinalizeRequest") -> this.toMsgFinalizeRequest().let { listOf(it.administrator)}
        typeUrl.contains("MsgActivateRequest") -> this.toMsgActivateRequest().let { listOf(it.administrator)}
        typeUrl.contains("MsgCancelRequest") -> this.toMsgCancelRequest().let { listOf(it.administrator)}
        typeUrl.contains("MsgDeleteRequest") -> this.toMsgDeleteRequest().let { listOf(it.administrator)}
        typeUrl.contains("MsgMintRequest") -> this.toMsgMintRequest().let { listOf(it.administrator)}
        typeUrl.contains("MsgBurnRequest") -> this.toMsgBurnRequest().let { listOf(it.administrator)}
        typeUrl.contains("MsgTransferRequest") -> this.toMsgTransferRequest()
            .let { listOf(it.administrator, it.toAddress, it.fromAddress)}
        typeUrl.contains("MsgSetDenomMetadataRequest") -> this.toMsgSetDenomMetadataRequest().let { listOf(it.administrator)}
        typeUrl.contains("MsgBindNameRequest") -> this.toMsgBindNameRequest().let { listOf(it.parent.address, it.record.address)}
        typeUrl.contains("MsgDeleteNameRequest") -> this.toMsgDeleteNameRequest().let { listOf(it.record.address)}
        typeUrl.contains("MsgAddAttributeRequest") -> this.toMsgAddAttributeRequest().let { listOf(it.account, it.owner)}
        typeUrl.contains("MsgDeleteAttributeRequest") -> this.toMsgDeleteAttributeRequest().let { listOf(it.account, it.owner)}
        typeUrl.contains("MsgP8eMemorializeContractRequest") -> this.toMsgP8eMemorializeContractRequest().let { listOf(it.invoker) }
        typeUrl.contains("MsgWriteP8eContractSpecRequest") -> this.toMsgWriteP8eContractSpecRequest().signersList
        typeUrl.contains("MsgWriteScopeRequest") -> this.toMsgWriteScopeRequest()
            .let { it.signersList + it.scope.ownersList.map { o -> o.address } + listOf(it.scope.valueOwnerAddress) }
        typeUrl.contains("MsgDeleteScopeRequest") -> this.toMsgDeleteScopeRequest().signersList
        typeUrl.contains("MsgWriteSessionRequest") -> this.toMsgWriteSessionRequest()
            .let { it.signersList + it.session.partiesList.map { o -> o.address } }
        typeUrl.contains("MsgWriteRecordRequest") -> this.toMsgWriteRecordRequest()
            .let { it.signersList + it.partiesList.map { o -> o.address } }
        typeUrl.contains("MsgDeleteRecordRequest") -> this.toMsgDeleteRecordRequest().signersList
        typeUrl.contains("MsgWriteScopeSpecificationRequest") -> this.toMsgWriteScopeSpecificationRequest()
            .let { it.signersList + it.specification.ownerAddressesList }
        typeUrl.contains("MsgDeleteScopeSpecificationRequest") -> this.toMsgDeleteScopeSpecificationRequest().signersList
        typeUrl.contains("MsgWriteContractSpecificationRequest") -> this.toMsgWriteContractSpecificationRequest()
            .let { it.signersList + it.specification.ownerAddressesList }
        typeUrl.contains("MsgDeleteContractSpecificationRequest") -> this.toMsgDeleteContractSpecificationRequest().signersList
        typeUrl.contains("MsgWriteRecordSpecificationRequest") -> this.toMsgWriteRecordSpecificationRequest().signersList
        typeUrl.contains("MsgDeleteRecordSpecificationRequest") -> this.toMsgDeleteRecordSpecificationRequest().signersList
        typeUrl.contains("MsgBindOSLocatorRequest") -> this.toMsgBindOSLocatorRequest().let { listOf(it.locator.owner) }
        typeUrl.contains("MsgDeleteOSLocatorRequest") -> this.toMsgDeleteOSLocatorRequest().let { listOf(it.locator.owner) }
        typeUrl.contains("MsgModifyOSLocatorRequest") -> this.toMsgModifyOSLocatorRequest().let { listOf(it.locator.owner) }
        typeUrl.contains("MsgStoreCode") -> this.toMsgStoreCode().let { listOf(it.sender) }
        typeUrl.contains("MsgInstantiateContract") -> this.toMsgInstantiateContract().let { listOf(it.sender, it.admin) }
        typeUrl.contains("MsgExecuteContract") -> this.toMsgExecuteContract().let { listOf(it.sender) }
        typeUrl.contains("MsgMigrateContract") -> this.toMsgMigrateContract().let { listOf(it.sender) }
        typeUrl.contains("MsgUpdateAdmin") -> this.toMsgUpdateAdmin().let { listOf(it.sender, it.newAdmin) }
        typeUrl.contains("MsgClearAdmin") -> this.toMsgClearAdmin().let { listOf(it.sender) }
        typeUrl.contains("MsgAddScopeDataAccessRequest") -> this.toMsgAddScopeDataAccessRequest()
            .let { it.signersList + it.dataAccessList }
        typeUrl.contains("MsgDeleteScopeDataAccessRequest") -> this.toMsgDeleteScopeDataAccessRequest()
            .let { it.signersList + it.dataAccessList }
        typeUrl.contains("MsgAddScopeOwnerRequest") -> this.toMsgAddScopeOwnerRequest()
            .let { it.ownersList.map { o -> o.address }  + it.signersList }
        typeUrl.contains("MsgDeleteScopeOwnerRequest") -> this.toMsgDeleteScopeOwnerRequest()
            .let { it.ownersList + it.signersList }

        else -> listOf<String>().also { logger().debug("This typeUrl is not yet supported as an address-based msg: $typeUrl") }
    }

fun Any.getAssociatedDenoms(): List<String> =
    when {
        typeUrl.contains("MsgSend") -> this.toMsgSend().let { it.amountList.map { am -> am.denom } }
        typeUrl.contains("MsgMultiSend") -> this.toMsgMultiSend()
            .let { it.inputsList.flatMap { inp -> inp.coinsList.map { c -> c.denom } } +
                it.outputsList.flatMap { out -> out.coinsList.map { c -> c.denom } } }
        typeUrl.contains("MsgWithdrawRequest") -> this.toMsgWithdrawRequest().let { listOf(it.denom)}
        typeUrl.contains("MsgAddMarkerRequest") -> this.toMsgAddMarkerRequest().let { listOf(it.amount.denom) }
        typeUrl.contains("MsgAddAccessRequest") -> this.toMsgAddAccessRequest().let { listOf(it.denom) }
        typeUrl.contains("MsgDeleteAccessRequest") -> this.toMsgDeleteAccessRequest().let { listOf(it.denom)}
        typeUrl.contains("MsgFinalizeRequest") -> this.toMsgFinalizeRequest().let { listOf(it.denom)}
        typeUrl.contains("MsgActivateRequest") -> this.toMsgActivateRequest().let { listOf(it.denom)}
        typeUrl.contains("MsgCancelRequest") -> this.toMsgCancelRequest().let { listOf(it.denom)}
        typeUrl.contains("MsgDeleteRequest") -> this.toMsgDeleteRequest().let { listOf(it.denom)}
        typeUrl.contains("MsgMintRequest") -> this.toMsgMintRequest().let { listOf(it.amount.denom)}
        typeUrl.contains("MsgBurnRequest") -> this.toMsgBurnRequest().let { listOf(it.amount.denom)}
        typeUrl.contains("MsgTransferRequest") -> this.toMsgTransferRequest().let { listOf(it.amount.denom)}
        typeUrl.contains("MsgSetDenomMetadataRequest") -> this.toMsgSetDenomMetadataRequest()
            .let { listOf(it.metadata.base) }
        typeUrl.contains("MsgAddAttributeRequest") -> this.toMsgAddAttributeRequest()
            .let { it.account.getDenomByAddress()?.let { denom -> listOf(denom) } ?: listOf() }
        typeUrl.contains("MsgDeleteAttributeRequest") -> this.toMsgDeleteAttributeRequest()
            .let { it.account.getDenomByAddress()?.let { denom -> listOf(denom) } ?: listOf() }
        typeUrl.contains("MsgInstantiateContract") -> this.toMsgInstantiateContract().let { it.fundsList.map { c -> c.denom } }
        typeUrl.contains("MsgExecuteContract") -> this.toMsgExecuteContract().let { it.fundsList.map { c -> c.denom } }

        else -> listOf<String>()
            .also { logger().debug("This typeUrl is not yet supported as an asset-based msg: $typeUrl") }
    }

enum class MdEvents(val event: String, val idField: String) {
    // Scope
    SCC("provenance.metadata.v1.EventScopeCreated","scope_addr"),
    SCU("provenance.metadata.v1.EventScopeUpdated", "scope_addr"),
    SCO("provenance.metadata.v1.EventScopeDeleted", "scope_addr"),
    // Session
    SEC("provenance.metadata.v1.EventSessionCreated", "session_addr"),
    SEU("provenance.metadata.v1.EventSessionUpdated", "session_addr"),
    SER("provenance.metadata.v1.EventSessionDeleted", "session_addr"),
    // Record
    RC("provenance.metadata.v1.EventRecordCreated", "record_addr"),
    RU("provenance.metadata.v1.EventRecordUpdated", "record_addr"),
    RR("provenance.metadata.v1.EventRecordDeleted", "record_addr"),
    // Scope Spec
    SCSPC("provenance.metadata.v1.EventScopeSpecificationCreated", "scope_specification_addr"),
    SCSPU("provenance.metadata.v1.EventScopeSpecificationUpdated", "scope_specification_addr"),
    SCSPR("provenance.metadata.v1.EventScopeSpecificationDeleted", "scope_specification_addr"),
    // Contract Spec
    CSPC("provenance.metadata.v1.EventContractSpecificationCreated", "contract_specification_addr"),
    CSPU("provenance.metadata.v1.EventContractSpecificationUpdated", "contract_specification_addr"),
    CSPR("provenance.metadata.v1.EventContractSpecificationDeleted", "contract_specification_addr"),
    // Record Spec
    RSPC("provenance.metadata.v1.EventRecordSpecificationCreated", "record_specification_addr"),
    RSPU("provenance.metadata.v1.EventRecordSpecificationUpdated", "record_specification_addr"),
    RSPR("provenance.metadata.v1.EventRecordSpecificationDeleted", "record_specification_addr")
}

fun Any.isMetadataDeletionMsg() =
    when {
        typeUrl.contains("MsgDeleteScopeRequest")
            || typeUrl.contains("MsgDeleteScopeSpecificationRequest")
            || typeUrl.contains("MsgDeleteContractSpecificationRequest") -> true
        else -> false
    }

fun Any.getAssociatedMetadataEvents() =
    when {
        typeUrl.contains("MsgWriteP8eContractSpecRequest") -> listOf(CSPC,CSPU)
        else -> listOf<MdEvents>()
            .also { logger().debug("This typeUrl is not yet supported in as an metadata-event-based msg: $typeUrl") }
    }

fun Any.getAssociatedMetadata() =
    when {
        typeUrl.contains("MsgWriteScopeRequest") -> this.toMsgWriteScopeRequest()
            .let { it.scopeUuid?.toMAddressScope() ?: it.scope.scopeId?.toMAddress() }
        typeUrl.contains("MsgDeleteScopeRequest") -> this.toMsgDeleteScopeRequest().scopeId.toMAddress()
        typeUrl.contains("MsgAddScopeDataAccessRequest") -> this.toMsgAddScopeDataAccessRequest().scopeId.toMAddress()
        typeUrl.contains("MsgDeleteScopeDataAccessRequest") -> this.toMsgDeleteScopeDataAccessRequest().scopeId.toMAddress()
        typeUrl.contains("MsgAddScopeOwnerRequest") -> this.toMsgAddScopeOwnerRequest().scopeId.toMAddress()
        typeUrl.contains("MsgDeleteScopeOwnerRequest") -> this.toMsgDeleteScopeOwnerRequest().scopeId.toMAddress()
        typeUrl.contains("MsgWriteSessionRequest") -> this.toMsgWriteSessionRequest()
            .let { it.sessionIdComponents.toMAddress() ?: it.session.sessionId.toMAddress() }
        typeUrl.contains("MsgWriteRecordRequest") -> this.toMsgWriteRecordRequest()
            .let { it.sessionIdComponents.toMAddress() ?: it.record.sessionId.toMAddress() }
        typeUrl.contains("MsgDeleteRecordRequest") -> this.toMsgDeleteRecordRequest().recordId.toMAddress()
        typeUrl.contains("MsgWriteScopeSpecificationRequest") -> this.toMsgWriteScopeSpecificationRequest()
            .let { it.specUuid?.toMAddressScopeSpec() ?: it.specification.specificationId?.toMAddress() }
        typeUrl.contains("MsgDeleteScopeSpecificationRequest") ->
            this.toMsgDeleteScopeSpecificationRequest().specificationId.toMAddress()
        typeUrl.contains("MsgWriteContractSpecificationRequest") -> this.toMsgWriteContractSpecificationRequest()
            .let { it.specUuid?.toMAddressContractSpec() ?: it.specification.specificationId?.toMAddress() }
        typeUrl.contains("MsgDeleteContractSpecificationRequest") ->
            this.toMsgDeleteContractSpecificationRequest().specificationId.toMAddress()
        typeUrl.contains("MsgWriteRecordSpecificationRequest") -> this.toMsgWriteRecordSpecificationRequest()
            .let { it.contractSpecUuid?.toMAddressContractSpec() ?: it.specification.specificationId?.toMAddress() }
        typeUrl.contains("MsgDeleteRecordSpecificationRequest") ->
            this.toMsgDeleteRecordSpecificationRequest().specificationId.toMAddress()
        typeUrl.contains("MsgP8eMemorializeContractRequest") -> this.toMsgP8eMemorializeContractRequest().scopeId.toMAddressScope()
        else -> null
            .also { logger().debug("This typeUrl is not yet supported in as an metadata-based msg: $typeUrl") }
    }

fun SessionIdComponents?.toMAddress() =
    if (this != null && this.sessionUuid != null && (this.scopeUuid != null || this.scopeAddr != null)) {
        val scope = this.scopeUuid.toUuid() ?: this.scopeAddr.toMAddress().getPrimaryUuid()
        this.sessionUuid.toMAddressSession(scope)
    } else null
