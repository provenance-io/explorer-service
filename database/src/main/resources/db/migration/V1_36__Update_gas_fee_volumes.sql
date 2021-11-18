SELECT 'Update update_gas_fee_volume() procedure' AS comment;
-- Update gas stats with latest data
CREATE OR REPLACE PROCEDURE update_gas_fee_volume()
    LANGUAGE plpgsql
AS
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
    SELECT date_trunc('DAY', tx_timestamp)                                                              AS tx_timestamp,
           sum(gas_wanted)                                                                              AS gas_wanted,
           sum(gas_used)                                                                                AS gas_used,
           sum((tx_v2 -> 'tx' -> 'auth_info' -> 'fee' -> 'amount' -> 0 ->> 'amount')::DOUBLE PRECISION) AS fee_amount
    FROM tx_cache
    WHERE date_trunc('DAY', tx_timestamp) = ANY (unprocessed_daily_times)
    GROUP BY date_trunc('DAY', tx_timestamp)
    ON CONFLICT (tx_timestamp)
        DO UPDATE
        SET gas_wanted = excluded.gas_wanted,
            gas_used = excluded.gas_used,
            fee_amount = excluded.fee_amount;

    -- Update hourly stats for unprocessed messages
    -- Reprocess hourly stats for the day that unprocessed messages fall in
    INSERT
    INTO tx_gas_fee_volume_hour
    SELECT date_trunc('HOUR', tx_timestamp)                                                             AS tx_timestamp,
           sum(gas_wanted)                                                                              AS gas_wanted,
           sum(gas_used)                                                                                AS gas_used,
           sum((tx_v2 -> 'tx' -> 'auth_info' -> 'fee' -> 'amount' -> 0 ->> 'amount')::DOUBLE PRECISION) AS fee_amount
    FROM tx_cache
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

SELECT 'Updating tx_gas_cache to reset' AS comment;
UPDATE tx_gas_cache
SET processed = false;

SELECT 'Calling procedure to bring up to date' AS comment;
CALL update_gas_fee_volume();
