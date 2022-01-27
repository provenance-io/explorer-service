SELECT 'Adding attr_hash to tx_msg_event_attr' AS comment;
ALTER TABLE tx_msg_event_attr
    ADD COLUMN IF NOT EXISTS attr_idx  INT  DEFAULT 0,
    ADD COLUMN IF NOT EXISTS attr_hash TEXT DEFAULT NULL;

UPDATE tx_msg_event_attr
SET attr_idx = t.rn - 1
FROM (
         SELECT id,
                tx_msg_event_id,
                row_number() OVER (PARTITION BY tx_msg_event_id ORDER BY id) AS rn
         FROM tx_msg_event_attr
     ) t
WHERE t.id = tx_msg_event_attr.id;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE tx_msg_event_attr
SET attr_hash = encode(digest(concat(attr_idx,attr_key,attr_value)::text, 'sha512'::TEXT), 'base64'::TEXT)
WHERE attr_hash IS NULL;

SELECT 'Adding unique indices' AS comment;
CREATE UNIQUE INDEX IF NOT EXISTS ibc_ledger_unique_idx ON ibc_ledger (channel_id, from_address, to_address,
                                                                       denom_trace, balance_in, balance_out,
                                                                       tx_hash_id);

CREATE UNIQUE INDEX IF NOT EXISTS tx_fee_unique_idx ON tx_fee (tx_hash_id, fee_type, marker_id);
CREATE UNIQUE INDEX IF NOT EXISTS tx_message_unique_idx ON tx_message (tx_hash_id, tx_message_hash, msg_idx);
CREATE UNIQUE INDEX IF NOT EXISTS tx_msg_event_unique_idx ON tx_msg_event (tx_hash_id, tx_message_id, event_type);
CREATE UNIQUE INDEX IF NOT EXISTS tx_msg_event_attr_unique_idx ON tx_msg_event_attr (tx_msg_event_id, attr_hash);
CREATE UNIQUE INDEX IF NOT EXISTS tx_address_join_unique_idx ON tx_address_join (tx_hash, address);
CREATE UNIQUE INDEX IF NOT EXISTS tx_marker_join_unique_idx ON tx_marker_join (tx_hash, marker_id);
CREATE UNIQUE INDEX IF NOT EXISTS tx_nft_join_unique_idx ON tx_nft_join (tx_hash, metadata_type, metadata_id);
CREATE UNIQUE INDEX IF NOT EXISTS gov_deposit_unique_idx ON gov_deposit (proposal_id, tx_hash, deposit_type, address_id, denom);
CREATE UNIQUE INDEX IF NOT EXISTS tx_sm_code_unique_idx ON tx_sm_code (tx_hash, sm_code);
CREATE UNIQUE INDEX IF NOT EXISTS tx_sm_contract_unique_idx ON tx_sm_contract (tx_hash, sm_contract_id);
DROP INDEX IF EXISTS signature_join_idx;
CREATE UNIQUE INDEX IF NOT EXISTS signature_join_unique_idx ON signature_join (join_type, join_key, signature_id);
CREATE UNIQUE INDEX IF NOT EXISTS tx_feepayer_unique_no_ids_idx ON tx_feepayer (tx_hash, payer_type, address_id);


SELECT 'Adding new types' AS comment;
DROP TYPE IF EXISTS tx_event CASCADE;
DROP TYPE IF EXISTS tx_msg CASCADE;
DROP TYPE IF EXISTS tx_update CASCADE;
DROP TYPE IF EXISTS block_update CASCADE;

CREATE TYPE tx_event AS
(
    txEvent tx_msg_event,
    txAttrs tx_msg_event_attr[]
);

CREATE TYPE tx_msg AS
(
    txMsg    tx_message,
    txEvents tx_event[]
);

CREATE TYPE tx_update AS
(
    tx               tx_cache,
    txGasFee         tx_gas_cache,
    txFees           tx_fee[],
    txMsgs           tx_msg[],
    singleMsg        tx_single_message_cache[],
    addressJoin      tx_address_join[],
    markerJoin       tx_marker_join[],
    nftJoin          tx_nft_join[],
    proposals        gov_proposal[],
    proposalMonitors proposal_monitor[],
    deposits         gov_deposit[],
    votes            gov_vote[],
    ibcLedgers       ibc_ledger[],
    smCodes          tx_sm_code[],
    smContracts      tx_sm_contract[],
    sigs             signature_join[],
    feepayers        tx_feepayer[]
);

CREATE TYPE block_update AS
(
    blocks         block_cache,
    proposer       block_proposer,
    validatorCache validators_cache,
    txs            tx_update[]
);

