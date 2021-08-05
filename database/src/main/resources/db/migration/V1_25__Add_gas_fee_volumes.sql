-- Host future and previous migrated data
CREATE TABLE IF NOT EXISTS tx_gas_cache
(
    id           BIGSERIAL PRIMARY KEY,
    hash         VARCHAR(64) UNIQUE    NOT NULL,
    tx_timestamp TIMESTAMP             NOT NULL,
    gas_wanted   INT                   NOT NULL,
    gas_used     INT                   NOT NULL,
    fee_amount   DOUBLE PRECISION      NOT NULL,
    processed    BOOLEAN DEFAULT false NOT NULL
);

CREATE INDEX IF NOT EXISTS tx_gas_cache_tx_timestamp_idx ON tx_gas_cache (tx_timestamp);
CREATE INDEX IF NOT EXISTS tx_gas_cache_tx_gas_used_idx ON tx_gas_cache (gas_used);
CREATE INDEX IF NOT EXISTS tx_gas_cache_tx_processed_idx ON tx_gas_cache (processed);

-- Aggregate daily gas volume
CREATE TABLE IF NOT EXISTS tx_gas_fee_volume_day
(
    tx_timestamp TIMESTAMP PRIMARY KEY,
    gas_used     BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS tx_gas_fee_volume_day_gas_used ON tx_gas_fee_volume_day (gas_used);

-- Aggregate hourly gas volume
CREATE TABLE IF NOT EXISTS tx_gas_fee_volume_hour
(
    tx_timestamp TIMESTAMP PRIMARY KEY,
    gas_used     BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS tx_gas_fee_volume_hour_gas_used ON tx_gas_fee_volume_hour (gas_used);

-- Host future and previous migrated data
INSERT
INTO tx_gas_cache(hash, tx_timestamp, gas_wanted, gas_used, fee_amount)
SELECT DISTINCT hash,
                tx_timestamp,
                gas_wanted,
                gas_used,
                (
                    SELECT min_gas_fee
                    FROM block_proposer
                    WHERE block_height = tx_cache.height
                ) AS fee_amount
FROM tx_cache
ON CONFLICT DO NOTHING;

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
    SELECT date_trunc('DAY', tx_timestamp) AS tx_timestamp,
           sum(gas_used)                   AS gas_used
    FROM tx_cache
    WHERE date_trunc('DAY', tx_timestamp) = ANY (unprocessed_daily_times)
    GROUP BY date_trunc('DAY', tx_timestamp)
    ON CONFLICT (tx_timestamp)
        DO UPDATE
        SET gas_used = tx_gas_fee_volume_day.gas_used + excluded.gas_used;

    -- Update hourly stats for unprocessed messages
    -- Reprocess hourly stats for the day that unprocessed messages fall in
    INSERT
    INTO tx_gas_fee_volume_hour
    SELECT date_trunc('HOUR', tx_timestamp) AS tx_timestamp,
           sum(gas_used)                    AS gas_used
    FROM tx_cache
    WHERE date_trunc('HOUR', tx_timestamp) = ANY (unprocessed_hourly_times)
    GROUP BY date_trunc('HOUR', tx_timestamp)
    ON CONFLICT (tx_timestamp)
        DO UPDATE
        SET gas_used = tx_gas_fee_volume_hour.gas_used + excluded.gas_used;

    -- Update complete. Now mark records as 'processed'.
    UPDATE tx_gas_cache
    SET processed = true
    WHERE id = ANY (unprocessed_ids);

    RAISE INFO 'UPDATED gas fee volume';
END;
$$;
