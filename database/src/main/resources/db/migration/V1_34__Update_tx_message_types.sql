SELECT 'Updating tx_msg_event' AS comment;

ALTER TABLE tx_msg_event
ADD COLUMN IF NOT EXISTS tx_msg_type_id INT;

DROP INDEX IF EXISTS tx_msg_event_msg_type_id_idx;
CREATE INDEX IF NOT EXISTS tx_msg_events_msg_type_id_idx ON tx_msg_event(tx_msg_type_id);

UPDATE tx_msg_event tme
    SET tx_msg_type_id = tmt.id
FROM tx_message_type tmt
WHERE tme.tx_message_type_id = tmt.type;

ALTER TABLE tx_msg_event
DROP COLUMN IF EXISTS tx_message_type_id;

SELECT 'Updating tx_single_message_cache' AS comment;
update tx_single_message_cache as tsmc
set tx_message_type = tsmc_new.tx_message_type_id_new
from (values ('/cosmwasm.wasm.v1.MsgExecuteContract', 'execute_contract'),
             ('write_p8e_contract_spec_request', 'write_p8e_contract_spec'),
             ('/provenance.metadata.v1.MsgAddContractSpecToScopeSpecRequest', 'add_contract_spec_to_scope_spec'),
             ('/cosmwasm.wasm.v1.MsgStoreCode', 'store_code'),
             ('write_record_specification_request', 'write_record_specification'),
             ('/provenance.metadata.v1.MsgWriteSessionRequest', 'write_session'),
             ('p8e_memorialize_contract_request', 'p8e_memorialize_contract'),
             ('write_scope_specification_request', 'write_scope_specification'),
             ('addmarker', 'add_marker'),
             ('clear-contract-admin', 'clear_admin'),
             ('store-code', 'store_code'),
             ('add_contract_spec_to_scope_spec_request', 'add_contract_spec_to_scope_spec'),
             ('/provenance.metadata.v1.MsgWriteContractSpecificationRequest', 'write_contract_specification'),
             ('/cosmos.authz.v1beta1.MsgGrant', 'grant'),
             ('write_contract_specification_request', 'write_contract_specification'),
             ('/provenance.metadata.v1.MsgWriteRecordSpecificationRequest', 'write_record_specification'),
             ('/cosmwasm.wasm.v1.MsgMigrateContract', 'migrate_contract'),
             ('/cosmwasm.wasm.v1.MsgInstantiateContract', 'instantiate_contract'),
             ('/provenance.metadata.v1.MsgWriteRecordRequest', 'write_record'),
             ('instantiate', 'instantiate_contract'),
             ('execute', 'execute_contract'),
             ('migrate', 'migrate_contract'),
             ('begin_unbonding', 'undelegate'),
             ('/cosmos.feegrant.v1beta1.MsgGrantAllowance', 'grant_allowance'),
             ('setmetadata', 'set_denom_metadata'),
             ('write_scope_request', 'write_scope'),
             ('addaccess', 'add_access'),
             ('acknowledge_packet', 'acknowledgement'),
             ('deleteaccess', 'delete_access'),
             ('delete_scope_owner_request', 'delete_scope_owner'),
             ('bind-name', 'bind_name'),
             ('delete-name', 'delete_name')
     ) as tsmc_new(tx_message_type_id_old, tx_message_type_id_new)
where tsmc_new.tx_message_type_id_old = tsmc.tx_message_type;

