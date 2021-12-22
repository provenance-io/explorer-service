package io.provenance.explorer.domain.extensions

import com.google.protobuf.Any
import com.google.protobuf.ByteString
import io.provenance.explorer.domain.core.Bech32
import io.provenance.explorer.domain.core.Hash.sha256
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.toBech32Data
import io.provenance.explorer.domain.entities.SignatureRecord
import io.provenance.explorer.domain.models.explorer.AccountSignature
import io.provenance.explorer.domain.models.explorer.Signatures
import io.provenance.explorer.grpc.extensions.toAddress
import io.provenance.explorer.grpc.extensions.toMsgAcknowledgement
import io.provenance.explorer.grpc.extensions.toMsgActivateRequest
import io.provenance.explorer.grpc.extensions.toMsgAddAccessRequest
import io.provenance.explorer.grpc.extensions.toMsgAddAttributeRequest
import io.provenance.explorer.grpc.extensions.toMsgAddContractSpecToScopeSpecRequest
import io.provenance.explorer.grpc.extensions.toMsgAddMarkerRequest
import io.provenance.explorer.grpc.extensions.toMsgAddScopeDataAccessRequest
import io.provenance.explorer.grpc.extensions.toMsgAddScopeOwnerRequest
import io.provenance.explorer.grpc.extensions.toMsgBeginRedelegate
import io.provenance.explorer.grpc.extensions.toMsgBindNameRequest
import io.provenance.explorer.grpc.extensions.toMsgBindOSLocatorRequest
import io.provenance.explorer.grpc.extensions.toMsgBurnRequest
import io.provenance.explorer.grpc.extensions.toMsgCancelRequest
import io.provenance.explorer.grpc.extensions.toMsgChannelCloseConfirm
import io.provenance.explorer.grpc.extensions.toMsgChannelCloseInit
import io.provenance.explorer.grpc.extensions.toMsgChannelOpenAck
import io.provenance.explorer.grpc.extensions.toMsgChannelOpenConfirm
import io.provenance.explorer.grpc.extensions.toMsgChannelOpenInit
import io.provenance.explorer.grpc.extensions.toMsgChannelOpenTry
import io.provenance.explorer.grpc.extensions.toMsgClearAdmin
import io.provenance.explorer.grpc.extensions.toMsgClearAdminOld
import io.provenance.explorer.grpc.extensions.toMsgConnectionOpenAck
import io.provenance.explorer.grpc.extensions.toMsgConnectionOpenConfirm
import io.provenance.explorer.grpc.extensions.toMsgConnectionOpenInit
import io.provenance.explorer.grpc.extensions.toMsgConnectionOpenTry
import io.provenance.explorer.grpc.extensions.toMsgCreateClient
import io.provenance.explorer.grpc.extensions.toMsgCreateValidator
import io.provenance.explorer.grpc.extensions.toMsgCreateVestingAccount
import io.provenance.explorer.grpc.extensions.toMsgDelegate
import io.provenance.explorer.grpc.extensions.toMsgDeleteAccessRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteAttributeRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteContractSpecFromScopeSpecRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteContractSpecificationRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteDistinctAttributeRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteNameRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteOSLocatorRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteRecordRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteRecordSpecificationRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteScopeDataAccessRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteScopeOwnerRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteScopeRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteScopeSpecificationRequest
import io.provenance.explorer.grpc.extensions.toMsgDeposit
import io.provenance.explorer.grpc.extensions.toMsgEditValidator
import io.provenance.explorer.grpc.extensions.toMsgExec
import io.provenance.explorer.grpc.extensions.toMsgExecuteContract
import io.provenance.explorer.grpc.extensions.toMsgExecuteContractOld
import io.provenance.explorer.grpc.extensions.toMsgFinalizeRequest
import io.provenance.explorer.grpc.extensions.toMsgFundCommunityPool
import io.provenance.explorer.grpc.extensions.toMsgGrant
import io.provenance.explorer.grpc.extensions.toMsgGrantAllowance
import io.provenance.explorer.grpc.extensions.toMsgInstantiateContract
import io.provenance.explorer.grpc.extensions.toMsgInstantiateContractOld
import io.provenance.explorer.grpc.extensions.toMsgMigrateContract
import io.provenance.explorer.grpc.extensions.toMsgMigrateContractOld
import io.provenance.explorer.grpc.extensions.toMsgMintRequest
import io.provenance.explorer.grpc.extensions.toMsgModifyOSLocatorRequest
import io.provenance.explorer.grpc.extensions.toMsgMultiSend
import io.provenance.explorer.grpc.extensions.toMsgP8eMemorializeContractRequest
import io.provenance.explorer.grpc.extensions.toMsgRecvPacket
import io.provenance.explorer.grpc.extensions.toMsgRevoke
import io.provenance.explorer.grpc.extensions.toMsgRevokeAllowance
import io.provenance.explorer.grpc.extensions.toMsgSend
import io.provenance.explorer.grpc.extensions.toMsgSetDenomMetadataRequest
import io.provenance.explorer.grpc.extensions.toMsgSetWithdrawAddress
import io.provenance.explorer.grpc.extensions.toMsgStoreCode
import io.provenance.explorer.grpc.extensions.toMsgStoreCodeOld
import io.provenance.explorer.grpc.extensions.toMsgSubmitEvidence
import io.provenance.explorer.grpc.extensions.toMsgSubmitMisbehaviour
import io.provenance.explorer.grpc.extensions.toMsgSubmitProposal
import io.provenance.explorer.grpc.extensions.toMsgTimeout
import io.provenance.explorer.grpc.extensions.toMsgTimeoutOnClose
import io.provenance.explorer.grpc.extensions.toMsgTransfer
import io.provenance.explorer.grpc.extensions.toMsgTransferRequest
import io.provenance.explorer.grpc.extensions.toMsgUndelegate
import io.provenance.explorer.grpc.extensions.toMsgUnjail
import io.provenance.explorer.grpc.extensions.toMsgUpdateAdmin
import io.provenance.explorer.grpc.extensions.toMsgUpdateAdminOld
import io.provenance.explorer.grpc.extensions.toMsgUpdateAttributeRequest
import io.provenance.explorer.grpc.extensions.toMsgUpdateClient
import io.provenance.explorer.grpc.extensions.toMsgUpgradeClient
import io.provenance.explorer.grpc.extensions.toMsgVote
import io.provenance.explorer.grpc.extensions.toMsgVoteWeighted
import io.provenance.explorer.grpc.extensions.toMsgWithdrawDelegatorReward
import io.provenance.explorer.grpc.extensions.toMsgWithdrawRequest
import io.provenance.explorer.grpc.extensions.toMsgWithdrawValidatorCommission
import io.provenance.explorer.grpc.extensions.toMsgWriteContractSpecificationRequest
import io.provenance.explorer.grpc.extensions.toMsgWriteP8eContractSpecRequest
import io.provenance.explorer.grpc.extensions.toMsgWriteRecordRequest
import io.provenance.explorer.grpc.extensions.toMsgWriteRecordSpecificationRequest
import io.provenance.explorer.grpc.extensions.toMsgWriteScopeRequest
import io.provenance.explorer.grpc.extensions.toMsgWriteScopeSpecificationRequest
import io.provenance.explorer.grpc.extensions.toMsgWriteSessionRequest
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import cosmos.crypto.multisig.Keys as MultiSigKeys
import io.provenance.explorer.domain.core.Hash as CustomHash

