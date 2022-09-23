SELECT 'Update update_gas_fee_volume()' AS comment;

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
    SELECT date_trunc('DAY', tx_timestamp) AS tx_timestamp,
           sum(gas_wanted)                 AS gas_wanted,
           sum(gas_used)                   AS gas_used,
           sum(tf.amount)                  AS fee_amount
    FROM tx_cache tc
             JOIN tx_fee tf ON tc.id = tf.tx_hash_id
    WHERE date_trunc('DAY', tx_timestamp) = ANY (unprocessed_daily_times)
    GROUP BY date_trunc('DAY', tx_timestamp)
    ON CONFLICT (tx_timestamp)
        DO UPDATE
        SET gas_wanted = excluded.gas_wanted,
            gas_used   = excluded.gas_used,
            fee_amount = excluded.fee_amount;

    -- Update hourly stats for unprocessed messages
    -- Reprocess hourly stats for the day that unprocessed messages fall in
    INSERT
    INTO tx_gas_fee_volume_hour
    SELECT date_trunc('HOUR', tx_timestamp) AS tx_timestamp,
           sum(gas_wanted)                 AS gas_wanted,
           sum(gas_used)                   AS gas_used,
           sum(tf.amount)                  AS fee_amount
    FROM tx_cache tc
             JOIN tx_fee tf ON tc.id = tf.tx_hash_id
    WHERE date_trunc('HOUR', tx_timestamp) = ANY (unprocessed_hourly_times)
    GROUP BY date_trunc('HOUR', tx_timestamp)
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

create or replace procedure insert_tx_gas_cache(txgasfee tx_gas_cache, timez timestamp without time zone, tx_height integer)
    language plpgsql as
$$
BEGIN
    INSERT INTO tx_gas_cache(height, hash, gas_wanted, gas_used, tx_timestamp, fee_amount)
    VALUES (tx_height, txGasFee.hash, txGasFee.gas_wanted, txGasFee.gas_used, timez, txGasFee.fee_amount)
    ON CONFLICT (height, hash) DO UPDATE
    SET processed = false,
        fee_amount = txgasfee.fee_amount;
END;
$$;

create or replace procedure insert_validator_market_rate(valmarketrate validator_market_rate, timez timestamp without time zone, tx_height integer, tx_id integer)
    language plpgsql as
$$
BEGIN
    INSERT INTO validator_market_rate(block_height, block_timestamp, proposer_address, tx_hash_id, tx_hash, market_rate, success)
    VALUES (tx_height, timez, valMarketRate.proposer_address, tx_id, valMarketRate.tx_hash, valMarketRate.market_rate, valMarketRate.success)
    ON CONFLICT (tx_hash_id) DO UPDATE
        SET market_rate = valMarketRate.market_rate;
END;
$$;

create or replace procedure insert_tx_fees(txfees tx_fee[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    tf tx_fee;
BEGIN

    DELETE FROM tx_fee WHERE tx_hash_id = tx_id;

    FOREACH tf IN ARRAY txFees
        LOOP
            INSERT INTO tx_fee(tx_hash, fee_type, marker_id, marker, amount, block_height, tx_hash_id, msg_type, recipient, orig_fees)
            VALUES (tf.tx_hash, tf.fee_type, tf.marker_id, tf.marker, tf.amount, tx_height, tx_id, tf.msg_type, tf.recipient, tf.orig_fees)
            ON CONFLICT (tx_hash_id, fee_type, COALESCE(msg_type, ''), marker_id, COALESCE(recipient, '')) DO UPDATE
                SET
                    amount = tf.amount,
                    orig_fees = tf.orig_fees
            ;
        END LOOP;
END;
$$;

SELECT 'Adding update range as a cache value' AS comment;
INSERT INTO cache_update(cache_key, description, cache_value, last_updated)
VALUES ('fee_bug_one_eleven_start_block', 'Block to start to reprocessing for the fee bug. Will be bounded by the end range as described in envs. Once completed, will be replaced by `DONE`',null, now());