SELECT 'Updating tx_single_message_gas_stats_day' AS comment;
update tx_single_message_gas_stats_day as tsmc
set tx_message_type = tsmc_new.tx_message_type_id_new
from (values ('/cosmwasm.wasm.v1.MsgExecuteContract', 'execute_contract'),
             ('write_p8e_contract_spec_request', 'write_p8e_contract_spec'),
             ('/provenance.metadata.v1.MsgAddContractSpecToScopeSpecRequest', 'add_contract_spec_to_scope_spec'),
             ('/cosmwasm.wasm.v1.MsgStoreCode', 'store_code'),
             ('write_record_specification_request', 'write_record_specification'),
             ('/provenance.metadata.v1.MsgWriteSessionRequest', 'write_session'),
             ('p8e_memorialize_contract_request', 'p8e_memorialize_contract'),
             ('write_scope_specification_request', 'write_scope_specification'),
             ('addmarker', 'add_marker'),
             ('clear-contract-admin', 'clear_admin'),
             ('store-code', 'store_code'),
             ('add_contract_spec_to_scope_spec_request', 'add_contract_spec_to_scope_spec'),
             ('/provenance.metadata.v1.MsgWriteContractSpecificationRequest', 'write_contract_specification'),
             ('/cosmos.authz.v1beta1.MsgGrant', 'grant'),
             ('write_contract_specification_request', 'write_contract_specification'),
             ('/provenance.metadata.v1.MsgWriteRecordSpecificationRequest', 'write_record_specification'),
             ('/cosmwasm.wasm.v1.MsgMigrateContract', 'migrate_contract'),
             ('/cosmwasm.wasm.v1.MsgInstantiateContract', 'instantiate_contract'),
             ('/provenance.metadata.v1.MsgWriteRecordRequest', 'write_record'),
             ('instantiate', 'instantiate_contract'),
             ('execute', 'execute_contract'),
             ('migrate', 'migrate_contract'),
             ('begin_unbonding', 'undelegate'),
             ('/cosmos.feegrant.v1beta1.MsgGrantAllowance', 'grant_allowance'),
             ('setmetadata', 'set_denom_metadata'),
             ('write_scope_request', 'write_scope'),
             ('addaccess', 'add_access'),
             ('acknowledge_packet', 'acknowledgement'),
             ('deleteaccess', 'delete_access'),
             ('delete_scope_owner_request', 'delete_scope_owner'),
             ('bind-name', 'bind_name'),
             ('delete-name', 'delete_name')
     ) as tsmc_new(tx_message_type_id_old, tx_message_type_id_new)
where tsmc_new.tx_message_type_id_old = tsmc.tx_message_type;

SELECT 'Updating tx_single_message_gas_stats_day' AS comment;
update tx_single_message_gas_stats_hour as tsmc
set tx_message_type = tsmc_new.tx_message_type_id_new
from (values ('/cosmwasm.wasm.v1.MsgExecuteContract', 'execute_contract'),
             ('write_p8e_contract_spec_request', 'write_p8e_contract_spec'),
             ('/provenance.metadata.v1.MsgAddContractSpecToScopeSpecRequest', 'add_contract_spec_to_scope_spec'),
             ('/cosmwasm.wasm.v1.MsgStoreCode', 'store_code'),
             ('write_record_specification_request', 'write_record_specification'),
             ('/provenance.metadata.v1.MsgWriteSessionRequest', 'write_session'),
             ('p8e_memorialize_contract_request', 'p8e_memorialize_contract'),
             ('write_scope_specification_request', 'write_scope_specification'),
             ('addmarker', 'add_marker'),
             ('clear-contract-admin', 'clear_admin'),
             ('store-code', 'store_code'),
             ('add_contract_spec_to_scope_spec_request', 'add_contract_spec_to_scope_spec'),
             ('/provenance.metadata.v1.MsgWriteContractSpecificationRequest', 'write_contract_specification'),
             ('/cosmos.authz.v1beta1.MsgGrant', 'grant'),
             ('write_contract_specification_request', 'write_contract_specification'),
             ('/provenance.metadata.v1.MsgWriteRecordSpecificationRequest', 'write_record_specification'),
             ('/cosmwasm.wasm.v1.MsgMigrateContract', 'migrate_contract'),
             ('/cosmwasm.wasm.v1.MsgInstantiateContract', 'instantiate_contract'),
             ('/provenance.metadata.v1.MsgWriteRecordRequest', 'write_record'),
             ('instantiate', 'instantiate_contract'),
             ('execute', 'execute_contract'),
             ('migrate', 'migrate_contract'),
             ('begin_unbonding', 'undelegate'),
             ('/cosmos.feegrant.v1beta1.MsgGrantAllowance', 'grant_allowance'),
             ('setmetadata', 'set_denom_metadata'),
             ('write_scope_request', 'write_scope'),
             ('addaccess', 'add_access'),
             ('acknowledge_packet', 'acknowledgement'),
             ('deleteaccess', 'delete_access'),
             ('delete_scope_owner_request', 'delete_scope_owner'),
             ('bind-name', 'bind_name'),
             ('delete-name', 'delete_name')
     ) as tsmc_new(tx_message_type_id_old, tx_message_type_id_new)
where tsmc_new.tx_message_type_id_old = tsmc.tx_message_type;

