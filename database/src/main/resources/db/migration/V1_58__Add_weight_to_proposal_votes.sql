SELECT 'Updating gov_vote table' AS comment;

ALTER TABLE gov_vote
ADD COLUMN IF NOT EXISTS weight DECIMAL(3,2) NOT NULL DEFAULT 1.00;

DROP INDEX IF EXISTS gov_vote_proposal_id_address_id_idx;

CREATE UNIQUE INDEX IF NOT EXISTS gov_vote_unique_idx ON gov_vote(proposal_id, address_id, vote);

SELECT 'Updating insert procedures' AS comment;
-- By breaking down the pieces into separate functions, it allows us to modify a piece without touching the whole procedure
create or replace function insert_tx_cache_returning_id(tx tx_cache, tx_height integer, timez timestamp without time zone, OUT txId integer)
    LANGUAGE plpgsql AS
$$
BEGIN
        WITH t AS (
            INSERT INTO tx_cache (hash, height, gas_wanted, gas_used, tx_timestamp, error_code, codespace, tx_v2)
                VALUES (tx.hash,
                        tx_height,
                        tx.gas_wanted,
                        tx.gas_used,
                        timez,
                        tx.error_code,
                        tx.codespace,
                        tx.tx_v2)
                ON CONFLICT (hash) DO NOTHING
                RETURNING id
        )
        SELECT * FROM t
        UNION SELECT id FROM tx_cache WHERE hash = tx.hash
        INTO txId;
END
$$;


create or replace procedure insert_tx_gas_cache(txGasFee tx_gas_cache, timez timestamp without time zone)
    language plpgsql as
$$
BEGIN
    INSERT INTO tx_gas_cache(hash, gas_wanted, gas_used, tx_timestamp, fee_amount)
    VALUES (txGasFee.hash, txGasFee.gas_wanted, txGasFee.gas_used, timez, txGasFee.fee_amount)
    ON CONFLICT (hash) DO NOTHING;
END;
$$;

create or replace procedure insert_validator_market_rate(
    valMarketRate validator_market_rate, timez timestamp without time zone, tx_height integer, tx_id integer)
    language plpgsql as
$$
BEGIN
    INSERT INTO validator_market_rate(block_height, block_timestamp, proposer_address, tx_hash_id, tx_hash, market_rate,
                                      success)
    VALUES (tx_height, timez, valMarketRate.proposer_address, tx_id, valMarketRate.tx_hash, valMarketRate.market_rate,
            valMarketRate.success)
    ON CONFLICT (tx_hash) DO NOTHING;
END;
$$;