// PubKeySecp256k1
fun ByteString.secp256k1PubKeyToBech32(hrpPrefix: String) = let {
    val base64 = this.toByteArray()
    require(base64.size == 33) { "Invalid Base 64 pub key byte length must be 33 not ${base64.size}" }
    require(base64[0] == 0x02.toByte() || base64[0] == 0x03.toByte()) { "Invalid first byte must be 2 or 3 not  ${base64[0]}" }
    val shah256 = base64.toSha256()
    val ripemd = shah256.toRIPEMD160()
    require(ripemd.size == 20) { "RipeMD size must be 20 not ${ripemd.size}" }
    Bech32.encode(hrpPrefix, ripemd)
}

// PubKeySecp256r1
fun ByteString.secp256r1PubKeyToBech32(hrpPrefix: String, protoType: String) = let {
    val protoSha = protoType.toByteArray().toSha256()
    val key = protoSha + this.toByteArray()
    val keySha = key.toSha256()
    Bech32.encode(hrpPrefix, keySha)
}

// PubKeyEd25519
// Used by validators to create keys
fun ByteString.edPubKeyToBech32(hrpPrefix: String) = let {
    val base64 = this.toByteArray()
    require(base64.size == 32) { "Invalid Base 64 pub key byte length must be 32 not ${base64.size}" }
    base64.toSha256().copyOfRange(0, 20).toBech32Data(hrpPrefix).address
}

