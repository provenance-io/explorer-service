package io.provenance.explorer.config

import com.google.protobuf.Descriptors
import com.google.protobuf.util.JsonFormat
import cosmos.auth.v1beta1.Auth
import cosmos.authz.v1beta1.Authz
import cosmos.authz.v1beta1.Authz.CountAuthorization
import cosmos.authz.v1beta1.Authz.GrantAuthorization
import cosmos.authz.v1beta1.Event
import cosmos.bank.v1beta1.Tx
import cosmos.distribution.v1beta1.Distribution
import cosmos.feegrant.v1beta1.Feegrant
import cosmos.gov.v1beta1.Gov
import cosmos.group.v1.Events.EventCreateGroup
import cosmos.group.v1.Events.EventCreateGroupPolicy
import cosmos.group.v1.Events.EventExec
import cosmos.group.v1.Events.EventLeaveGroup
import cosmos.group.v1.Events.EventSubmitProposal
import cosmos.group.v1.Events.EventUpdateGroup
import cosmos.group.v1.Events.EventUpdateGroupPolicy
import cosmos.group.v1.Events.EventVote
import cosmos.group.v1.Events.EventWithdrawProposal
import cosmos.group.v1.Tx.MsgCreateGroup
import cosmos.group.v1.Tx.MsgCreateGroupPolicy
import cosmos.group.v1.Tx.MsgCreateGroupWithPolicy
import cosmos.group.v1.Tx.MsgExec
import cosmos.group.v1.Tx.MsgLeaveGroup
import cosmos.group.v1.Tx.MsgSubmitProposal
import cosmos.group.v1.Tx.MsgUpdateGroupAdmin
import cosmos.group.v1.Tx.MsgUpdateGroupMembers
import cosmos.group.v1.Tx.MsgUpdateGroupMetadata
import cosmos.group.v1.Tx.MsgUpdateGroupPolicyAdmin
import cosmos.group.v1.Tx.MsgUpdateGroupPolicyDecisionPolicy
import cosmos.group.v1.Tx.MsgUpdateGroupPolicyMetadata
import cosmos.group.v1.Tx.MsgVote
import cosmos.group.v1.Tx.MsgWithdrawProposal
import cosmos.group.v1.Types.PercentageDecisionPolicy
import cosmos.group.v1.Types.ThresholdDecisionPolicy
import cosmos.nft.v1beta1.Tx.MsgSend
import cosmos.params.v1beta1.Params
import cosmos.staking.v1beta1.Tx.MsgCancelUnbondingDelegation
import cosmos.tx.v1beta1.TxOuterClass
import cosmos.upgrade.v1beta1.Upgrade
import cosmos.vesting.v1beta1.Vesting
import cosmwasm.wasm.v1.Ibc
import cosmwasm.wasm.v1.Proposal
import cosmwasm.wasm.v1.Proposal.UpdateInstantiateConfigProposal
import ibc.applications.interchain_accounts.v1.Account.InterchainAccount
import ibc.core.client.v1.Client
import ibc.lightclients.tendermint.v1.Tendermint
import org.reflections.Reflections
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
            .add(packageDescriptors())
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
            .add(packageDescriptors())
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
        Auth.BaseAccount.getDescriptor(),
        Auth.ModuleAccount.getDescriptor(),
        Vesting.BaseVestingAccount.getDescriptor(),
        Vesting.ContinuousVestingAccount.getDescriptor(),
        Vesting.DelayedVestingAccount.getDescriptor(),
        Vesting.PeriodicVestingAccount.getDescriptor(),
        Vesting.PermanentLockedAccount.getDescriptor(),
        InterchainAccount.getDescriptor()
    )

fun pubKeyDescriptors() =
    listOf(
        cosmos.crypto.ed25519.Keys.PubKey.getDescriptor(),
        cosmos.crypto.secp256k1.Keys.PubKey.getDescriptor(),
        cosmos.crypto.secp256r1.Keys.PubKey.getDescriptor(),
        cosmos.crypto.multisig.Keys.LegacyAminoPubKey.getDescriptor()
    )