SELECT 'Updating tx_message_type' AS comment;
CREATE UNIQUE INDEX IF NOT EXISTS tx_message_type_proto_type_idx ON tx_message_type (proto_type);

INSERT INTO tx_message_type(proto_type, module, type, category)
VALUES ( '/cosmos.gov.v1beta1.MsgVote', 'gov', 'vote', 'governance' ),
       ( '/cosmos.gov.v1beta1.MsgSubmitProposal', 'gov', 'submit_proposal', 'governance' ),
       ( '/provenance.marker.v1.MsgWithdrawRequest', 'marker', 'withdraw', 'asset' ),
       ( '/cosmos.slashing.v1beta1.MsgUnjail', 'slashing', 'unjail', 'staking' ),
       ( '/cosmos.staking.v1beta1.MsgDelegate', 'staking', 'delegate', 'staking' ),
       ( '/provenance.marker.v1.MsgMintRequest', 'marker', 'mint', 'asset' ),
       ( '/cosmos.staking.v1beta1.MsgCreateValidator', 'staking', 'create_validator', 'staking' ),
       ( '/provenance.marker.v1.MsgBurnRequest', 'marker', 'burn', 'asset' ),
       ( '/provenance.marker.v1.MsgAddAccessRequest', 'marker', 'add_access', 'asset' ),
       ( '/provenance.marker.v1.MsgActivateRequest', 'marker', 'activate', 'asset' ),
       ( '/provenance.marker.v1.MsgFinalizeRequest', 'marker', 'finalize', 'asset' ),
       ( '/provenance.marker.v1.MsgDeleteAccessRequest', 'marker', 'delete_access', 'asset' ),
       ( '/cosmos.bank.v1beta1.MsgSend', 'bank', 'send', 'transfer' ),
       ( '/provenance.marker.v1.MsgAddMarkerRequest', 'marker', 'add_marker', 'asset' ),
       ( '/provenance.marker.v1.MsgSetDenomMetadataRequest', 'marker', 'set_denom_metadata', 'asset' ),
       ( '/cosmos.staking.v1beta1.MsgUndelegate', 'staking', 'undelegate', 'staking' ),
       ( '/ibc.core.client.v1.MsgUpdateClient', 'ibc_client', 'update_client', 'ibc' ),
       ( '/ibc.core.channel.v1.MsgChannelOpenConfirm', 'ibc_channel', 'channel_open_confirm', 'ibc' ),
       ( '/ibc.core.channel.v1.MsgChannelOpenTry', 'ibc_channel', 'channel_open_try', 'ibc' ),
       ( '/ibc.core.connection.v1.MsgConnectionOpenConfirm', 'ibc_connection', 'connection_open_confirm', 'ibc' ),
       ( '/ibc.core.connection.v1.MsgConnectionOpenTry', 'ibc_connection', 'connection_open_try', 'ibc' ),
       ( '/ibc.core.client.v1.MsgCreateClient', 'ibc_client', 'create_client', 'ibc' ),
       ( '/provenance.metadata.v1.MsgWriteContractSpecificationRequest', 'metadata', 'write_contract_specification', 'nft' ),
       ( '/provenance.metadata.v1.MsgAddContractSpecToScopeSpecRequest', 'metadata', 'add_contract_spec_to_scope_spec', 'nft' ),
       ( '/cosmwasm.wasm.v1.MsgExecuteContract', 'wasm', 'execute_contract', 'smart_contract' ),
       ( '/ibc.core.channel.v1.MsgChannelOpenAck', 'ibc_channel', 'channel_open_ack', 'ibc' ),
       ( '/ibc.core.connection.v1.MsgConnectionOpenAck', 'ibc_connection', 'connection_open_ack', 'ibc' ),
       ( '/ibc.core.channel.v1.MsgChannelOpenInit', 'ibc_channel', 'channel_open_init', 'ibc' ),
       ( '/ibc.core.channel.v1.MsgRecvPacket', 'ibc_channel', 'recv_packet', 'ibc' ),
       ( '/ibc.core.connection.v1.MsgConnectionOpenInit', 'ibc_connection', 'connection_open_init', 'ibc' ),
       ( '/ibc.core.channel.v1.MsgAcknowledgement', 'ibc_channel', 'acknowledgement', 'ibc' ),
       ( '/provenance.attribute.v1.MsgAddAttributeRequest', 'attribute', 'add_attribute', 'account' ),
       ( '/provenance.attribute.v1.MsgDeleteAttributeRequest', 'attribute', 'delete_attribute', 'account' ),
       ( '/provenance.metadata.v1.MsgWriteScopeSpecificationRequest', 'metadata', 'write_scope_specification', 'nft' ),
       ( '/provenance.metadata.v1.MsgWriteP8eContractSpecRequest', 'metadata', 'write_p8e_contract_spec', 'nft' ),
       ( '/provenance.metadata.v1.MsgP8eMemorializeContractRequest', 'metadata', 'p8e_memorialize_contract', 'nft' ),
       ( '/cosmos.staking.v1beta1.MsgEditValidator', 'staking', 'edit_validator', 'staking' ),
       ( '/cosmos.distribution.v1beta1.MsgWithdrawDelegatorReward', 'distribution', 'withdraw_delegator_reward', 'staking' ),
       ( '/cosmos.staking.v1beta1.MsgBeginRedelegate', 'staking', 'begin_redelegate', 'staking' ),
       ( '/ibc.applications.transfer.v1.MsgTransfer', 'ibc_transfer', 'transfer', 'transfer' ),
       ( '/provenance.name.v1.MsgDeleteNameRequest', 'name', 'delete_name', null ),
       ( '/provenance.marker.v1.MsgTransferRequest', 'marker', 'transfer', 'transfer' ),
       ( '/ibc.core.channel.v1.MsgChannelCloseInit', 'ibc_channel', 'channel_close_init', 'ibc' ),
       ( '/cosmos.distribution.v1beta1.MsgWithdrawValidatorCommission', 'distribution', 'withdraw_validator_commission', 'staking' ),
       ( '/cosmos.distribution.v1beta1.MsgSetWithdrawAddress', 'distribution', 'set_withdraw_address', 'staking' ),
       ( '/cosmos.gov.v1beta1.MsgDeposit', 'gov', 'deposit', 'governance' ),
       ( '/provenance.metadata.v1.MsgDeleteScopeOwnerRequest', 'metadata', 'delete_scope_owner', 'nft' ),
       ( '/provenance.metadata.v1.MsgWriteScopeRequest', 'metadata', 'write_scope', 'nft' ),
       ( '/cosmwasm.wasm.v1beta1.MsgStoreCode', 'wasm', 'store_code', 'smart_contract' ),
       ( '/cosmwasm.wasm.v1beta1.MsgMigrateContract', 'wasm', 'migrate_contract', 'smart_contract' ),
       ( '/cosmwasm.wasm.v1beta1.MsgInstantiateContract', 'wasm', 'instantiate_contract', 'smart_contract' ),
       ( '/cosmwasm.wasm.v1beta1.MsgClearAdmin', 'wasm', 'clear_admin', 'smart_contract' ),
       ( '/cosmwasm.wasm.v1beta1.MsgExecuteContract', 'wasm', 'execute_contract', 'smart_contract' ),
       ( '/provenance.metadata.v1.MsgWriteRecordSpecificationRequest', 'metadata', 'write_record_specification', 'nft' ),
       ( '/cosmos.authz.v1beta1.MsgGrant', 'authz', 'grant', 'account' ),
       ( '/cosmos.feegrant.v1beta1.MsgGrantAllowance', 'feegrant', 'grant_allowance', 'account' ),
       ( '/provenance.metadata.v1.MsgWriteSessionRequest', 'metadata', 'write_session', 'nft' ),
       ( '/provenance.metadata.v1.MsgWriteRecordRequest', 'metadata', 'write_record', 'nft' ),
       ( '/cosmwasm.wasm.v1.MsgMigrateContract', 'wasm', 'migrate_contract', 'smart_contract' ),
       ( '/cosmwasm.wasm.v1.MsgInstantiateContract', 'wasm', 'instantiate_contract', 'smart_contract' ),
       ( '/cosmwasm.wasm.v1.MsgStoreCode', 'wasm', 'store_code', 'smart_contract' ),
       ( '/provenance.name.v1.MsgBindNameRequest', 'name', 'bind_name', null ),
       ( '/provenance.marker.v1.MsgCancelRequest', 'marker', 'cancel', 'asset')
ON CONFLICT (proto_type) DO UPDATE
    SET type     = excluded.type,
        module   = excluded.module,
        category = excluded.category;