fun ByteArray.toSha256() = CustomHash.sha256(this)

fun ByteArray.toRIPEMD160() = RIPEMD160Digest().let {
    it.update(this, 0, this.size)
    val buffer = ByteArray(it.digestSize)
    it.doFinal(buffer, 0)
    buffer
}

fun List<SignatureRecord>.toSigObj(hrpPrefix: String) =
    if (this.isNotEmpty())
        Signatures(
            this.map { rec -> rec.pubkeyObject.toAddress(hrpPrefix) ?: rec.base64Sig },
            this.first().multiSigObject?.toMultiSig()?.threshold
        )
    else Signatures(listOf(), null)

fun SignatureRecord.toAccountPubKey() =
    this.pubkeyType.split(".").let { it[it.size - 2] }.let { AccountSignature(this.base64Sig, it) }

// PubKey Extensions
fun Any.toSingleSigKeyValue() = this.toSingleSig().let { it?.toBase64() }

fun Any.toSingleSig(): ByteString? =
    when {
        typeUrl.contains("secp256k1") -> this.unpack(cosmos.crypto.secp256k1.Keys.PubKey::class.java).key
        typeUrl.contains("secp256r1") -> this.unpack(cosmos.crypto.secp256r1.Keys.PubKey::class.java).key
        typeUrl.contains("ed25519") -> this.unpack(cosmos.crypto.ed25519.Keys.PubKey::class.java).key
        else -> null.also { logger().error("This typeUrl is not supported in single sig: $typeUrl") }
    }

fun Any.toMultiSig() =
    when {
        typeUrl.contains("LegacyAminoPubKey") -> this.unpack(MultiSigKeys.LegacyAminoPubKey::class.java)
        else -> null.also { logger().error("This typeUrl is not supported in multi sig: $typeUrl") }
    }

