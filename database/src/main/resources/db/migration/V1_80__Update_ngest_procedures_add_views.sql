
-- add tx_timestamp to tx_fee, tx_feepayer, tx_message
-- add tx_hash_id to tx_gas_cache
-- update the ingest procedures associated

SELECT 'Update ingest procedures' AS comment;

drop procedure if exists insert_tx_fees(tx_fee[], integer, integer);

create or replace procedure insert_tx_fees(txfees tx_fee[], tx_height integer, tx_id integer, timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    tf tx_fee;
BEGIN

    DELETE FROM tx_fee WHERE tx_hash_id = tx_id;

    FOREACH tf IN ARRAY txFees
        LOOP
            INSERT INTO tx_fee(tx_hash, fee_type, marker_id, marker, amount, block_height, tx_hash_id, msg_type, recipient, orig_fees, tx_timestamp)
            VALUES (tf.tx_hash, tf.fee_type, tf.marker_id, tf.marker, tf.amount, tx_height, tx_id, tf.msg_type, tf.recipient, tf.orig_fees, timez)
            ON CONFLICT (tx_hash_id, fee_type, COALESCE(msg_type, ''), marker_id, COALESCE(recipient, '')) DO UPDATE
                SET
                    amount = tf.amount,
                    orig_fees = tf.orig_fees
            ;
        END LOOP;
END;
$$;

drop procedure if exists insert_tx_feepayer(tx_feepayer[], integer, integer);

create or replace procedure insert_tx_feepayer(feepayers tx_feepayer[], tx_height integer, tx_id integer, timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    tfp tx_feepayer;
BEGIN
    FOREACH tfp IN ARRAY feepayers
        LOOP
            INSERT INTO tx_feepayer(block_height, tx_hash_id, tx_hash, payer_type, address_id, address, tx_timestamp)
            VALUES (tx_height, tx_id, tfp.tx_hash, tfp.payer_type, tfp.address_id, tfp.address, timez)
            ON CONFLICT (tx_hash_id, payer_type, address_id) DO NOTHING;
        END LOOP;
END;
$$;

drop procedure if exists insert_tx_msgs(tx_msg[], integer, integer);

create or replace procedure insert_tx_msgs(txmsgs tx_msg[], tx_height integer, tx_id integer, timez timestamp without time zone)
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
                                        msg_idx, "tx_message", tx_timestamp)
                    VALUES ((msg).txMsg.tx_hash,
                            tx_height,
                            tx_id,
                            (msg).txMsg.tx_message_type_id,
                            (msg).txMsg.tx_message_hash,
                            (msg).txMsg.msg_idx,
                            (msg).txMsg.tx_message,
                            timez)
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

drop procedure if exists insert_tx_gas_cache(tx_gas_cache, timestamp, integer);

create or replace procedure insert_tx_gas_cache(txgasfee tx_gas_cache, timez timestamp without time zone, tx_height integer, tx_id integer)
    language plpgsql as
$$
BEGIN
    INSERT INTO tx_gas_cache(height, hash, gas_wanted, gas_used, tx_timestamp, fee_amount, tx_hash_id)
    VALUES (tx_height, txGasFee.hash, txGasFee.gas_wanted, txGasFee.gas_used, timez, txGasFee.fee_amount, tx_id)
    ON CONFLICT (height, hash) DO UPDATE
        SET processed = false,
            fee_amount = txgasfee.fee_amount;
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
    CALL insert_tx_gas_cache((tu).txGasFee, timez, tx_height, tx_id);

    -- insert market rate
    CALL insert_validator_market_rate((tu).valMarketRate, timez, tx_height, tx_id);

    -- insert fees
    CALL insert_tx_fees((tu).txFees, tx_height, tx_id, timez);

    -- insert msgs
    CALL insert_tx_msgs((tu).txMsgs, tx_height, tx_id, timez);

    -- insert single msg record
    CALL insert_tx_single_message_cache((tu).singlemsg, timez, tx_height);

    -- insert address join
    CALL insert_tx_address_join((tu).addressJoin, tx_height, tx_id);

    -- insert marker join
    CALL insert_tx_marker_join((tu).markerjoin, tx_height, tx_id);

    -- insert scope join
    CALL insert_tx_nft_join((tu).nftJoin, tx_height, tx_id);

    -- insert ibc join
    CALL insert_tx_ibc((tu).ibcJoin, tx_height, tx_id);

    -- insert proposal
    CALL insert_gov_proposal((tu).proposals, tx_height, tx_id, timez);

    -- insert monitor
    CALL insert_proposal_monitor((tu).proposalmonitors);

    -- insert deposit
    CALL insert_gov_deposit((tu).deposits, tx_height, tx_id, timez);

    -- insert vote
    CALL insert_gov_vote((tu).votes, tx_height, tx_id, timez);

    -- insert ledger
    CALL insert_ibc_ledger((tu).ibcLedgers, tx_id);

    -- insert ledger ack
    CALL insert_ibc_ledger_ack((tu).ibcLedgerAcks, tx_id);

    -- insert sm join
    CALL insert_tx_sm_code((tu).smCodes, tx_height, tx_id);

    CALL insert_tx_sm_contract((tu).smContracts, tx_height, tx_id);

    -- insert sig join
    CALL insert_signature_join((tu).sigs);

    -- insert feepayer
    CALL insert_tx_feepayer((tu).feepayers, tx_height, tx_id, timez);

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
    CALL insert_tx_gas_cache((tu).txGasFee, timez, tx_height, tx_id);

    -- insert market rate
    CALL insert_validator_market_rate((tu).valMarketRate, timez, tx_height, tx_id);

    -- insert fees
    CALL insert_tx_fees((tu).txFees, tx_height, tx_id, timez);

    -- insert msgs
    CALL insert_tx_msgs((tu).txMsgs, tx_height, tx_id, timez);

    -- insert single msg record
    CALL insert_tx_single_message_cache((tu).singlemsg, timez, tx_height);

    -- insert address join
    CALL insert_tx_address_join((tu).addressJoin, tx_height, tx_id);

    -- insert marker join
    CALL insert_tx_marker_join((tu).markerjoin, tx_height, tx_id);

    -- insert scope join
    CALL insert_tx_nft_join((tu).nftJoin, tx_height, tx_id);

    -- insert ibc join
    CALL insert_tx_ibc((tu).ibcJoin, tx_height, tx_id);

    -- insert proposal
    CALL insert_gov_proposal((tu).proposals, tx_height, tx_id, timez);

    -- insert monitor
    CALL insert_proposal_monitor((tu).proposalmonitors);

    -- insert deposit
    CALL insert_gov_deposit((tu).deposits, tx_height, tx_id, timez);

    -- insert vote
    CALL insert_gov_vote((tu).votes, tx_height, tx_id, timez);

    -- insert ledger
    CALL insert_ibc_ledger((tu).ibcLedgers, tx_id);

    -- insert ledger ack
    CALL insert_ibc_ledger_ack((tu).ibcLedgerAcks, tx_id);

    -- insert sm join
    CALL insert_tx_sm_code((tu).smCodes, tx_height, tx_id);

    CALL insert_tx_sm_contract((tu).smContracts, tx_height, tx_id);

    -- insert sig join
    CALL insert_signature_join((tu).sigs);

    -- insert feepayer
    CALL insert_tx_feepayer((tu).feepayers, tx_height, tx_id, timez);

    RAISE INFO 'UPDATED tx';
END;
$$;

create or replace procedure update_gas_fee_volume()
    language plpgsql as
$$
DECLARE
    unprocessed_daily_times  TIMESTAMP[];
    unprocessed_hourly_times TIMESTAMP[];
    unprocessed_ids          INT[];
BEGIN
    -- Collect unprocessed message timestamps (grouped by day and hour)
    SELECT array_agg(DISTINCT date_trunc('DAY', tx_timestamp)) FROM tx_gas_cache WHERE processed = false INTO unprocessed_daily_times;
    SELECT array_agg(DISTINCT date_trunc('HOUR', tx_timestamp)) FROM tx_gas_cache WHERE processed = false INTO unprocessed_hourly_times;
    SELECT array_agg(id) FROM tx_gas_cache WHERE processed = false INTO unprocessed_ids;

    -- Update daily stats for unprocessed messages
    -- Reprocess daily stats for the day that unprocessed messages fall in
    INSERT
    INTO tx_gas_fee_volume_day
    SELECT date_trunc('DAY',tc.tx_timestamp) AS tx_timestamp,
           sum(gas_wanted)                 AS gas_wanted,
           sum(gas_used)                   AS gas_used,
           sum(tf.amount)                  AS fee_amount
    FROM tx_cache tc
             JOIN tx_fee tf ON tc.id = tf.tx_hash_id
    WHERE date_trunc('DAY', tc.tx_timestamp) = ANY (unprocessed_daily_times)
    GROUP BY date_trunc('DAY', tc.tx_timestamp)
    ON CONFLICT (tx_timestamp)
        DO UPDATE
        SET gas_wanted = excluded.gas_wanted,
            gas_used   = excluded.gas_used,
            fee_amount = excluded.fee_amount;

    -- Update hourly stats for unprocessed messages
    -- Reprocess hourly stats for the day that unprocessed messages fall in
    INSERT
    INTO tx_gas_fee_volume_hour
    SELECT date_trunc('HOUR', tc.tx_timestamp) AS tx_timestamp,
           sum(gas_wanted)                 AS gas_wanted,
           sum(gas_used)                   AS gas_used,
           sum(tf.amount)                  AS fee_amount
    FROM tx_cache tc
             JOIN tx_fee tf ON tc.id = tf.tx_hash_id
    WHERE date_trunc('HOUR', tc.tx_timestamp) = ANY (unprocessed_hourly_times)
    GROUP BY date_trunc('HOUR', tc.tx_timestamp)
    ON CONFLICT (tx_timestamp)
        DO UPDATE
        SET gas_wanted = excluded.gas_wanted,
            gas_used = excluded.gas_used,
            fee_amount = excluded.fee_amount;

    -- Update complete. Now mark records as 'processed'.
    UPDATE tx_gas_cache
    SET processed = true
    WHERE id = ANY (unprocessed_ids);

    RAISE INFO 'UPDATED gas fee volume';
END;
$$;

SELECT 'Create materialized view for tx history chart data' AS comment;

-- materialized view for basic data
-- hour, feepayer, tx count, fees paid in hash, hash price in USD, fees paid in USD, gas wanted, gas used
drop materialized view if exists tx_history_chart_data_hourly;
Create materialized view if not exists tx_history_chart_data_hourly as
with feepayer_fees as (
    with base as (
        select
            tx_hash_id,
            CASE
                WHEN payer_type = 'GRANTER' THEN 1
                WHEN payer_type = 'PAYER' THEN 2
                ELSE 3 END AS payer_ordinal,
            payer_type,
            address
        from tx_feepayer
    ), agg as (
        select
            tx_hash_id,
            array_agg(address order by payer_ordinal) addr_array
        from base
        group by tx_hash_id
    )
    select
        tf.tx_hash_id,
        tgc.tx_timestamp,
        agg.addr_array[1] feepayer,
        sum(tf.amount)    fee_amount_in_base_token,
        sum(tgc.gas_wanted) gas_wanted,
        sum(tgc.gas_used)   gas_used
    from agg
             join tx_gas_cache tgc on agg.tx_hash_id = tgc.tx_hash_id
             join tx_fee tf on agg.tx_hash_id = tf.tx_hash_id
    group by tf.tx_hash_id, tgc.tx_timestamp, feepayer
), token_pricing as (
    select
        historical_timestamp,
        data -> 'quote' -> 'USD' ->> 'close' as price
    from token_historical_daily
)
select
    date_trunc('hour', ff.tx_timestamp) as hourly,
    ff.feepayer,
    count(ff.tx_hash_id) as tx_count,
    sum(ff.fee_amount_in_base_token) as fee_amount_in_base_token,
    sum(ff.gas_wanted) as gas_wanted,
    sum(ff.gas_used) as gas_used,
    max(tp.price::numeric) as token_price_usd  -- means its been adjusted to the display token value
from feepayer_fees ff
         left join token_pricing tp on tp.historical_timestamp = date_trunc('day', ff.tx_timestamp)
group by hourly, feepayer
order by hourly
WITH DATA;

SELECT 'Create materialized view for tx type data' AS comment;

-- advanced data
-- hour, tx type count
drop materialized view if exists tx_type_data_hourly;
Create materialized view if not exists tx_type_data_hourly as
select
    date_trunc('hour', tm.tx_timestamp) AS hourly,
    tf.address as feepayer,
    tmt.module || ' / ' || tmt.type as tx_type,
    count(tmt.id) AS tx_type_count
from tx_message tm
         join tx_message_type tmt on tm.tx_message_type_id = tmt.id
         join tx_feepayer tf on tm.tx_hash_id = tf.tx_hash_id
group by hourly, feepayer, tx_type
order by hourly, feepayer, tx_type
WITH DATA;

SELECT 'Create materialized view for fee type data' AS comment;

-- hour, fee type, msg type, fees paid in nhash, hash price in USD
drop materialized view if exists fee_type_data_hourly;
Create materialized view if not exists fee_type_data_hourly as
select
    date_trunc('hour', tf.tx_timestamp) as hourly,
    tfp.address as feepayer,
    tf.fee_type,
    tf.msg_type,
    sum(tf.amount) as fee_amount_in_base_token,
    max((thd.data -> 'quote' -> 'USD' ->> 'close')::numeric) as token_price_usd  -- means its been adjusted to the display token value
from tx_fee tf
         join tx_feepayer tfp on tf.tx_hash_id = tfp.tx_hash_id
         left join token_historical_daily thd ON thd.historical_timestamp = date_trunc('day', tf.tx_timestamp)
group by hourly, feepayer, fee_type, msg_type
order by hourly, feepayer, fee_type, msg_type
WITH DATA;
