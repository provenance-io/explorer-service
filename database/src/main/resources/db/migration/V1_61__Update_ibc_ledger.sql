SELECT 'Modifying IBC ledger table' AS comment;

ALTER TABLE ibc_ledger
    ADD COLUMN IF NOT EXISTS sequence    INT,
    ADD COLUMN IF NOT EXISTS unique_hash VARCHAR(256);

UPDATE ibc_ledger ibc
SET sequence    = q.sequence,
    unique_hash = encode(digest(concat(q.channel_id, q.sequence, q.in_out)::text, 'sha512'::TEXT), 'base64'::TEXT)
FROM (
         SELECT il.id                                                        AS id,
                value::integer                                               AS sequence,
                il.channel_id,
                CASE WHEN il.balance_in IS NOT NULL THEN 'IN' ELSE 'OUT' END AS in_out
         FROM ibc_ledger il,
              jsonb_to_recordset(logs -> 'events') event("type" text, "attributes" jsonb),
              jsonb_to_recordset(event.attributes) attr("key" text, "value" text)
         WHERE event.type IN ('send_packet', 'recv_packet')
           and key = 'packet_sequence') AS q
WHERE ibc.id = q.id;

DROP INDEX IF EXISTS ibc_ledger_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS ibc_ledger_unique_idx ON ibc_ledger (unique_hash);

