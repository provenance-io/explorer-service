SELECT 'Combining gas fee stats procedures' AS comment;

DROP PROCEDURE IF EXISTS update_daily_gas_stats();
DROP PROCEDURE IF EXISTS update_hourly_gas_stats();

-- Update daily gas stats with latest data
CREATE OR REPLACE PROCEDURE update_gas_fee_stats()
    LANGUAGE plpgsql
AS
$$
DECLARE
    unprocessed_daily_times  TIMESTAMP[];
    unprocessed_hourly_times TIMESTAMP[];
    unprocessed_ids          INT[];
BEGIN
    -- Collect unprocessed message timestamps (grouped by day)
    -- Collect IDs of those unprocessed messages
    SELECT array_agg(DISTINCT date_trunc('DAY', tx_timestamp)) FROM tx_single_message_cache WHERE processed = false INTO unprocessed_daily_times;
    SELECT array_agg(DISTINCT date_trunc('HOUR', tx_timestamp)) FROM tx_single_message_cache WHERE processed = false INTO unprocessed_hourly_times;
    SELECT array_agg(id) FROM tx_single_message_cache WHERE processed = false INTO unprocessed_ids;

    -- Update daily stats for unprocessed messages
    -- Reprocess daily stats for the day that unprocessed messages fall in
    INSERT
    INTO tx_single_message_gas_stats_day(tx_timestamp,
                                         min_gas_used,
                                         max_gas_used,
                                         avg_gas_used,
                                         stddev_gas_used,
                                         tx_message_type)
    SELECT date_trunc('DAY', tx_timestamp)           AS tx_timestamp,
           min(gas_used)                             AS min_gas_used,
           max(gas_used)                             AS max_gas_used,
           round(avg(gas_used))                      AS avg_gas_used,
           coalesce(round(stddev_samp(gas_used)), 0) AS stddev_gas_used,
           tx_message_type
    FROM tx_single_message_cache
    WHERE date_trunc('DAY', tx_timestamp) = ANY (unprocessed_daily_times)
    GROUP BY date_trunc('DAY', tx_timestamp), tx_message_type
    ON CONFLICT (tx_timestamp, tx_message_type)
        DO UPDATE
        SET min_gas_used    = excluded.min_gas_used,
            max_gas_used    = excluded.max_gas_used,
            avg_gas_used    = excluded.avg_gas_used,
            stddev_gas_used = excluded.stddev_gas_used;

    -- Update hourly stats for unprocessed messages
    -- Reprocess hourly stats for the day that unprocessed messages fall in
    INSERT
    INTO tx_single_message_gas_stats_hour(tx_timestamp,
                                          min_gas_used,
                                          max_gas_used,
                                          avg_gas_used,
                                          stddev_gas_used,
                                          tx_message_type)
    SELECT date_trunc('HOUR', tx_timestamp)          AS tx_timestamp,
           min(gas_used)                             AS min_gas_used,
           max(gas_used)                             AS max_gas_used,
           round(avg(gas_used))                      AS avg_gas_used,
           coalesce(round(stddev_samp(gas_used)), 0) AS stddev_gas_used,
           tx_message_type
    FROM tx_single_message_cache
    WHERE date_trunc('HOUR', tx_timestamp) = ANY (unprocessed_hourly_times)
    GROUP BY date_trunc('HOUR', tx_timestamp), tx_message_type
    ON CONFLICT (tx_timestamp, tx_message_type)
        DO UPDATE
        SET min_gas_used    = excluded.min_gas_used,
            max_gas_used    = excluded.max_gas_used,
            avg_gas_used    = excluded.avg_gas_used,
            stddev_gas_used = excluded.stddev_gas_used;

    -- Update complete, now mark records as 'processed'.
    UPDATE tx_single_message_cache
    SET processed = true
    WHERE id = ANY (unprocessed_ids);

    RAISE INFO 'UPDATED gas stats';
END;
$$;