fun Any.getSigners() =
    when {
        typeUrl.endsWith("MsgSend") -> this.toMsgSend().let { listOf(it.fromAddress) }
        typeUrl.endsWith("MsgMultiSend") -> this.toMsgMultiSend().let { it.inputsList.map { inp -> inp.address } }
        typeUrl.endsWith("MsgSetWithdrawAddress") -> this.toMsgSetWithdrawAddress().let { listOf(it.delegatorAddress) }
        typeUrl.endsWith("MsgWithdrawDelegatorReward") -> this.toMsgWithdrawDelegatorReward().let { listOf(it.delegatorAddress) }
        typeUrl.endsWith("MsgWithdrawValidatorCommission") -> this.toMsgWithdrawValidatorCommission().let { listOf(it.validatorAddress) }
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
        typeUrl.endsWith("MsgDelegate") -> this.toMsgDelegate().let { listOf(it.delegatorAddress) }
        typeUrl.endsWith("MsgBeginRedelegate") -> this.toMsgBeginRedelegate().let { listOf(it.delegatorAddress) }
        typeUrl.endsWith("MsgUndelegate") -> this.toMsgUndelegate().let { listOf(it.delegatorAddress) }
        typeUrl.endsWith("MsgCreateVestingAccount") -> this.toMsgCreateVestingAccount().let { listOf(it.fromAddress) }
        typeUrl.endsWith("MsgWithdrawRequest") -> this.toMsgWithdrawRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgAddMarkerRequest") -> this.toMsgAddMarkerRequest().let { listOf(it.fromAddress) }
        typeUrl.endsWith("MsgAddAccessRequest") -> this.toMsgAddAccessRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgDeleteAccessRequest") -> this.toMsgDeleteAccessRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgFinalizeRequest") -> this.toMsgFinalizeRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgActivateRequest") -> this.toMsgActivateRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgCancelRequest") -> this.toMsgCancelRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgDeleteRequest") -> this.toMsgDeleteRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgMintRequest") -> this.toMsgMintRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgBurnRequest") -> this.toMsgBurnRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgTransferRequest") -> this.toMsgTransferRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgSetDenomMetadataRequest") -> this.toMsgSetDenomMetadataRequest().let { listOf(it.administrator) }
        typeUrl.endsWith("MsgBindNameRequest") -> this.toMsgBindNameRequest().let { listOf(it.parent.address) }
        typeUrl.endsWith("MsgDeleteNameRequest") -> this.toMsgDeleteNameRequest().let { listOf(it.record.address) }
        typeUrl.endsWith("MsgAddAttributeRequest") -> this.toMsgAddAttributeRequest().let { listOf(it.owner) }
        typeUrl.endsWith("MsgUpdateAttributeRequest") -> this.toMsgUpdateAttributeRequest().let { listOf(it.owner) }
        typeUrl.endsWith("MsgDeleteAttributeRequest") -> this.toMsgDeleteAttributeRequest().let { listOf(it.owner) }
        typeUrl.endsWith("MsgDeleteDistinctAttributeRequest") -> this.toMsgDeleteDistinctAttributeRequest().let { listOf(it.owner) }
        typeUrl.endsWith("MsgP8eMemorializeContractRequest") -> listOf(this.toMsgP8eMemorializeContractRequest().invoker)
        typeUrl.endsWith("MsgWriteP8eContractSpecRequest") -> this.toMsgWriteP8eContractSpecRequest().signersList
        typeUrl.endsWith("MsgWriteScopeRequest") -> this.toMsgWriteScopeRequest().signersList
        typeUrl.endsWith("MsgDeleteScopeRequest") -> this.toMsgDeleteScopeRequest().signersList
        typeUrl.endsWith("MsgAddScopeDataAccessRequest") -> this.toMsgAddScopeDataAccessRequest().signersList
        typeUrl.endsWith("MsgDeleteScopeDataAccessRequest") -> this.toMsgDeleteScopeDataAccessRequest().signersList
        typeUrl.endsWith("MsgAddScopeOwnerRequest") -> this.toMsgAddScopeOwnerRequest().signersList
        typeUrl.endsWith("MsgDeleteScopeOwnerRequest") -> this.toMsgDeleteScopeOwnerRequest().signersList
        typeUrl.endsWith("MsgWriteSessionRequest") -> this.toMsgWriteSessionRequest().signersList
        typeUrl.endsWith("MsgWriteRecordRequest") -> this.toMsgWriteRecordRequest().signersList
        typeUrl.endsWith("MsgDeleteRecordRequest") -> this.toMsgDeleteRecordRequest().signersList
        typeUrl.endsWith("MsgWriteScopeSpecificationRequest") -> this.toMsgWriteScopeSpecificationRequest().signersList
        typeUrl.endsWith("MsgDeleteScopeSpecificationRequest") -> this.toMsgDeleteScopeSpecificationRequest().signersList
        typeUrl.endsWith("MsgWriteContractSpecificationRequest") -> this.toMsgWriteContractSpecificationRequest().signersList
        typeUrl.endsWith("MsgDeleteContractSpecificationRequest") -> this.toMsgDeleteContractSpecificationRequest().signersList
        typeUrl.endsWith("MsgWriteRecordSpecificationRequest") -> this.toMsgWriteRecordSpecificationRequest().signersList
        typeUrl.endsWith("MsgDeleteRecordSpecificationRequest") -> this.toMsgDeleteRecordSpecificationRequest().signersList
        typeUrl.endsWith("MsgAddContractSpecToScopeSpecRequest") -> this.toMsgAddContractSpecToScopeSpecRequest().signersList
        typeUrl.endsWith("MsgDeleteContractSpecFromScopeSpecRequest") -> this.toMsgDeleteContractSpecFromScopeSpecRequest().signersList
        typeUrl.endsWith("MsgBindOSLocatorRequest") -> this.toMsgBindOSLocatorRequest().let { listOf(it.locator.owner) }
        typeUrl.endsWith("MsgDeleteOSLocatorRequest") -> this.toMsgDeleteOSLocatorRequest().let { listOf(it.locator.owner) }
        typeUrl.endsWith("MsgModifyOSLocatorRequest") -> this.toMsgModifyOSLocatorRequest().let { listOf(it.locator.owner) }
        typeUrl.endsWith("v1.MsgStoreCode") -> this.toMsgStoreCode().let { listOf(it.sender) }
        typeUrl.endsWith("v1.MsgInstantiateContract") -> this.toMsgInstantiateContract().let { listOf(it.sender) }
        typeUrl.endsWith("v1.MsgExecuteContract") -> this.toMsgExecuteContract().let { listOf(it.sender) }
        typeUrl.endsWith("v1.MsgMigrateContract") -> this.toMsgMigrateContract().let { listOf(it.sender) }
        typeUrl.endsWith("v1.MsgUpdateAdmin") -> this.toMsgUpdateAdmin().let { listOf(it.sender) }
        typeUrl.endsWith("v1.MsgClearAdmin") -> this.toMsgClearAdmin().let { listOf(it.sender) }
        typeUrl.endsWith("v1beta1.MsgStoreCode") -> this.toMsgStoreCodeOld().let { listOf(it.sender) }
        typeUrl.endsWith("v1beta1.MsgInstantiateContract") -> this.toMsgInstantiateContractOld().let { listOf(it.sender) }
        typeUrl.endsWith("v1beta1.MsgExecuteContract") -> this.toMsgExecuteContractOld().let { listOf(it.sender) }
        typeUrl.endsWith("v1beta1.MsgMigrateContract") -> this.toMsgMigrateContractOld().let { listOf(it.sender) }
        typeUrl.endsWith("v1beta1.MsgUpdateAdmin") -> this.toMsgUpdateAdminOld().let { listOf(it.sender) }
        typeUrl.endsWith("v1beta1.MsgClearAdmin") -> this.toMsgClearAdminOld().let { listOf(it.sender) }
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
        typeUrl.endsWith("MsgGrant") -> this.toMsgGrant().let { listOf(it.granter) }
        typeUrl.endsWith("MsgExec") -> this.toMsgExec().let { listOf(it.grantee) }
        typeUrl.endsWith("MsgRevoke") -> this.toMsgRevoke().let { listOf(it.granter) }
        typeUrl.endsWith("MsgGrantAllowance") -> this.toMsgGrantAllowance().let { listOf(it.granter) }
        typeUrl.endsWith("MsgRevokeAllowance") -> this.toMsgRevokeAllowance().let { listOf(it.granter) }

        else -> listOf<String>().also { logger().debug("This typeUrl is not yet supported as an address-based msg: $typeUrl") }
    }
