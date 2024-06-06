package io.provenance.explorer.config

import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.util.JsonFormat
import cosmos.auth.v1beta1.Auth
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(RestConfigTest.Config::class)
class RestConfigTest {

    @Autowired
    private lateinit var protoPrinter: JsonFormat.Printer

    @Autowired
    private lateinit var protoParser: JsonFormat.Parser

    @Configuration
    class Config : RestConfig() {
        @Bean
        override fun protoPrinter(): JsonFormat.Printer? {
            return super.protoPrinter()
        }

        @Bean
        override fun protoParser(): JsonFormat.Parser? {
            return super.protoParser()
        }
    }

    @Test
    fun `test protoPrinter includes all declared descriptors`() {
        val descriptorsToCheck = getDeclaredDescriptors()

        descriptorsToCheck.forEach { descriptor ->
            val messageBuilder = DynamicMessage.newBuilder(descriptor).build()
            val json = protoPrinter.print(messageBuilder)
            val parsedMessageBuilder = DynamicMessage.newBuilder(descriptor)
            JsonFormat.parser().merge(json, parsedMessageBuilder)
            val parsedMessage = parsedMessageBuilder.build()

            assertTrue(messageBuilder.descriptorForType == parsedMessage.descriptorForType, "Descriptor for ${descriptor.fullName} should be present")
        }
    }

    @Test
    fun `test protoParser includes all declared descriptors`() {
        val descriptorsToCheck = getDeclaredDescriptors()

        descriptorsToCheck.forEach { descriptor ->
            val messageBuilder = DynamicMessage.newBuilder(descriptor).build()
            val json = protoPrinter.print(messageBuilder)
            val parsedMessageBuilder = DynamicMessage.newBuilder(descriptor)
            protoParser.merge(json, parsedMessageBuilder)
            val parsedMessage = parsedMessageBuilder.build()

            assertTrue(messageBuilder.descriptorForType == parsedMessage.descriptorForType, "Descriptor for ${descriptor.fullName} should be present")
        }
    }