SELECT 'Creating ibc_ledger_ack table, and Inserting' AS comment;
CREATE TABLE IF NOT EXISTS ibc_ledger_ack
(
    id               SERIAL PRIMARY KEY,
    ibc_ledger_id    INT         NOT NULL,
    ack_type         VARCHAR(64) NOT NULL,
    block_height     INT         NOT NULL,
    tx_hash_id       INT         NOT NULL,
    tx_hash          VARCHAR(64) NOT NULL,
    tx_timestamp     TIMESTAMP   NOT NULL,
    logs             JSONB       NOT NULL,
    changes_effected BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS ibc_ledger_ack_unique_idx ON ibc_ledger_ack (tx_hash_id, ibc_ledger_id, ack_type);

WITH tx_data AS (
    SELECT tm.block_height,
           tm.tx_hash_id,
           tm.tx_hash,
           tc.tx_timestamp,
           event.type,
           CASE WHEN attr.key = 'packet_sequence' THEN attr.value::integer ELSE -1 END                  AS sequence,
           CASE
               WHEN event.type = 'recv_packet' AND attr.key = 'packet_dst_channel' THEN attr.value
               WHEN event.type != 'recv_packet' AND attr.key = 'packet_src_channel' THEN attr.value END AS src_channel,
           logs.events                                                                                  AS logs,
           CASE
               WHEN logs.events::text like '%acknowledge_packet%' THEN 'ACKNOWLEDGEMENT'
               WHEN logs.events::text like '%recv_packet%' THEN 'RECEIVE'
               WHEN logs.events::text like '%timeout_packet%' THEN 'TIMEOUT' END                        AS ack_type,
           CASE
               WHEN logs.events::text like '%fungible_token_packet%'
                   OR logs.events::text like '%refund_receiver%' THEN true
               ELSE false END                                                                           AS changes_effected
    FROM tx_message tm
             JOIN tx_message_type tmt on tm.tx_message_type_id = tmt.id
             JOIN tx_cache tc on tm.tx_hash_id = tc.id,
         jsonb_array_elements(tc.tx_v2 -> 'tx_response' -> 'logs') with ordinality logs("events", idx),
         jsonb_to_recordset(logs.events -> 'events') event("type" text, "attributes" jsonb),
         jsonb_to_recordset(event.attributes) attr("key" text, "value" text)
    WHERE tc.error_code IS NULL
      AND tmt.proto_type IN ('/ibc.core.channel.v1.MsgTimeout', '/ibc.core.channel.v1.MsgAcknowledgement',
                             '/ibc.core.channel.v1.MsgRecvPacket')
      AND logs.idx - 1 = msg_idx
      AND event.type IN ('acknowledge_packet', 'recv_packet', 'timeout_packet')
      AND attr.key IN ('packet_sequence', 'packet_src_channel', 'packet_dst_channel')
    ORDER BY tm.block_height
)
   , noNulls AS (
    SELECT td.block_height,
           td.tx_hash_id,
           td.tx_hash,
           td.tx_timestamp,
           td.type,
           td.logs,
           td.ack_type,
           td.changes_effected,
           MAX(td.sequence)    AS seq,
           MAX(td.src_channel) AS src_chann
    FROM tx_data td
    GROUP BY td.block_height, td.tx_hash_id, td.tx_hash, td.tx_timestamp, td.type, td.logs, td.ack_type,
             td.changes_effected
)
   , chann AS (
    SELECT nN.*,
           CASE WHEN nN.type = 'recv_packet' THEN 'IN' ELSE 'OUT' END AS movement,
           ic.id                                                      AS channelId
    FROM noNulls nN
             JOIN ibc_channel ic ON nN.src_chann = ic.src_channel
)
INSERT
INTO ibc_ledger_ack (block_height, tx_hash_id, tx_hash, tx_timestamp, ibc_ledger_id, logs, ack_type, changes_effected)
SELECT chann.block_height,
       chann.tx_hash_id,
       chann.tx_hash,
       chann.tx_timestamp,
       il.id,
       chann.logs,
       chann.ack_type,
       chann.changes_effected
FROM chann
         JOIN ibc_ledger il ON chann.channelId = il.channel_id
    AND chann.seq = il.sequence
    AND (CASE WHEN chann.movement = 'IN' THEN il.balance_in IS NOT NULL ELSE il.balance_out IS NOT NULL END)
WHERE il.tx_hash_id != chann.tx_hash_id
ON CONFLICT (tx_hash_id, ibc_ledger_id, ack_type) DO NOTHING;


WITH ack AS (
    SELECT 'ack'                                                              AS in_out,
           il.id                                                              AS il_id,
           bool_or(ila.changes_effected IS NOT NULL)                          AS acknowledged,
           bool_or(ila.changes_effected IS NOT NULL AND ila.changes_effected) AS success
    FROM ibc_ledger il
             LEFT JOIN ibc_ledger_ack ila ON il.id = ila.ibc_ledger_id
    WHERE il.balance_out IS NOT NULL
    GROUP BY il.id, in_out
),
     recv AS (SELECT 'recv'                      AS in_out,
                     il.id                       AS il_id,
                     true                        AS acknowledged,
                     bool_or(CASE
                                 WHEN (event.type = 'fungible_token_packet' AND attr.key = 'success' AND
                                       attr.value = 'true') OR (event.type = 'transfer') THEN true
                                 ELSE FALSE END) AS success
              FROM ibc_ledger il,
                   jsonb_to_recordset(logs -> 'events') event("type" text, "attributes" jsonb),
                   jsonb_to_recordset(event.attributes) attr("key" text, "value" text)
              WHERE il.balance_in IS NOT NULL
              GROUP BY il.id, in_out
     )
UPDATE ibc_ledger upd
SET acknowledged = q.acknowledged,
    ack_success  = q.success
FROM (SELECT * from ack UNION ALL SELECT * FROM recv order by il_id) q
WHERE upd.id = q.il_id;

SELECT 'Dropping columns from ibc_ledger' AS comment;

ALTER TABLE ibc_ledger

    DROP COLUMN IF EXISTS ack_logs,
    DROP COLUMN IF EXISTS ack_block_height,
    DROP COLUMN IF EXISTS ack_tx_hash_id,
    DROP COLUMN IF EXISTS ack_tx_hash,
    DROP COLUMN IF EXISTS ack_tx_timestamp;

SELECT 'Creating table tx_ibc and inserting' AS comment;

CREATE TABLE IF NOT EXISTS tx_ibc
(
    id           SERIAL PRIMARY KEY,
    block_height INT          NOT NULL,
    tx_hash_id   INT          NOT NULL,
    tx_hash      VARCHAR(64)  NOT NULL,
    client       VARCHAR(128) NULL,
    channel_id   INT          NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS tx_ibc_unique_idx ON tx_ibc (tx_hash_id, client, COALESCE(channel_id,-1));
CREATE INDEX IF NOT EXISTS tx_ibc_client_idx ON tx_ibc (client);
CREATE INDEX IF NOT EXISTS tx_ibc_channel_id_idx ON tx_ibc (channel_id);

-- This only inserts successful Txs. Will need to run a migration API to insert for failed IBC txs
WITH base AS (
    SELECT tme.block_height,
           tme.tx_hash_id,
           tme.tx_hash,
           tmt.proto_type,
           tme.event_type,
           tmea.attr_key,
           tmea.attr_value,
           CASE
               WHEN tmt.proto_type = '/ibc.core.client.v1.MsgUpdateClient' AND
                    tme.event_type = 'update_client' AND tmea.attr_key = 'client_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.client.v1.MsgCreateClient' AND
                    tme.event_type = 'create_client' AND tmea.attr_key = 'client_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.connection.v1.MsgConnectionOpenConfirm' AND
                    tme.event_type = 'connection_open_confirm' AND tmea.attr_key = 'client_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.connection.v1.MsgConnectionOpenTry' AND
                    tme.event_type = 'connection_open_try' AND tmea.attr_key = 'client_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.connection.v1.MsgConnectionOpenInit' AND
                    tme.event_type = 'connection_open_init' AND tmea.attr_key = 'client_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.connection.v1.MsgConnectionOpenAck' AND
                    tme.event_type = 'connection_open_ack' AND tmea.attr_key = 'client_id' THEN tmea.attr_value
               END                          AS client,
           CASE
               WHEN tmt.proto_type = '/ibc.applications.transfer.v1.MsgTransfer' AND
                    tme.event_type = 'send_packet' AND tmea.attr_key = 'packet_src_port' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgChannelOpenInit' AND
                    tme.event_type = 'channel_open_init' AND tmea.attr_key = 'port_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgChannelOpenTry' AND
                    tme.event_type = 'channel_open_try' AND tmea.attr_key = 'port_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgChannelOpenAck' AND
                    tme.event_type = 'channel_open_ack' AND tmea.attr_key = 'port_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgChannelOpenConfirm' AND
                    tme.event_type = 'channel_open_confirm' AND tmea.attr_key = 'port_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgChannelCloseInit' AND
                    tme.event_type = 'channel_close_init' AND tmea.attr_key = 'port_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgRecvPacket' AND
                    tme.event_type = 'recv_packet' AND
                    tmea.attr_key = 'packet_dst_port' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgTimeout' AND
                    tme.event_type = 'timeout_packet' AND
                    tmea.attr_key = 'packet_src_port' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgAcknowledgement' AND
                    tme.event_type = 'acknowledge_packet' AND tmea.attr_key = 'packet_src_port'
                   THEN tmea.attr_value END AS src_port,
           CASE
               WHEN tmt.proto_type = '/ibc.applications.transfer.v1.MsgTransfer' AND
                    tme.event_type = 'send_packet' AND tmea.attr_key = 'packet_src_channel'
                   THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgChannelOpenInit' AND
                    tme.event_type = 'channel_open_init' AND tmea.attr_key = 'channel_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgChannelOpenTry' AND
                    tme.event_type = 'channel_open_try' AND tmea.attr_key = 'channel_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgChannelOpenAck' AND
                    tme.event_type = 'channel_open_ack' AND tmea.attr_key = 'channel_id' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgChannelOpenConfirm' AND
                    tme.event_type = 'channel_open_confirm' AND tmea.attr_key = 'channel_id'
                   THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgChannelCloseInit' AND
                    tme.event_type = 'channel_close_init' AND tmea.attr_key = 'channel_id'
                   THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgRecvPacket' AND
                    tme.event_type = 'recv_packet' AND
                    tmea.attr_key = 'packet_dst_channel' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgTimeout' AND
                    tme.event_type = 'timeout_packet' AND
                    tmea.attr_key = 'packet_src_channel' THEN tmea.attr_value
               WHEN tmt.proto_type = '/ibc.core.channel.v1.MsgAcknowledgement' AND
                    tme.event_type = 'acknowledge_packet' AND tmea.attr_key = 'packet_src_channel'
                   THEN tmea.attr_value END AS src_channel
    FROM tx_msg_event tme
             JOIN tx_message_type tmt ON tme.tx_msg_type_id = tmt.id
             JOIN tx_msg_event_attr tmea ON tme.id = tmea.tx_msg_event_id
    WHERE tmt.proto_type IN ('/ibc.applications.transfer.v1.MsgTransfer',
                             '/ibc.core.channel.v1.MsgChannelOpenInit',
                             '/ibc.core.channel.v1.MsgChannelOpenTry',
                             '/ibc.core.channel.v1.MsgChannelOpenAck',
                             '/ibc.core.channel.v1.MsgChannelOpenConfirm',
                             '/ibc.core.channel.v1.MsgChannelCloseInit',
                             '/ibc.core.channel.v1.MsgRecvPacket',
                             '/ibc.core.channel.v1.MsgTimeout',
                             '/ibc.core.channel.v1.MsgAcknowledgement',
                             '/ibc.core.client.v1.MsgUpdateClient',
                             '/ibc.core.client.v1.MsgCreateClient',
                             '/ibc.core.connection.v1.MsgConnectionOpenConfirm',
                             '/ibc.core.connection.v1.MsgConnectionOpenTry',
                             '/ibc.core.connection.v1.MsgConnectionOpenInit',
                             '/ibc.core.connection.v1.MsgConnectionOpenAck'
        )
    ORDER BY tme.block_height, event_type),
     agg AS (
         SELECT base.block_height,
                base.tx_hash_id,
                base.tx_hash,
                max(base.client)      AS client,
                max(base.src_port)    AS src_port,
                max(base.src_channel) AS src_channel
         FROM base
         GROUP BY base.block_height, base.tx_hash_id, base.tx_hash
         order by block_height)
INSERT
INTO tx_ibc (block_height, tx_hash_id, tx_hash, client, channel_id)
SELECT agg.block_height,
       agg.tx_hash_id,
       agg.tx_hash,
       COALESCE(agg.client, ic.client),
       ic.id
FROM agg
         LEFT JOIN ibc_channel ic ON agg.src_port = ic.src_port AND agg.src_channel = ic.src_channel
ON CONFLICT (tx_hash_id, client, COALESCE(channel_id,-1)) DO NOTHING;

SELECT 'Creating table ibc_relayer and inserting' AS comment;

CREATE TABLE IF NOT EXISTS ibc_relayer
(
    id         SERIAL PRIMARY KEY,
    client     VARCHAR(128) NOT NULL,
    channel_id INT          NULL,
    address_id INT          NOT NULL,
    address    VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ibc_relayer_unique_idx ON ibc_relayer (client, COALESCE(channel_id,-1), address_id);

WITH base AS (
    SELECT ti.tx_hash_id,
           ti.client,
           ti.channel_id,
           sj.signature_id
    FROM tx_ibc ti
             JOIN tx_message tm ON ti.tx_hash_id = tm.tx_hash_id
             JOIN tx_message_type tmt ON tm.tx_message_type_id = tmt.id
             JOIN signature_join sj ON sj.join_type = 'TRANSACTION' AND ti.tx_hash = sj.join_key
    WHERE tmt.proto_type IN (
                             '/ibc.core.channel.v1.MsgChannelOpenAck',
                             '/ibc.core.channel.v1.MsgChannelCloseInit',
                             '/ibc.core.channel.v1.MsgTimeout',
                             '/ibc.core.channel.v1.MsgAcknowledgement',
                             '/ibc.core.channel.v1.MsgChannelOpenConfirm',
                             '/ibc.core.channel.v1.MsgChannelOpenTry',
                             '/ibc.core.channel.v1.MsgRecvPacket',
                             '/ibc.core.channel.v1.MsgChannelOpenInit',
                             '/ibc.core.client.v1.MsgUpdateClient',
                             '/ibc.core.client.v1.MsgCreateClient',
                             '/ibc.core.connection.v1.MsgConnectionOpenConfirm',
                             '/ibc.core.connection.v1.MsgConnectionOpenTry',
                             '/ibc.core.connection.v1.MsgConnectionOpenInit',
                             '/ibc.core.connection.v1.MsgConnectionOpenAck'
        )
    GROUP BY ti.tx_hash_id, ti.client, ti.channel_id, sj.signature_id
)
INSERT
INTO ibc_relayer (client, channel_id, address_id, address)
SELECT base.client,
       base.channel_id,
       a.id,
       a.account_address
FROM base
         JOIN signature_join sj ON sj.join_type = 'ACCOUNT' AND base.signature_id = sj.signature_id
         JOIN account a ON sj.join_key = a.account_address
GROUP BY base.client, base.channel_id, a.id
ON CONFLICT (client, COALESCE(channel_id,-1), address_id) DO NOTHING;
