DROP INDEX IF EXISTS block_cache_timestamp_idx;
DROP INDEX IF EXISTS block_cache_tx_count_idx;
DROP INDEX IF EXISTS tx_message_hash_message_hash_idx;
DROP INDEX IF EXISTS tx_address_join_hash_address_idx;
DROP INDEX IF EXISTS tx_marker_join_hash_denom_idx;

CREATE INDEX IF NOT EXISTS block_cache_date_trunc_day_idx on block_cache(date_trunc('DAY', block_timestamp));

CREATE TABLE IF NOT EXISTS block_tx_count_cache
(
    block_height    INT PRIMARY KEY,
    block_timestamp TIMESTAMP NOT NULL,
    tx_count        INT       NOT NULL DEFAULT 0,
    processed       BOOLEAN            DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS block_tx_count_cache_processed_idx ON block_tx_count_cache(processed);

SELECT 'Inserting into block_tx_count_cache' as comment;
INSERT
INTO block_tx_count_cache
SELECT height,
       block_timestamp,
       tx_count,
       true
FROM block_cache
ON CONFLICT (block_height)
    DO UPDATE
    SET processed = true;

TRUNCATE block_cache_hourly_tx_counts;

SELECT 'Inserting into block_cache_hourly_tx_counts' as comment;
INSERT
INTO block_cache_hourly_tx_counts
SELECT date_trunc('HOUR', block_tx_count_cache.block_timestamp) AS block_timestamp,
       SUM(block_tx_count_cache.tx_count)                       AS tx_count
FROM block_tx_count_cache
GROUP BY date_trunc('HOUR', block_tx_count_cache.block_timestamp)
ON CONFLICT DO NOTHING;

DROP PROCEDURE IF EXISTS update_block_cache_hourly_tx_counts(timestamp);

-- Recalculate tx counts for unprocessed blocks
CREATE OR REPLACE PROCEDURE update_block_cache_hourly_tx_counts()
    LANGUAGE plpgsql
AS
$$
DECLARE
    unprocessed INT[];
BEGIN
    SELECT array_agg(block_height) from block_tx_count_cache WHERE processed = false INTO unprocessed;

    INSERT
    INTO block_cache_hourly_tx_counts (block_timestamp, tx_count)
    SELECT date_trunc('HOUR', block_tx_count_cache.block_timestamp) AS block_timestamp,
           SUM(block_tx_count_cache.tx_count)                       AS tx_count
    FROM block_tx_count_cache
    WHERE block_tx_count_cache.block_height = ANY (unprocessed)
    GROUP BY date_trunc('HOUR', block_tx_count_cache.block_timestamp)
    ON CONFLICT (block_timestamp)
        DO UPDATE
        SET tx_count = block_cache_hourly_tx_counts.tx_count + excluded.tx_count;

    UPDATE block_tx_count_cache
    SET processed = true
    WHERE block_height = ANY (unprocessed);

    RAISE INFO 'UPDATED Tx history';
END;
$$;
