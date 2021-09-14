package io.provenance.explorer.config

import com.google.protobuf.util.JsonFormat
import cosmos.auth.v1beta1.Auth
import cosmos.authz.v1beta1.Authz
import cosmos.authz.v1beta1.Event
import cosmos.bank.v1beta1.Tx
import cosmos.crypto.ed25519.Keys
import cosmos.distribution.v1beta1.Distribution
import cosmos.feegrant.v1beta1.Feegrant
import cosmos.gov.v1beta1.Gov
import cosmos.params.v1beta1.Params
import cosmos.tx.v1beta1.TxOuterClass
import cosmos.upgrade.v1beta1.Upgrade
import cosmos.vesting.v1beta1.Vesting
import cosmwasm.wasm.v1beta1.Proposal
import ibc.core.client.v1.Client
import ibc.lightclients.tendermint.v1.Tendermint
import io.provenance.attribute.v1.EventAttributeAdd
import io.provenance.attribute.v1.EventAttributeDelete
import io.provenance.attribute.v1.EventAttributeDistinctDelete
import io.provenance.attribute.v1.MsgAddAttributeRequest
import io.provenance.attribute.v1.MsgDeleteAttributeRequest
import io.provenance.attribute.v1.MsgDeleteDistinctAttributeRequest
import io.provenance.attribute.v1.MsgUpdateAttributeRequest
import io.provenance.marker.v1.AddMarkerProposal
import io.provenance.marker.v1.ChangeStatusProposal
import io.provenance.marker.v1.EventDenomUnit
import io.provenance.marker.v1.EventMarkerAccess
import io.provenance.marker.v1.EventMarkerActivate
import io.provenance.marker.v1.EventMarkerAdd
import io.provenance.marker.v1.EventMarkerAddAccess
import io.provenance.marker.v1.EventMarkerBurn
import io.provenance.marker.v1.EventMarkerCancel
import io.provenance.marker.v1.EventMarkerDelete
import io.provenance.marker.v1.EventMarkerDeleteAccess
import io.provenance.marker.v1.EventMarkerFinalize
import io.provenance.marker.v1.EventMarkerMint
import io.provenance.marker.v1.EventMarkerSetDenomMetadata
import io.provenance.marker.v1.EventMarkerTransfer
import io.provenance.marker.v1.EventMarkerWithdraw
import io.provenance.marker.v1.MarkerAccount
import io.provenance.marker.v1.MarkerTransferAuthorization
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
import io.provenance.marker.v1.RemoveAdministratorProposal
import io.provenance.marker.v1.SetAdministratorProposal
import io.provenance.marker.v1.SetDenomMetadataProposal
import io.provenance.marker.v1.SupplyDecreaseProposal
import io.provenance.marker.v1.SupplyIncreaseProposal
import io.provenance.marker.v1.WithdrawEscrowProposal
import io.provenance.metadata.v1.EventContractSpecificationCreated
import io.provenance.metadata.v1.EventContractSpecificationDeleted
import io.provenance.metadata.v1.EventContractSpecificationUpdated
import io.provenance.metadata.v1.EventOSLocatorCreated
import io.provenance.metadata.v1.EventOSLocatorDeleted
import io.provenance.metadata.v1.EventOSLocatorUpdated
import io.provenance.metadata.v1.EventRecordCreated
import io.provenance.metadata.v1.EventRecordDeleted
import io.provenance.metadata.v1.EventRecordSpecificationCreated
import io.provenance.metadata.v1.EventRecordSpecificationDeleted
import io.provenance.metadata.v1.EventRecordSpecificationUpdated
import io.provenance.metadata.v1.EventRecordUpdated
import io.provenance.metadata.v1.EventScopeCreated
import io.provenance.metadata.v1.EventScopeDeleted
import io.provenance.metadata.v1.EventScopeSpecificationCreated
import io.provenance.metadata.v1.EventScopeSpecificationDeleted
import io.provenance.metadata.v1.EventScopeSpecificationUpdated
import io.provenance.metadata.v1.EventScopeUpdated
import io.provenance.metadata.v1.EventSessionCreated
import io.provenance.metadata.v1.EventSessionDeleted
import io.provenance.metadata.v1.EventSessionUpdated
import io.provenance.metadata.v1.EventTxCompleted
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
import io.provenance.name.v1.CreateRootNameProposal
import io.provenance.name.v1.EventNameBound
import io.provenance.name.v1.EventNameUnbound
import io.provenance.name.v1.MsgBindNameRequest
import io.provenance.name.v1.MsgDeleteNameRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter
import org.springframework.http.converter.protobuf.ProtobufJsonFormatHttpMessageConverter
import org.springframework.web.client.RestTemplate
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class RestConfig {

    @Bean
    fun protoPrinter(): JsonFormat.Printer? {
        val typeRegistry = JsonFormat.TypeRegistry.newBuilder()
            .add(accountDescriptors())
            .add(pubKeyDescriptors())
            .add(msgDescriptors())
            .add(contentDescriptors())
            .add(events())
            .add(miscAnys())
            .build()
        return JsonFormat.printer().usingTypeRegistry(typeRegistry)
    }

    @Bean
    fun protoParser(): JsonFormat.Parser? {
        val typeRegistry = JsonFormat.TypeRegistry.newBuilder()
            .add(accountDescriptors())
            .add(pubKeyDescriptors())
            .add(msgDescriptors())
            .add(contentDescriptors())
            .add(events())
            .add(miscAnys())
            .build()
        return JsonFormat.parser().usingTypeRegistry(typeRegistry)
    }

    @Bean
    @Primary
    fun protobufJsonFormatHttpMessageConverter(): ProtobufHttpMessageConverter? {
        return ProtobufJsonFormatHttpMessageConverter(protoParser(), protoPrinter())
    }

    @Bean
    fun restTemplate(hmc: ProtobufHttpMessageConverter?): RestTemplate? {
        return RestTemplate(listOf(hmc))
    }

    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            @Override
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/api/**")
                    .allowedMethods("*")
                    .allowedOriginPatterns("*")
                    .allowCredentials(true)
                    .maxAge(3600)
            }
        }
    }
}

fun accountDescriptors() =
    listOf(
        MarkerAccount.getDescriptor(),
        Auth.BaseAccount.getDescriptor(),
        Auth.ModuleAccount.getDescriptor(),
        Vesting.BaseVestingAccount.getDescriptor(),
        Vesting.ContinuousVestingAccount.getDescriptor(),
        Vesting.DelayedVestingAccount.getDescriptor(),
        Vesting.PeriodicVestingAccount.getDescriptor(),
        Vesting.PermanentLockedAccount.getDescriptor()
    )

fun pubKeyDescriptors() =
    listOf(
        Keys.PubKey.getDescriptor(),
        cosmos.crypto.secp256k1.Keys.PubKey.getDescriptor(),
        cosmos.crypto.multisig.Keys.LegacyAminoPubKey.getDescriptor()
    )

fun msgDescriptors() =
    listOf(
        TxOuterClass.Tx.getDescriptor(),
        Tx.MsgSend.getDescriptor(),
        Tx.MsgMultiSend.getDescriptor(),
        cosmos.gov.v1beta1.Tx.MsgSubmitProposal.getDescriptor(),
        cosmos.gov.v1beta1.Tx.MsgVote.getDescriptor(),
        cosmos.gov.v1beta1.Tx.MsgVoteWeighted.getDescriptor(),
        cosmos.gov.v1beta1.Tx.MsgDeposit.getDescriptor(),
        cosmos.distribution.v1beta1.Tx.MsgSetWithdrawAddress.getDescriptor(),
        cosmos.distribution.v1beta1.Tx.MsgWithdrawDelegatorReward.getDescriptor(),
        cosmos.distribution.v1beta1.Tx.MsgWithdrawValidatorCommission.getDescriptor(),
        cosmos.distribution.v1beta1.Tx.MsgFundCommunityPool.getDescriptor(),
        cosmos.evidence.v1beta1.Tx.MsgSubmitEvidence.getDescriptor(),
        cosmos.slashing.v1beta1.Tx.MsgUnjail.getDescriptor(),
        cosmos.staking.v1beta1.Tx.MsgCreateValidator.getDescriptor(),
        cosmos.staking.v1beta1.Tx.MsgEditValidator.getDescriptor(),
        cosmos.staking.v1beta1.Tx.MsgDelegate.getDescriptor(),
        cosmos.staking.v1beta1.Tx.MsgBeginRedelegate.getDescriptor(),
        cosmos.staking.v1beta1.Tx.MsgUndelegate.getDescriptor(),
        cosmos.vesting.v1beta1.Tx.MsgCreateVestingAccount.getDescriptor(),
        MsgWithdrawRequest.getDescriptor(),
        MsgAddMarkerRequest.getDescriptor(),
        MsgAddAccessRequest.getDescriptor(),
        MsgDeleteAccessRequest.getDescriptor(),
        MsgFinalizeRequest.getDescriptor(),
        MsgActivateRequest.getDescriptor(),
        MsgCancelRequest.getDescriptor(),
        MsgDeleteRequest.getDescriptor(),
        MsgMintRequest.getDescriptor(),
        MsgBurnRequest.getDescriptor(),
        MsgTransferRequest.getDescriptor(),
        MsgSetDenomMetadataRequest.getDescriptor(),
        MsgBindNameRequest.getDescriptor(),
        MsgDeleteNameRequest.getDescriptor(),
        MsgAddAttributeRequest.getDescriptor(),
        MsgDeleteAttributeRequest.getDescriptor(),
        MsgWriteP8eContractSpecRequest.getDescriptor(),
        MsgP8eMemorializeContractRequest.getDescriptor(),
        MsgWriteScopeRequest.getDescriptor(),
        MsgDeleteScopeRequest.getDescriptor(),
        MsgWriteSessionRequest.getDescriptor(),
        MsgWriteRecordRequest.getDescriptor(),
        MsgDeleteRecordRequest.getDescriptor(),
        MsgWriteScopeSpecificationRequest.getDescriptor(),
        MsgDeleteScopeSpecificationRequest.getDescriptor(),
        MsgWriteContractSpecificationRequest.getDescriptor(),
        MsgDeleteContractSpecificationRequest.getDescriptor(),
        MsgWriteRecordSpecificationRequest.getDescriptor(),
        MsgDeleteRecordSpecificationRequest.getDescriptor(),
        MsgBindOSLocatorRequest.getDescriptor(),
        MsgDeleteOSLocatorRequest.getDescriptor(),
        MsgModifyOSLocatorRequest.getDescriptor(),
        cosmwasm.wasm.v1beta1.Tx.MsgStoreCode.getDescriptor(),
        cosmwasm.wasm.v1beta1.Tx.MsgInstantiateContract.getDescriptor(),
        cosmwasm.wasm.v1beta1.Tx.MsgExecuteContract.getDescriptor(),
        cosmwasm.wasm.v1beta1.Tx.MsgMigrateContract.getDescriptor(),
        cosmwasm.wasm.v1beta1.Tx.MsgUpdateAdmin.getDescriptor(),
        cosmwasm.wasm.v1beta1.Tx.MsgClearAdmin.getDescriptor(),
        cosmos.crisis.v1beta1.Tx.MsgVerifyInvariant.getDescriptor(),
        ibc.applications.transfer.v1.Tx.MsgTransfer.getDescriptor(),
        ibc.core.channel.v1.Tx.MsgChannelOpenInit.getDescriptor(),
        ibc.core.channel.v1.Tx.MsgChannelOpenTry.getDescriptor(),
        ibc.core.channel.v1.Tx.MsgChannelOpenAck.getDescriptor(),
        ibc.core.channel.v1.Tx.MsgChannelOpenConfirm.getDescriptor(),
        ibc.core.channel.v1.Tx.MsgChannelCloseInit.getDescriptor(),
        ibc.core.channel.v1.Tx.MsgChannelCloseConfirm.getDescriptor(),
        ibc.core.channel.v1.Tx.MsgRecvPacket.getDescriptor(),
        ibc.core.channel.v1.Tx.MsgTimeout.getDescriptor(),
        ibc.core.channel.v1.Tx.MsgTimeoutOnClose.getDescriptor(),
        ibc.core.channel.v1.Tx.MsgAcknowledgement.getDescriptor(),
        ibc.core.client.v1.Tx.MsgCreateClient.getDescriptor(),
        ibc.core.client.v1.Tx.MsgUpdateClient.getDescriptor(),
        ibc.core.client.v1.Tx.MsgUpgradeClient.getDescriptor(),
        ibc.core.client.v1.Tx.MsgSubmitMisbehaviour.getDescriptor(),
        ibc.core.connection.v1.Tx.MsgConnectionOpenInit.getDescriptor(),
        ibc.core.connection.v1.Tx.MsgConnectionOpenTry.getDescriptor(),
        ibc.core.connection.v1.Tx.MsgConnectionOpenAck.getDescriptor(),
        ibc.core.connection.v1.Tx.MsgConnectionOpenConfirm.getDescriptor(),
        MsgAddScopeDataAccessRequest.getDescriptor(),
        MsgDeleteScopeDataAccessRequest.getDescriptor(),
        MsgAddScopeOwnerRequest.getDescriptor(),
        MsgDeleteScopeOwnerRequest.getDescriptor(),
        MsgUpdateAttributeRequest.getDescriptor(),
        MsgDeleteDistinctAttributeRequest.getDescriptor(),
        MsgAddContractSpecToScopeSpecRequest.getDescriptor(),
        MsgDeleteContractSpecFromScopeSpecRequest.getDescriptor(),
        cosmos.authz.v1beta1.Tx.MsgGrant.getDescriptor(),
        cosmos.authz.v1beta1.Tx.MsgExec.getDescriptor(),
        cosmos.authz.v1beta1.Tx.MsgRevoke.getDescriptor(),
        cosmos.feegrant.v1beta1.Tx.MsgGrantAllowance.getDescriptor(),
        cosmos.feegrant.v1beta1.Tx.MsgRevokeAllowance.getDescriptor()
    )

fun contentDescriptors() =
    listOf(
        Gov.TextProposal.getDescriptor(),
        Params.ParameterChangeProposal.getDescriptor(),
        Upgrade.SoftwareUpgradeProposal.getDescriptor(),
        Upgrade.CancelSoftwareUpgradeProposal.getDescriptor(),
        Distribution.CommunityPoolSpendProposal.getDescriptor(),
        Distribution.CommunityPoolSpendProposalWithDeposit.getDescriptor(),
        AddMarkerProposal.getDescriptor(),
        SupplyIncreaseProposal.getDescriptor(),
        SupplyDecreaseProposal.getDescriptor(),
        SetAdministratorProposal.getDescriptor(),
        RemoveAdministratorProposal.getDescriptor(),
        ChangeStatusProposal.getDescriptor(),
        WithdrawEscrowProposal.getDescriptor(),
        CreateRootNameProposal.getDescriptor(),
        Proposal.StoreCodeProposal.getDescriptor(),
        Proposal.InstantiateContractProposal.getDescriptor(),
        Proposal.MigrateContractProposal.getDescriptor(),
        Proposal.UpdateAdminProposal.getDescriptor(),
        Proposal.ClearAdminProposal.getDescriptor(),
        Proposal.PinCodesProposal.getDescriptor(),
        Proposal.UnpinCodesProposal.getDescriptor(),
        Client.ClientUpdateProposal.getDescriptor(),
        SetDenomMetadataProposal.getDescriptor()
    )

fun events() = listOf(
    EventNameBound.getDescriptor(),
    EventNameUnbound.getDescriptor(),
    EventMarkerAdd.getDescriptor(),
    EventMarkerAddAccess.getDescriptor(),
    EventMarkerAccess.getDescriptor(),
    EventMarkerDeleteAccess.getDescriptor(),
    EventMarkerFinalize.getDescriptor(),
    EventMarkerActivate.getDescriptor(),
    EventMarkerCancel.getDescriptor(),
    EventMarkerDelete.getDescriptor(),
    EventMarkerMint.getDescriptor(),
    EventMarkerBurn.getDescriptor(),
    EventMarkerWithdraw.getDescriptor(),
    EventMarkerTransfer.getDescriptor(),
    EventMarkerSetDenomMetadata.getDescriptor(),
    EventDenomUnit.getDescriptor(),
    EventTxCompleted.getDescriptor(),
    EventScopeCreated.getDescriptor(),
    EventScopeUpdated.getDescriptor(),
    EventScopeDeleted.getDescriptor(),
    EventSessionCreated.getDescriptor(),
    EventSessionUpdated.getDescriptor(),
    EventSessionDeleted.getDescriptor(),
    EventRecordCreated.getDescriptor(),
    EventRecordUpdated.getDescriptor(),
    EventRecordDeleted.getDescriptor(),
    EventScopeSpecificationCreated.getDescriptor(),
    EventScopeSpecificationUpdated.getDescriptor(),
    EventScopeSpecificationDeleted.getDescriptor(),
    EventContractSpecificationCreated.getDescriptor(),
    EventContractSpecificationUpdated.getDescriptor(),
    EventContractSpecificationDeleted.getDescriptor(),
    EventRecordSpecificationCreated.getDescriptor(),
    EventRecordSpecificationUpdated.getDescriptor(),
    EventRecordSpecificationDeleted.getDescriptor(),
    EventOSLocatorCreated.getDescriptor(),
    EventOSLocatorUpdated.getDescriptor(),
    EventOSLocatorDeleted.getDescriptor(),
    EventAttributeAdd.getDescriptor(),
    EventAttributeDelete.getDescriptor(),
    EventAttributeDistinctDelete.getDescriptor(),
    Event.EventGrant.getDescriptor(),
    Event.EventRevoke.getDescriptor()
)

fun miscAnys() = listOf(
    Tendermint.Header.getDescriptor(),
    Authz.GenericAuthorization.getDescriptor(),
    cosmos.bank.v1beta1.Authz.SendAuthorization.getDescriptor(),
    Feegrant.BasicAllowance.getDescriptor(),
    Feegrant.PeriodicAllowance.getDescriptor(),
    Feegrant.AllowedMsgAllowance.getDescriptor(),
    cosmos.staking.v1beta1.Authz.StakeAuthorization.getDescriptor(),
    MarkerTransferAuthorization.getDescriptor()
)
