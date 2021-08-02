-- Host future and previous migrated data
CREATE TABLE IF NOT EXISTS tx_single_message_cache
(
    id              SERIAL PRIMARY KEY,
    tx_timestamp    TIMESTAMP             NOT NULL,
    tx_hash         VARCHAR(64) UNIQUE    NOT NULL,
    gas_used        INT                   NOT NULL,
    tx_message_type VARCHAR(128)          NOT NULL,
    processed       BOOLEAN DEFAULT false NOT NULL
);

CREATE INDEX IF NOT EXISTS tx_single_message_cache_tx_timestamp_idx
    ON tx_single_message_cache (tx_timestamp);

CREATE INDEX IF NOT EXISTS tx_single_message_cache_gas_used_idx
    ON tx_single_message_cache (gas_used);

CREATE INDEX IF NOT EXISTS tx_single_message_cache_tx_message_type_idx
    ON tx_single_message_cache (tx_message_type);

CREATE INDEX IF NOT EXISTS tx_single_message_cache_processed_idx
    ON tx_single_message_cache (processed);

-- Aggregated HOURLY data for GAS statistics.
CREATE TABLE IF NOT EXISTS tx_single_message_gas_stats_day
(
    tx_timestamp    TIMESTAMP    NOT NULL,
    min_gas_used    INT          NOT NULL,
    max_gas_used    INT          NOT NULL,
    avg_gas_used    INT          NOT NULL,
    stddev_gas_used INT          NOT NULL,
    tx_message_type VARCHAR(128) NOT NULL,
    PRIMARY KEY (tx_timestamp, tx_message_type)
);

CREATE TABLE IF NOT EXISTS tx_single_message_gas_stats_hour
(
    tx_timestamp    TIMESTAMP    NOT NULL,
    min_gas_used    INT          NOT NULL,
    max_gas_used    INT          NOT NULL,
    avg_gas_used    INT          NOT NULL,
    stddev_gas_used INT          NOT NULL,
    tx_message_type VARCHAR(128) NOT NULL,
    PRIMARY KEY (tx_timestamp, tx_message_type)
);

-- Migrate historical data to tx_single_message_cache
WITH single_message_txs AS (
    SELECT tm.tx_hash,
           -- avoids adding to GROUP BY
           string_agg(tc.tx_timestamp::TEXT, ',') AS tx_timestamp,
           string_agg(tc.gas_used::TEXT, ',')     AS gas_used,
           string_agg(tmt.type::TEXT, ',')        AS tx_message_type
    FROM tx_message tm
             INNER JOIN tx_cache tc
                        ON tc.id = tm.tx_hash_id
             INNER JOIN tx_message_type tmt
                        ON tm.tx_message_type_id = tmt.id
    GROUP BY tm.tx_hash
    HAVING count(tm.tx_hash) = 1
),
     stats AS (
         SELECT cast(tx_timestamp AS TIMESTAMP) AS tx_timestamp,
                tx_hash,
                cast(gas_used AS INT)           AS gas_used,
                tx_message_type
         FROM single_message_txs
     )
INSERT
INTO tx_single_message_cache (tx_timestamp, tx_hash, gas_used, tx_message_type)
SELECT *
FROM STATS
ON CONFLICT DO NOTHING;

-- Daily aggregate of historical data
INSERT
INTO tx_single_message_gas_stats_day
SELECT date_trunc('DAY', tx_timestamp)           AS tx_timestamp,
       min(gas_used)                             AS min_gas_used,
       max(gas_used)                             AS max_gas_used,
       round(avg(gas_used))                      AS avg_gas_used,
       coalesce(round(stddev_samp(gas_used)), 0) AS stddev_gas_used,
       tx_message_type
FROM tx_single_message_cache
GROUP BY date_trunc('DAY', tx_timestamp),
         tx_message_type
ON CONFLICT DO NOTHING;