fun msgDescriptors(): List<Descriptors.Descriptor> {
    val descriptors = mutableListOf(
        TxOuterClass.Tx.getDescriptor(),
        Tx.MsgSend.getDescriptor(),
        Tx.MsgMultiSend.getDescriptor(),
        cosmos.gov.v1beta1.Tx.MsgSubmitProposal.getDescriptor(),
        cosmos.gov.v1beta1.Tx.MsgVote.getDescriptor(),
        cosmos.gov.v1beta1.Tx.MsgVoteWeighted.getDescriptor(),
        cosmos.gov.v1beta1.Tx.MsgDeposit.getDescriptor(),
        cosmos.gov.v1.Tx.MsgSubmitProposal.getDescriptor(),
        cosmos.gov.v1.Tx.MsgVote.getDescriptor(),
        cosmos.gov.v1.Tx.MsgVoteWeighted.getDescriptor(),
        cosmos.gov.v1.Tx.MsgDeposit.getDescriptor(),
        cosmos.gov.v1.Tx.MsgExecLegacyContent.getDescriptor(),
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
        MsgCancelUnbondingDelegation.getDescriptor(),
        cosmwasm.wasm.v1.Tx.MsgStoreCode.getDescriptor(),
        cosmwasm.wasm.v1.Tx.MsgInstantiateContract.getDescriptor(),
        cosmwasm.wasm.v1.Tx.MsgExecuteContract.getDescriptor(),
        cosmwasm.wasm.v1.Tx.MsgMigrateContract.getDescriptor(),
        cosmwasm.wasm.v1.Tx.MsgUpdateAdmin.getDescriptor(),
        cosmwasm.wasm.v1.Tx.MsgClearAdmin.getDescriptor(),
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
        cosmos.authz.v1beta1.Tx.MsgGrant.getDescriptor(),
        cosmos.authz.v1beta1.Tx.MsgExec.getDescriptor(),
        cosmos.authz.v1beta1.Tx.MsgRevoke.getDescriptor(),
        cosmos.feegrant.v1beta1.Tx.MsgGrantAllowance.getDescriptor(),
        cosmos.feegrant.v1beta1.Tx.MsgRevokeAllowance.getDescriptor(),
        Ibc.MsgIBCSend.getDescriptor(),
        Ibc.MsgIBCCloseChannel.getDescriptor(),
        MsgSend.getDescriptor(),
        MsgCreateGroup.getDescriptor(),
        MsgCreateGroupWithPolicy.getDescriptor(),
        MsgCreateGroupPolicy.getDescriptor(),
        MsgUpdateGroupMembers.getDescriptor(),
        MsgUpdateGroupMetadata.getDescriptor(),
        MsgUpdateGroupPolicyMetadata.getDescriptor(),
        MsgUpdateGroupAdmin.getDescriptor(),
        MsgUpdateGroupPolicyAdmin.getDescriptor(),
        MsgUpdateGroupPolicyDecisionPolicy.getDescriptor(),
        MsgSubmitProposal.getDescriptor(),
        MsgVote.getDescriptor(),
        MsgWithdrawProposal.getDescriptor(),
        MsgExec.getDescriptor(),
        MsgLeaveGroup.getDescriptor()
    )
    return descriptors
}

fun packageDescriptors(): List<Descriptors.Descriptor> {
    val descriptors = mutableListOf<Descriptors.Descriptor>()
    descriptors.addAll(findDescriptorsInPackage("io.provenance.attribute.v1"))
    descriptors.addAll(findDescriptorsInPackage("io.provenance.exchange.v1"))
    descriptors.addAll(findDescriptorsInPackage("io.provenance.hold.v1"))
    descriptors.addAll(findDescriptorsInPackage("io.provenance.ibchooks.v1"))
    descriptors.addAll(findDescriptorsInPackage("io.provenance.ibcratelimit.v1"))
    descriptors.addAll(findDescriptorsInPackage("io.provenance.marker.v1"))
    descriptors.addAll(findDescriptorsInPackage("io.provenance.metadata.v1"))
    descriptors.addAll(findDescriptorsInPackage("io.provenance.msgfees.v1"))
    descriptors.addAll(findDescriptorsInPackage("io.provenance.name.v1"))
    descriptors.addAll(findDescriptorsInPackage("io.provenance.oracle.v1"))
    descriptors.addAll(findDescriptorsInPackage("io.provenance.reward.v1"))
    descriptors.addAll(findDescriptorsInPackage("io.provenance.trigger.v1"))
    return descriptors
}