SELECT 'Adding tx insert procedure' AS comment;
CREATE OR REPLACE PROCEDURE add_tx(tu tx_update, tx_height INT, timez TIMESTAMP)
    LANGUAGE plpgsql
AS
$$
DECLARE
    tx_id     INT; -- tx id
    tf        tx_fee;
    msg       tx_msg;
    msgId     integer;
    event     tx_event;
    eventId   integer;
    attr      tx_msg_event_attr;
    sm        tx_single_message_cache;
    aj        tx_address_join;
    mj        tx_marker_join;
    nj        tx_nft_join;
    gp        gov_proposal;
    pm        proposal_monitor;
    gd        gov_deposit;
    gv        gov_vote;
    ibcl      ibc_ledger;
    codej     tx_sm_code;
    contractj tx_sm_contract;
    sig       signature_join;
    tfp       tx_feepayer;
BEGIN

    -- Insert tx record, getting id
    INSERT INTO tx_cache(hash, height, gas_wanted, gas_used, tx_timestamp, error_code, codespace, tx_v2)
    VALUES ((tu).tx.hash,
            tx_height,
            (tu).tx.gas_wanted,
            (tu).tx.gas_used,
            timez,
            (tu).tx.error_code,
            (tu).tx.codespace,
            (tu).tx.tx_v2)
    ON CONFLICT (hash) DO NOTHING
    RETURNING id INTO tx_id;

    -- insert gas fee
    INSERT INTO tx_gas_cache(hash, gas_wanted, gas_used, tx_timestamp, fee_amount)
    VALUES ((tu).txGasFee.hash, (tu).txGasFee.gas_wanted, (tu).txGasFee.gas_used, timez, (tu).txGasFee.fee_amount)
    ON CONFLICT (hash) DO NOTHING;
    -- insert fee
    FOREACH tf IN ARRAY (tu).txFees
        LOOP
            INSERT INTO tx_fee(tx_hash, fee_type, marker_id, marker, amount, block_height, tx_hash_id)
            VALUES (tf.tx_hash, tf.fee_type, tf.marker_id, tf.marker, tf.amount, tx_height, tx_id)
            ON CONFLICT (tx_hash_id, fee_type, marker_id) DO NOTHING;
        END LOOP;

    -- insert msgs
    FOREACH msg IN ARRAY (tu).txMsgs
        LOOP
            INSERT INTO tx_message(tx_hash, block_height, tx_hash_id, tx_message_type_id, tx_message_hash,
                                   msg_idx, "tx_message")
            VALUES ((msg).txMsg.tx_hash,
                    tx_height,
                    tx_id,
                    (msg).txMsg.tx_message_type_id,
                    (msg).txMsg.tx_message_hash,
                    (msg).txMsg.msg_idx,
                    (msg).txMsg.tx_message)
            ON CONFLICT (tx_hash_id, tx_message_hash, msg_idx) DO NOTHING
            RETURNING id INTO msgId;
            -- insert events
            FOREACH event IN ARRAY (msg).txEvents
                LOOP
                    INSERT INTO tx_msg_event(tx_hash, block_height, tx_hash_id, tx_message_id, event_type,
                                             tx_msg_type_id)
                    VALUES ((event).txEvent.tx_hash,
                            tx_height,
                            tx_id,
                            msgId,
                            (event).txEvent.event_type,
                            (event).txEvent.tx_msg_type_id)
                    ON CONFLICT (tx_hash_id, tx_message_id, event_type) DO NOTHING
                    RETURNING id INTO eventId;
                    -- insert attributes
                    FOREACH attr IN ARRAY (event).txAttrs
                        LOOP
                            INSERT INTO tx_msg_event_attr(tx_msg_event_id, attr_key, attr_value)
                            VALUES (eventId, attr.attr_key, attr.attr_value)
                            ON CONFLICT (tx_msg_event_id, attr_hash) DO NOTHING;
                        END LOOP;
                END LOOP;
        END LOOP;

    -- insert single msg record
    FOREACH sm IN ARRAY (tu).singleMsg
        LOOP
            INSERT INTO tx_single_message_cache(tx_hash, tx_timestamp, gas_used, tx_message_type)
            VALUES (sm.tx_hash, timez, sm.gas_used, sm.tx_message_type)
            ON CONFLICT (tx_hash) DO NOTHING;
        END LOOP;

    -- insert address join
    FOREACH aj IN ARRAY (tu).addressJoin
        LOOP
            INSERT INTO tx_address_join(block_height, tx_hash, address, tx_hash_id, address_id, address_type)
            VALUES (tx_height, aj.tx_hash, aj.address, tx_id, aj.address_id, aj.address_type)
            ON CONFLICT (tx_hash, address) DO NOTHING;
        END LOOP;

    -- insert marker join
    FOREACH mj IN ARRAY (tu).markerJoin
        LOOP
            INSERT INTO tx_marker_join(block_height, tx_hash, denom, tx_hash_id, marker_id)
            VALUES (tx_height, mj.tx_hash, mj.denom, tx_id, mj.marker_id)
            ON CONFLICT (tx_hash, marker_id) DO NOTHING;
        END LOOP;

    -- insert scope join
    FOREACH nj IN ARRAY (tu).nftJoin
        LOOP
            INSERT INTO tx_nft_join(block_height, tx_hash, tx_hash_id, metadata_id, metadata_type,
                                    metadata_uuid)
            VALUES (tx_height, nj.tx_hash, tx_id, nj.metadata_id, nj.metadata_type, nj.metadata_uuid)
            ON CONFLICT (tx_hash, metadata_type, metadata_id) DO NOTHING;
        END LOOP;

    -- insert proposal
    FOREACH gp IN ARRAY (tu).proposals
        LOOP
            INSERT INTO gov_proposal(proposal_id, proposal_type, address_id, address, is_validator, title,
                                     description,
                                     status, data, content, block_height, tx_hash, tx_timestamp)
            VALUES (gp.proposal_id,
                    gp.proposal_type,
                    gp.address_id,
                    gp.address,
                    gp.is_validator,
                    gp.title,
                    gp.description,
                    gp.status,
                    gp.data,
                    gp.content,
                    tx_height,
                    gp.tx_hash,
                    timez)
            ON CONFLICT (proposal_id) DO UPDATE
                SET status       = gp.status,
                    data         = gp.data,
                    tx_hash      = gp.tx_hash,
                    tx_timestamp = timez,
                    block_height = tx_height;
        END LOOP;
    -- insert monitor
    FOREACH pm IN ARRAY (tu).proposalMonitors
        LOOP
            INSERT INTO proposal_monitor(proposal_id, submitted_height, proposed_completion_height,
                                         voting_end_time, proposal_type, matching_data_hash)
            VALUES (pm.proposal_id,
                    pm.submitted_height,
                    pm.proposed_completion_height,
                    pm.voting_end_time,
                    pm.proposal_type,
                    pm.matching_data_hash)
            ON CONFLICT (proposal_id) DO NOTHING;
        END LOOP;
    -- insert deposit
    FOREACH gd IN ARRAY (tu).deposits
        LOOP
            INSERT INTO gov_deposit(proposal_id, address_id, address, block_height, tx_hash, tx_timestamp,
                                    is_validator, deposit_type, amount, denom)
            VALUES (gd.proposal_id,
                    gd.address_id,
                    gd.address,
                    tx_height,
                    gd.tx_hash,
                    timez,
                    gd.is_validator,
                    gd.deposit_type,
                    gd.amount,
                    gd.denom)
            ON CONFLICT (proposal_id, tx_hash, deposit_type, address_id, denom) DO NOTHING;
        END LOOP;
    -- insert vote
    FOREACH gv IN ARRAY (tu).votes
        LOOP
            INSERT INTO gov_vote(proposal_id, address_id, address, block_height, tx_hash, tx_timestamp,
                                 is_validator, vote)
            VALUES (gv.proposal_id,
                    gv.address_id,
                    gv.address,
                    tx_height,
                    gv.tx_hash,
                    timez,
                    gv.is_validator,
                    gv.vote)
            ON CONFLICT (proposal_id, address_id) DO UPDATE
                SET vote         = gv.vote,
                    block_height = tx_height,
                    tx_hash      = gv.tx_hash,
                    tx_timestamp = timez
            WHERE tx_height > gov_vote.block_height;
        END LOOP;

    -- insert ledger
    FOREACH ibcl IN ARRAY (tu).ibcLedgers
        LOOP
            INSERT INTO ibc_ledger(dst_chain_name, channel_id, denom, denom_trace, balance_in, balance_out,
                                   from_address, to_address, pass_through_address_id, pass_through_address,
                                   logs, block_height, tx_hash_id, tx_hash, tx_timestamp, acknowledged,
                                   ack_success)
            VALUES (ibcl.dst_chain_name,
                    ibcl.channel_id,
                    ibcl.denom,
                    ibcl.denom_trace,
                    ibcl.balance_in,
                    ibcl.balance_out,
                    ibcl.from_address,
                    ibcl.to_address,
                    ibcl.pass_through_address_id,
                    ibcl.pass_through_address,
                    ibcl.logs,
                    ibcl.block_height,
                    CASE WHEN ibcl.tx_hash_id < 0 THEN tx_id ELSE ibcl.tx_hash_id END,
                    ibcl.tx_hash,
                    ibcl.tx_timestamp,
                    ibcl.acknowledged,
                    ibcl.ack_success)
            ON CONFLICT (channel_id, from_address, to_address, denom_trace, balance_in, balance_out, tx_hash_id)
                DO UPDATE
                SET acknowledged     = true,
                    ack_success      = ibcl.ack_success,
                    ack_logs         = ibcl.ack_logs,
                    ack_block_height = tx_height,
                    ack_tx_hash_id   = tx_id,
                    ack_tx_hash      = ibcl.ack_tx_hash,
                    ack_tx_timestamp = timez;
        END LOOP;

    -- insert sm join
    FOREACH codej IN ARRAY (tu).smCodes
        LOOP
            INSERT INTO tx_sm_code(block_height, tx_hash, tx_hash_id, sm_code)
            VALUES (tx_height, codej.tx_hash, tx_id, codej.sm_code)
            ON CONFLICT (tx_hash, sm_code) DO NOTHING;
        END LOOP;

    FOREACH contractj IN ARRAY (tu).smContracts
        LOOP
            INSERT INTO tx_sm_contract(block_height, tx_hash, tx_hash_id, sm_contract_id, sm_contract_address)
            VALUES (tx_height, contractj.tx_hash, tx_id, contractj.sm_contract_id, contractj.sm_contract_address)
            ON CONFLICT (tx_hash, sm_contract_id) DO NOTHING;
        END LOOP;

    -- insert sig join
    FOREACH sig IN ARRAY (tu).sigs
        LOOP
            INSERT INTO signature_join(join_type, join_key, signature_id)
            VALUES (sig.join_type, tx_id, sig.signature_id)
            ON CONFLICT (join_type, join_key, signature_id) DO NOTHING;
        END LOOP;

    -- insert feepayer
    FOREACH tfp IN ARRAY (tu).feepayers
        LOOP
            INSERT INTO tx_feepayer(block_height, tx_hash_id, tx_hash, payer_type, address_id, address)
            VALUES (tx_height, tx_id, tfp.tx_hash, tfp.payer_type, tfp.address_id, tfp.address)
            ON CONFLICT (tx_hash, payer_type, address_id) DO NOTHING;
        END LOOP;

    RAISE INFO 'UPDATED tx';
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error saving tx %', (tu).tx.hash;
END;
$$;

