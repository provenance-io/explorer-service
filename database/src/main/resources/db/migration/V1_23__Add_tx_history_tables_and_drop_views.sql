DROP MATERIALIZED VIEW IF EXISTS block_cache_tx_history_day;
DROP MATERIALIZED VIEW IF EXISTS block_cache_tx_history_hour;

-- HOURLY count of Tx history
CREATE TABLE IF NOT EXISTS block_cache_hourly_tx_counts
(
    block_timestamp TIMESTAMP PRIMARY KEY,
    tx_count        INT NOT NULL
);

-- Migrate historical data
INSERT
INTO block_cache_hourly_tx_counts
SELECT date_trunc('HOUR', block_cache.block_timestamp) AS block_timestamp,
       SUM(block_cache.tx_count)                       AS tx_count
FROM block_cache
GROUP BY date_trunc('HOUR', block_cache.block_timestamp)
ON CONFLICT DO NOTHING;

-- Recalculate tx counts for the last hour
CREATE OR REPLACE PROCEDURE update_block_cache_hourly_tx_counts(TIMESTAMP)
    LANGUAGE plpgsql
AS
$$
BEGIN
    -- Delete the last hour counts
    DELETE
    FROM block_cache_hourly_tx_counts
    WHERE block_timestamp >= date_trunc('HOUR', $1);

    -- Insert the last hour counts
    INSERT
    INTO block_cache_hourly_tx_counts
    SELECT date_trunc('HOUR', block_cache.block_timestamp) AS block_timestamp,
           SUM(block_cache.tx_count)                       AS tx_count
    FROM block_cache
    WHERE date_trunc('HOUR', block_cache.block_timestamp) >= date_trunc('HOUR', $1)
    GROUP BY date_trunc('HOUR', block_cache.block_timestamp);

    RAISE INFO 'UPDATED Tx history';
END;
$$;