private fun findDescriptorsInPackage(packageName: String): List<Descriptors.Descriptor> {
    val reflections = Reflections(packageName)
    val messageClasses = reflections.getSubTypesOf(com.google.protobuf.Message::class.java)

    return messageClasses.mapNotNull {
        try {
            it.getMethod("getDescriptor").invoke(null) as Descriptors.Descriptor
        } catch (e: Exception) {
            null // or log error
        }
    }
}

fun contentDescriptors() =
    listOf(
        Gov.TextProposal.getDescriptor(),
        Params.ParameterChangeProposal.getDescriptor(),
        Upgrade.SoftwareUpgradeProposal.getDescriptor(),
        Upgrade.CancelSoftwareUpgradeProposal.getDescriptor(),
        Distribution.CommunityPoolSpendProposal.getDescriptor(),
        Distribution.CommunityPoolSpendProposalWithDeposit.getDescriptor(),
        Proposal.StoreCodeProposal.getDescriptor(),
        Proposal.InstantiateContractProposal.getDescriptor(),
        Proposal.MigrateContractProposal.getDescriptor(),
        Proposal.SudoContractProposal.getDescriptor(),
        Proposal.ExecuteContractProposal.getDescriptor(),
        Proposal.UpdateAdminProposal.getDescriptor(),
        Proposal.ClearAdminProposal.getDescriptor(),
        Proposal.PinCodesProposal.getDescriptor(),
        Proposal.UnpinCodesProposal.getDescriptor(),
        UpdateInstantiateConfigProposal.getDescriptor(),
        cosmwasm.wasm.v1beta1.Proposal.StoreCodeProposal.getDescriptor(),
        cosmwasm.wasm.v1beta1.Proposal.InstantiateContractProposal.getDescriptor(),
        cosmwasm.wasm.v1beta1.Proposal.MigrateContractProposal.getDescriptor(),
        cosmwasm.wasm.v1beta1.Proposal.UpdateAdminProposal.getDescriptor(),
        cosmwasm.wasm.v1beta1.Proposal.ClearAdminProposal.getDescriptor(),
        cosmwasm.wasm.v1beta1.Proposal.PinCodesProposal.getDescriptor(),
        cosmwasm.wasm.v1beta1.Proposal.UnpinCodesProposal.getDescriptor(),
        Client.ClientUpdateProposal.getDescriptor(),
        Client.UpgradeProposal.getDescriptor()
    )

fun events() = listOf(
    Event.EventGrant.getDescriptor(),
    Event.EventRevoke.getDescriptor(),
    EventCreateGroup.getDescriptor(),
    EventUpdateGroup.getDescriptor(),
    EventCreateGroupPolicy.getDescriptor(),
    EventUpdateGroupPolicy.getDescriptor(),
    EventSubmitProposal.getDescriptor(),
    EventWithdrawProposal.getDescriptor(),
    EventVote.getDescriptor(),
    EventExec.getDescriptor(),
    EventLeaveGroup.getDescriptor()
)

fun miscAnys() = listOf(
    Tendermint.Header.getDescriptor(),
    Authz.GenericAuthorization.getDescriptor(),
    cosmos.bank.v1beta1.Authz.SendAuthorization.getDescriptor(),
    Feegrant.BasicAllowance.getDescriptor(),
    Feegrant.PeriodicAllowance.getDescriptor(),
    Feegrant.AllowedMsgAllowance.getDescriptor(),
    cosmos.staking.v1beta1.Authz.StakeAuthorization.getDescriptor(),
    CountAuthorization.getDescriptor(),
    GrantAuthorization.getDescriptor(),
    ThresholdDecisionPolicy.getDescriptor(),
    PercentageDecisionPolicy.getDescriptor()
)