create or replace procedure insert_tx_fees(txFees tx_fee[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    tf tx_fee;
BEGIN
    FOREACH tf IN ARRAY txFees
        LOOP
            INSERT INTO tx_fee(tx_hash, fee_type, marker_id, marker, amount, block_height, tx_hash_id, msg_type)
            VALUES (tf.tx_hash, tf.fee_type, tf.marker_id, tf.marker, tf.amount, tx_height, tx_id, tf.msg_type)
            ON CONFLICT (tx_hash_id, fee_type, COALESCE(msg_type, ''), marker_id) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_tx_msg_event_attr(txAttrs tx_msg_event_attr[], eventId integer)
    language plpgsql as
$$
DECLARE
    attr tx_msg_event_attr;
BEGIN
    FOREACH attr IN ARRAY txAttrs
        LOOP
            INSERT INTO tx_msg_event_attr(tx_msg_event_id, attr_key, attr_value, attr_idx, attr_hash)
            VALUES (eventId, attr.attr_key, attr.attr_value, attr.attr_idx, attr.attr_hash)
            ON CONFLICT (tx_msg_event_id, attr_hash) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_tx_msg_event(txEvents tx_event[], msgId integer, tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    event   tx_event;
    eventId integer;
BEGIN
    FOREACH event IN ARRAY txEvents
        LOOP
            WITH e AS (
                INSERT INTO tx_msg_event (tx_hash, block_height, tx_hash_id, tx_message_id, event_type, tx_msg_type_id)
                    VALUES ((event).txEvent.tx_hash,
                            tx_height,
                            tx_id,
                            msgId,
                            (event).txEvent.event_type,
                            (event).txEvent.tx_msg_type_id)
                    ON CONFLICT (tx_hash_id, tx_message_id, event_type) DO NOTHING
                    RETURNING id
            )
            SELECT *
            FROM e
            UNION
            SELECT id
            FROM tx_msg_event
            WHERE tx_hash_id = tx_id
              AND tx_message_id = msgId
              AND event_type = (event).txEvent.event_type
            INTO eventId;
            CALL insert_tx_msg_event_attr((event).txattrs, eventId);
        END LOOP;
END;
$$;

create or replace procedure insert_tx_msgs(txMsgs tx_msg[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    msg   tx_msg;
    msgId integer;
BEGIN
    FOREACH msg IN ARRAY txMsgs
        LOOP
            WITH m AS (
                INSERT INTO tx_message (tx_hash, block_height, tx_hash_id, tx_message_type_id, tx_message_hash,
                                        msg_idx, "tx_message")
                    VALUES ((msg).txMsg.tx_hash,
                            tx_height,
                            tx_id,
                            (msg).txMsg.tx_message_type_id,
                            (msg).txMsg.tx_message_hash,
                            (msg).txMsg.msg_idx,
                            (msg).txMsg.tx_message)
                    ON CONFLICT (tx_hash_id, tx_message_hash, msg_idx) DO NOTHING
                    RETURNING id
            )
            SELECT *
            FROM m
            UNION
            SELECT id
            FROM tx_message
            WHERE tx_hash_id = tx_id
              AND tx_message_hash = (msg).txMsg.tx_message_hash
              AND msg_idx = (msg).txMsg.msg_idx
            INTO msgId;
            -- insert events
            CALL insert_tx_msg_event((msg).txevents, msgId, tx_height, tx_id);
        END LOOP;
END;
$$;

create or replace procedure insert_tx_single_message_cache(singleMsg tx_single_message_cache[],
                                                           timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    sm tx_single_message_cache;
BEGIN
    FOREACH sm IN ARRAY singleMsg
        LOOP
            INSERT INTO tx_single_message_cache(tx_hash, tx_timestamp, gas_used, tx_message_type)
            VALUES (sm.tx_hash, timez, sm.gas_used, sm.tx_message_type)
            ON CONFLICT (tx_hash) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_tx_address_join(addressJoin tx_address_join[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    aj tx_address_join;
BEGIN
    FOREACH aj IN ARRAY addressJoin
        LOOP
            INSERT INTO tx_address_join(block_height, tx_hash, address, tx_hash_id, address_id, address_type)
            VALUES (tx_height, aj.tx_hash, aj.address, tx_id, aj.address_id, aj.address_type)
            ON CONFLICT (tx_hash, address) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_tx_marker_join(markerJoin tx_marker_join[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    mj tx_marker_join;
BEGIN
    FOREACH mj IN ARRAY markerJoin
        LOOP
            INSERT INTO tx_marker_join(block_height, tx_hash, denom, tx_hash_id, marker_id)
            VALUES (tx_height, mj.tx_hash, mj.denom, tx_id, mj.marker_id)
            ON CONFLICT (tx_hash, marker_id) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_tx_nft_join(nftJoin tx_nft_join[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    nj tx_nft_join;
BEGIN
    FOREACH nj IN ARRAY nftJoin
        LOOP
            INSERT INTO tx_nft_join(block_height, tx_hash, tx_hash_id, metadata_id, metadata_type, metadata_uuid)
            VALUES (tx_height, nj.tx_hash, tx_id, nj.metadata_id, nj.metadata_type, nj.metadata_uuid)
            ON CONFLICT (tx_hash, metadata_type, metadata_id) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_gov_proposal(
    proposals gov_proposal[], tx_height integer, tx_id integer, timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    gp gov_proposal;
BEGIN
    FOREACH gp IN ARRAY proposals
        LOOP
            INSERT INTO gov_proposal(proposal_id, proposal_type, address_id, address, is_validator, title, description,
                                     status,
                                     data, content, block_height, tx_hash, tx_timestamp, tx_hash_id)
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
                    timez,
                    tx_id)
            ON CONFLICT (proposal_id) DO UPDATE SET status       = gp.status,
                                                    data         = gp.data,
                                                    tx_hash      = gp.tx_hash,
                                                    tx_timestamp = timez,
                                                    block_height = tx_height;
        END LOOP;
END;
$$;

create or replace procedure insert_proposal_monitor(proposalMonitors proposal_monitor[])
    language plpgsql as
$$
DECLARE
    pm proposal_monitor;
BEGIN
    FOREACH pm IN ARRAY proposalMonitors
        LOOP
            INSERT INTO proposal_monitor(proposal_id, submitted_height, proposed_completion_height, voting_end_time,
                                         proposal_type, matching_data_hash)
            VALUES (pm.proposal_id,
                    pm.submitted_height,
                    pm.proposed_completion_height,
                    pm.voting_end_time,
                    pm.proposal_type,
                    pm.matching_data_hash)
            ON CONFLICT (proposal_id) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_gov_deposit(
    deposits gov_deposit[], tx_height integer, tx_id integer, timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    gd gov_deposit;
BEGIN
    FOREACH gd IN ARRAY deposits
        LOOP
            INSERT INTO gov_deposit(proposal_id, address_id, address, block_height, tx_hash, tx_timestamp, is_validator,
                                    deposit_type, amount, denom, tx_hash_id)
            VALUES (gd.proposal_id,
                    gd.address_id,
                    gd.address,
                    tx_height,
                    gd.tx_hash,
                    timez,
                    gd.is_validator,
                    gd.deposit_type,
                    gd.amount,
                    gd.denom,
                    tx_id)
            ON CONFLICT (proposal_id, tx_hash, deposit_type, address_id, denom) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_gov_vote(votes gov_vote[], tx_height integer, tx_id integer, timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    gvaa integer[];
    gva  integer[];
    gv   gov_vote;
BEGIN
    SELECT DISTINCT ARRAY [(e::gov_vote).proposal_id, (e::gov_vote).address_id] arr
    FROM unnest(votes::gov_vote[]) AS e
    INTO gvaa;

    RAISE INFO 'distinct %', gvaa;

    IF gvaa IS NOT NULL THEN
        FOREACH gva SLICE 1 IN ARRAY gvaa
            LOOP
                DELETE FROM gov_vote WHERE proposal_id = gva[1] AND address_id = gva[2] AND tx_height > block_height;
            END LOOP;
    END IF;

    FOREACH gv IN ARRAY votes
        LOOP
            INSERT INTO gov_vote(proposal_id, address_id, address, block_height, tx_hash, tx_timestamp,
                                 is_validator, vote, tx_hash_id, weight)
            VALUES (gv.proposal_id,
                    gv.address_id,
                    gv.address,
                    tx_height,
                    gv.tx_hash,
                    timez,
                    gv.is_validator,
                    gv.vote,
                    tx_id,
                    gv.weight)
            ON CONFLICT (proposal_id, address_id, vote) DO UPDATE
                SET weight       = gv.weight,
                    block_height = tx_height,
                    tx_hash      = gv.tx_hash,
                    tx_timestamp = timez
            WHERE tx_height > gov_vote.block_height;
        END LOOP;
END;
$$;

create or replace procedure insert_ibc_ledger(ibcLedgers ibc_ledger[], tx_height integer, tx_id integer,
                                              timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    ibcl ibc_ledger;
BEGIN
    FOREACH ibcl IN ARRAY ibcLedgers
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
END;
$$;

create or replace procedure insert_tx_sm_code(smCodes tx_sm_code[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    codej tx_sm_code;
BEGIN
    FOREACH codej IN ARRAY smCodes
        LOOP
            INSERT INTO tx_sm_code(block_height, tx_hash, tx_hash_id, sm_code)
            VALUES (tx_height, codej.tx_hash, tx_id, codej.sm_code)
            ON CONFLICT (tx_hash, sm_code) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_tx_sm_contract(smContracts tx_sm_contract[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    contractj tx_sm_contract;
BEGIN
    FOREACH contractj IN ARRAY smContracts
        LOOP
            INSERT INTO tx_sm_contract(block_height, tx_hash, tx_hash_id, sm_contract_id, sm_contract_address)
            VALUES (tx_height, contractj.tx_hash, tx_id, contractj.sm_contract_id, contractj.sm_contract_address)
            ON CONFLICT (tx_hash, sm_contract_id) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_signature_join(sigs signature_join[])
    language plpgsql as
$$
DECLARE
    sig signature_join;
BEGIN
    FOREACH sig IN ARRAY sigs
        LOOP
            INSERT INTO signature_join(join_type, join_key, signature_id)
            VALUES (sig.join_type, sig.join_key, sig.signature_id)
            ON CONFLICT (join_type, join_key, signature_id) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_tx_feepayer(feepayers tx_feepayer[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    tfp tx_feepayer;
BEGIN
    FOREACH tfp IN ARRAY feepayers
        LOOP
            INSERT INTO tx_feepayer(block_height, tx_hash_id, tx_hash, payer_type, address_id, address)
            VALUES (tx_height, tx_id, tfp.tx_hash, tfp.payer_type, tfp.address_id, tfp.address)
            ON CONFLICT (tx_hash, payer_type, address_id) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure add_tx(tu tx_update, tx_height integer, timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    tx_id INT; -- tx id
BEGIN

    -- Insert tx record, getting id
    SELECT insert_tx_cache_returning_id((tu).tx, tx_height, timez) INTO tx_id;

    -- insert gas fee
    CALL insert_tx_gas_cache((tu).txGasFee, timez);

    -- insert market rate
    CALL insert_validator_market_rate((tu).valMarketRate, timez, tx_height, tx_id);

    -- insert fees
    CALL insert_tx_fees((tu).txFees, tx_height, tx_id);

    -- insert msgs
    CALL insert_tx_msgs((tu).txMsgs, tx_height, tx_id);

    -- insert single msg record
    CALL insert_tx_single_message_cache((tu).singlemsg, timez);

    -- insert address join
    CALL insert_tx_address_join((tu).addressJoin, tx_height, tx_id);

    -- insert marker join
    CALL insert_tx_marker_join((tu).markerjoin, tx_height, tx_id);

    -- insert scope join
    CALL insert_tx_nft_join((tu).nftJoin, tx_height, tx_id);

    -- insert proposal
    CALL insert_gov_proposal((tu).proposals, tx_height, tx_id, timez);

    -- insert monitor
    CALL insert_proposal_monitor((tu).proposalmonitors);

    -- insert deposit
    CALL insert_gov_deposit((tu).deposits, tx_height, tx_id, timez);

    -- insert vote
    CALL insert_gov_vote((tu).votes, tx_height, tx_id, timez);

    -- insert ledger
    CALL insert_ibc_ledger((tu).ibcLedgers, tx_height, tx_id, timez);

    -- insert sm join
    CALL insert_tx_sm_code((tu).smCodes, tx_height, tx_id);

    CALL insert_tx_sm_contract((tu).smContracts, tx_height, tx_id);

    -- insert sig join
    CALL insert_signature_join((tu).sigs);

    -- insert feepayer
    CALL insert_tx_feepayer((tu).feepayers, tx_height, tx_id);

    RAISE INFO 'UPDATED tx';
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error saving tx %', (tu).tx.hash;
END;
$$;


create or replace procedure add_tx_debug(tu tx_update, tx_height integer, timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    tx_id     INT; -- tx id
BEGIN

    -- Insert tx record, getting id
    SELECT insert_tx_cache_returning_id((tu).tx, tx_height, timez) INTO tx_id;

    -- insert gas fee
    CALL insert_tx_gas_cache((tu).txGasFee, timez);

    -- insert market rate
    CALL insert_validator_market_rate((tu).valMarketRate, timez, tx_height, tx_id);

    -- insert fees
    CALL insert_tx_fees((tu).txFees, tx_height, tx_id);

    -- insert msgs
    CALL insert_tx_msgs((tu).txMsgs, tx_height, tx_id);

    -- insert single msg record
    CALL insert_tx_single_message_cache((tu).singlemsg, timez);

    -- insert address join
    CALL insert_tx_address_join((tu).addressJoin, tx_height, tx_id);

    -- insert marker join
    CALL insert_tx_marker_join((tu).markerjoin, tx_height, tx_id);

    -- insert scope join
    CALL insert_tx_nft_join((tu).nftJoin, tx_height, tx_id);

    -- insert proposal
    CALL insert_gov_proposal((tu).proposals, tx_height, tx_id, timez);

    -- insert monitor
    CALL insert_proposal_monitor((tu).proposalmonitors);

    -- insert deposit
    CALL insert_gov_deposit((tu).deposits, tx_height, tx_id, timez);

    -- insert vote
    CALL insert_gov_vote((tu).votes, tx_height, tx_id, timez);

    -- insert ledger
    CALL insert_ibc_ledger((tu).ibcLedgers, tx_height, tx_id, timez);

    -- insert sm join
    CALL insert_tx_sm_code((tu).smCodes, tx_height, tx_id);

    CALL insert_tx_sm_contract((tu).smContracts, tx_height, tx_id);

    -- insert sig join
    CALL insert_signature_join((tu).sigs);

    -- insert feepayer
    CALL insert_tx_feepayer((tu).feepayers, tx_height, tx_id);

    RAISE INFO 'UPDATED tx';
END;
$$;

SELECT 'Inserting weighted votes' AS comment;
WITH base AS (SELECT tc.hash                                                                         AS tx_hash,
                     tc.id                                                                           AS tx_hash_id,
                     tc.tx_timestamp                                                                 AS tx_timestamp,
                     tc.height                                                                       AS block_height,
                     MAX(CASE
                             WHEN event.type = 'proposal_vote' AND msg.key = 'proposal_id'
                                 THEN msg.value::integer END)                                        AS proposalId,
                     MAX(CASE WHEN event.type = 'message' AND msg.key = 'sender' THEN msg.value END) AS address,
                     MAX(CASE
                             WHEN event.type = 'proposal_vote' AND msg.key = 'option'
                                 THEN msg.value END)                                                 AS weightedVote
              FROM tx_cache tc
                       JOIN tx_message tm ON tc.id = tm.tx_hash_id
                       JOIN tx_message_type tmt ON tm.tx_message_type_id = tmt.id,
                   jsonb_to_recordset(tx_v2 -> 'tx_response' -> 'logs') logs("events" jsonb),
                   jsonb_to_recordset(logs.events) event("type" text, "attributes" jsonb),
                   jsonb_to_recordset(event.attributes) msg("key" text, "value" text)
              WHERE tmt.proto_type = '/cosmos.gov.v1beta1.MsgVoteWeighted'
              GROUP BY tc.hash, tc.id, tc.tx_timestamp, tc.height
),
     weighted AS (
         SELECT base.tx_hash_id,
                ((base.weightedVote::jsonb) ->> 'option')::integer       AS option,
                ((base.weightedVote::jsonb) ->> 'weight')::decimal(3, 2) AS weight
         FROM base
     ),
     vote AS (
         SELECT weighted.tx_hash_id,
                CASE
                    WHEN option = 1 THEN 'VOTE_OPTION_YES'
                    WHEN option = 2 THEN 'VOTE_OPTION_ABSTAIN'
                    WHEN option = 3 THEN 'VOTE_OPTION_NO'
                    WHEN option = 4 THEN 'VOTE_OPTION_NO_WITH_VETO'
                    END AS vote
         FROM weighted
     )
INSERT
INTO gov_vote (proposal_id, address_id, address, is_validator, vote, block_height, tx_hash, tx_timestamp, tx_hash_id,
               weight)
SELECT base.proposalId,
       a.id               AS address_id,
       base.address,
       svc.id IS NOT NULL AS is_validator,
       vote.vote,
       base.block_height,
       base.tx_hash,
       base.tx_timestamp,
       base.tx_hash_id,
       weighted.weight
FROM base
         JOIN weighted ON base.tx_hash_id = weighted.tx_hash_id
         JOIN vote ON base.tx_hash_id = vote.tx_hash_id
         JOIN account a ON base.address = a.account_address
         LEFT JOIN staking_validator_cache svc ON a.account_address = svc.account_address;