-- Hourly aggregate of historical data
INSERT
INTO tx_single_message_gas_stats_hour
SELECT date_trunc('HOUR', tx_timestamp)          AS tx_timestamp,
       min(gas_used)                             AS min_gas_used,
       max(gas_used)                             AS max_gas_used,
       round(avg(gas_used))                      AS avg_gas_used,
       coalesce(round(stddev_samp(gas_used)), 0) AS stddev_gas_used,
       tx_message_type
FROM tx_single_message_cache
GROUP BY date_trunc('HOUR', tx_timestamp),
         tx_message_type
ON CONFLICT DO NOTHING;

-- Update daily gas stats with latest data
CREATE OR REPLACE PROCEDURE update_daily_gas_stats()
    LANGUAGE plpgsql
AS
$$
DECLARE
    unprocessed INT[];
BEGIN
    -- Collection unprocessed record IDs
    SELECT array_agg(id) FROM tx_single_message_cache WHERE processed = false INTO unprocessed;

    -- Upsert unprocessed stats.
    INSERT
    INTO tx_single_message_gas_stats_day(tx_timestamp,
                                         min_gas_used,
                                         max_gas_used,
                                         avg_gas_used,
                                         stddev_gas_used,
                                         tx_message_type)
    SELECT date_trunc('DAY', tx_timestamp)          AS tx_timestamp,
           min(gas_used)                             AS min_gas_used,
           max(gas_used)                             AS max_gas_used,
           round(avg(gas_used))                      AS avg_gas_used,
           coalesce(round(stddev_samp(gas_used)), 0) AS stddev_gas_used,
           tx_message_type
    FROM tx_single_message_cache
    WHERE id = ANY (unprocessed)
    GROUP BY date_trunc('DAY', tx_timestamp),
             tx_message_type
    ON CONFLICT (tx_timestamp, tx_message_type)
        DO UPDATE
        SET min_gas_used    = tx_single_message_gas_stats_day.min_gas_used + excluded.min_gas_used,
            max_gas_used    = tx_single_message_gas_stats_day.max_gas_used + excluded.max_gas_used,
            avg_gas_used    = tx_single_message_gas_stats_day.avg_gas_used + excluded.avg_gas_used,
            stddev_gas_used = tx_single_message_gas_stats_day.stddev_gas_used + excluded.stddev_gas_used;

    -- Update complete, now mark records as 'processed'.
    UPDATE tx_single_message_cache
    SET processed = true
    WHERE id = ANY (unprocessed);

    RAISE INFO 'UPDATED daily gas stats';
END;
$$;

-- Update hourly gas stats with latest data
CREATE OR REPLACE PROCEDURE update_hourly_gas_stats()
    LANGUAGE plpgsql
AS
$$
DECLARE
    unprocessed INT[];
BEGIN
    -- Collection unprocessed record IDs
    SELECT array_agg(id) FROM tx_single_message_cache WHERE processed = false INTO unprocessed;

    -- Upsert unprocessed stats.
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
    WHERE id = ANY (unprocessed)
    GROUP BY date_trunc('HOUR', tx_timestamp),
             tx_message_type
    ON CONFLICT (tx_timestamp, tx_message_type)
        DO UPDATE
        SET min_gas_used    = tx_single_message_gas_stats_hour.min_gas_used + excluded.min_gas_used,
            max_gas_used    = tx_single_message_gas_stats_hour.max_gas_used + excluded.max_gas_used,
            avg_gas_used    = tx_single_message_gas_stats_hour.avg_gas_used + excluded.avg_gas_used,
            stddev_gas_used = tx_single_message_gas_stats_hour.stddev_gas_used + excluded.stddev_gas_used;

    -- Update complete, now mark records as 'processed'.
    UPDATE tx_single_message_cache
    SET processed = true
    WHERE id = ANY (unprocessed);

    RAISE INFO 'UPDATED hourly gas stats';
END;
$$;