    // These are all the descriptors that were explicitly declared in RestConfig from v5.8.0 and prior
    // wanted to test that they still exist after dynamically loading descriptor fix.
    // Can be slimmed down or removed completely in a later release
    private fun getDeclaredDescriptors(): List<Descriptors.Descriptor> {
        return listOf(
            // Account descriptors
            Auth.BaseAccount.getDescriptor(),
            Auth.ModuleAccount.getDescriptor(),
            cosmos.vesting.v1beta1.Vesting.BaseVestingAccount.getDescriptor(),
            cosmos.vesting.v1beta1.Vesting.ContinuousVestingAccount.getDescriptor(),
            cosmos.vesting.v1beta1.Vesting.DelayedVestingAccount.getDescriptor(),
            cosmos.vesting.v1beta1.Vesting.PeriodicVestingAccount.getDescriptor(),
            cosmos.vesting.v1beta1.Vesting.PermanentLockedAccount.getDescriptor(),
            ibc.applications.interchain_accounts.v1.Account.InterchainAccount.getDescriptor(),
            // PubKey descriptors
            cosmos.crypto.ed25519.Keys.PubKey.getDescriptor(),
            cosmos.crypto.secp256k1.Keys.PubKey.getDescriptor(),
            cosmos.crypto.secp256r1.Keys.PubKey.getDescriptor(),
            cosmos.crypto.multisig.Keys.LegacyAminoPubKey.getDescriptor(),
            // Msg descriptors
            cosmos.tx.v1beta1.TxOuterClass.Tx.getDescriptor(),
            cosmos.bank.v1beta1.Tx.MsgSend.getDescriptor(),
            cosmos.bank.v1beta1.Tx.MsgMultiSend.getDescriptor(),
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
            cosmos.staking.v1beta1.Tx.MsgCancelUnbondingDelegation.getDescriptor(),
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
            cosmwasm.wasm.v1.Ibc.MsgIBCSend.getDescriptor(),
            cosmwasm.wasm.v1.Ibc.MsgIBCCloseChannel.getDescriptor(),
            cosmos.nft.v1beta1.Tx.MsgSend.getDescriptor(),
            cosmos.group.v1.Tx.MsgCreateGroup.getDescriptor(),
            cosmos.group.v1.Tx.MsgCreateGroupWithPolicy.getDescriptor(),
            cosmos.group.v1.Tx.MsgCreateGroupPolicy.getDescriptor(),
            cosmos.group.v1.Tx.MsgUpdateGroupMembers.getDescriptor(),
            cosmos.group.v1.Tx.MsgUpdateGroupMetadata.getDescriptor(),
            cosmos.group.v1.Tx.MsgUpdateGroupPolicyMetadata.getDescriptor(),
            cosmos.group.v1.Tx.MsgUpdateGroupAdmin.getDescriptor(),
            cosmos.group.v1.Tx.MsgUpdateGroupPolicyAdmin.getDescriptor(),
            cosmos.group.v1.Tx.MsgUpdateGroupPolicyDecisionPolicy.getDescriptor(),
            cosmos.group.v1.Tx.MsgSubmitProposal.getDescriptor(),
            cosmos.group.v1.Tx.MsgVote.getDescriptor(),
            cosmos.group.v1.Tx.MsgWithdrawProposal.getDescriptor(),
            cosmos.group.v1.Tx.MsgExec.getDescriptor(),
            cosmos.group.v1.Tx.MsgLeaveGroup.getDescriptor(),
            // Content descriptors
            cosmos.gov.v1beta1.Gov.TextProposal.getDescriptor(),
            cosmos.params.v1beta1.Params.ParameterChangeProposal.getDescriptor(),
            cosmos.upgrade.v1beta1.Upgrade.SoftwareUpgradeProposal.getDescriptor(),
            cosmos.upgrade.v1beta1.Upgrade.CancelSoftwareUpgradeProposal.getDescriptor(),
            cosmos.distribution.v1beta1.Distribution.CommunityPoolSpendProposal.getDescriptor(),
            cosmwasm.wasm.v1.Proposal.StoreCodeProposal.getDescriptor(),
            cosmwasm.wasm.v1.Proposal.InstantiateContractProposal.getDescriptor(),
            cosmwasm.wasm.v1.Proposal.MigrateContractProposal.getDescriptor(),
            cosmwasm.wasm.v1.Proposal.SudoContractProposal.getDescriptor(),
            cosmwasm.wasm.v1.Proposal.ExecuteContractProposal.getDescriptor(),
            cosmwasm.wasm.v1.Proposal.UpdateAdminProposal.getDescriptor(),
            cosmwasm.wasm.v1.Proposal.ClearAdminProposal.getDescriptor(),
            cosmwasm.wasm.v1.Proposal.PinCodesProposal.getDescriptor(),
            cosmwasm.wasm.v1.Proposal.UnpinCodesProposal.getDescriptor(),
            cosmwasm.wasm.v1.Proposal.UpdateInstantiateConfigProposal.getDescriptor(),
            cosmwasm.wasm.v1beta1.Proposal.StoreCodeProposal.getDescriptor(),
            cosmwasm.wasm.v1beta1.Proposal.InstantiateContractProposal.getDescriptor(),
            cosmwasm.wasm.v1beta1.Proposal.MigrateContractProposal.getDescriptor(),
            cosmwasm.wasm.v1beta1.Proposal.UpdateAdminProposal.getDescriptor(),
            cosmwasm.wasm.v1beta1.Proposal.ClearAdminProposal.getDescriptor(),
            cosmwasm.wasm.v1beta1.Proposal.PinCodesProposal.getDescriptor(),
            cosmwasm.wasm.v1beta1.Proposal.UnpinCodesProposal.getDescriptor(),
            ibc.core.client.v1.Client.ClientUpdateProposal.getDescriptor(),
            ibc.core.client.v1.Client.UpgradeProposal.getDescriptor(),
            // Event descriptors
            cosmos.authz.v1beta1.Event.EventGrant.getDescriptor(),
            cosmos.authz.v1beta1.Event.EventRevoke.getDescriptor(),
            cosmos.group.v1.Events.EventCreateGroup.getDescriptor(),
            cosmos.group.v1.Events.EventUpdateGroup.getDescriptor(),
            cosmos.group.v1.Events.EventCreateGroupPolicy.getDescriptor(),
            cosmos.group.v1.Events.EventUpdateGroupPolicy.getDescriptor(),
            cosmos.group.v1.Events.EventSubmitProposal.getDescriptor(),
            cosmos.group.v1.Events.EventWithdrawProposal.getDescriptor(),
            cosmos.group.v1.Events.EventVote.getDescriptor(),
            cosmos.group.v1.Events.EventExec.getDescriptor(),
            cosmos.group.v1.Events.EventLeaveGroup.getDescriptor(),
            // Misc Anys
            ibc.lightclients.tendermint.v1.Tendermint.Header.getDescriptor(),
            cosmos.authz.v1beta1.Authz.GenericAuthorization.getDescriptor(),
            cosmos.bank.v1beta1.Authz.SendAuthorization.getDescriptor(),
            cosmos.feegrant.v1beta1.Feegrant.BasicAllowance.getDescriptor(),
            cosmos.feegrant.v1beta1.Feegrant.PeriodicAllowance.getDescriptor(),
            cosmos.feegrant.v1beta1.Feegrant.AllowedMsgAllowance.getDescriptor(),
            cosmos.staking.v1beta1.Authz.StakeAuthorization.getDescriptor(),
            cosmos.authz.v1beta1.Authz.CountAuthorization.getDescriptor(),
            cosmos.authz.v1beta1.Authz.GrantAuthorization.getDescriptor(),
            cosmos.group.v1.Types.ThresholdDecisionPolicy.getDescriptor(),
            cosmos.group.v1.Types.PercentageDecisionPolicy.getDescriptor(),
        )
    }
}