SELECT 'Adding block insert procedure' AS comment;
CREATE OR REPLACE PROCEDURE add_block(bd block_update)
    LANGUAGE plpgsql
AS
$$
DECLARE
    tx_height INT; --block height
    timez     TIMESTAMP; -- block timestamp
    tu        tx_update;
BEGIN

    SELECT (bd).blocks.height INTO tx_height;
    SELECT (bd).blocks.block_timestamp INTO timez;

    -- insert block
    INSERT INTO block_cache(height, tx_count, block_timestamp, block, last_hit, hit_count)
    VALUES (tx_height,
            (bd).blocks.tx_count,
            timez,
            (bd).blocks.block,
            (bd).blocks.last_hit,
            (bd).blocks.hit_count)
    ON CONFLICT (height) DO NOTHING;
    -- insert block tx count
    INSERT INTO block_tx_count_cache(block_height, block_timestamp, tx_count)
    VALUES (tx_height, timez, (bd).blocks.tx_count)
    ON CONFLICT (block_height) DO NOTHING;
    -- insert block proposer fee
    INSERT INTO block_proposer(block_height, block_timestamp, proposer_operator_address, min_gas_fee, block_latency)
    VALUES (tx_height,
            timez,
            (bd).proposer.proposer_operator_address,
            (bd).proposer.min_gas_fee,
            (bd).proposer.block_latency)
    ON CONFLICT (block_height) DO NOTHING;
    -- insert validator cache
    INSERT INTO validators_cache(height, validators, last_hit, hit_count)
    VALUES (tx_height, (bd).validatorCache.validators, (bd).validatorCache.last_hit, (bd).validatorCache.hit_count)
    ON CONFLICT (height) DO NOTHING;

    -- for each tx
    FOREACH tu IN ARRAY (bd).txs
        LOOP
            CALL add_tx(tu, tx_height, timez);
        END LOOP;

    RAISE INFO 'UPDATED block';
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error saving block %', (bd).blocks.height;
END;
$$;
