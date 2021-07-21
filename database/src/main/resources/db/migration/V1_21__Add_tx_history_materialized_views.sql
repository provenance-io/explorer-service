-- DAY
CREATE MATERIALIZED VIEW IF NOT EXISTS block_cache_tx_history_day AS
SELECT
    DATE_TRUNC('DAY', block_cache.block_timestamp) AS block_timestamp,
    SUM(block_cache.tx_count) AS tx_count
FROM block_cache
GROUP BY DATE_TRUNC('DAY', block_cache.block_timestamp)
ORDER BY DATE_TRUNC('DAY', block_cache.block_timestamp);

CREATE UNIQUE INDEX IF NOT EXISTS block_cache_tx_history_day_block_timestamp_idx
    ON block_cache_tx_history_day (block_timestamp);

REFRESH MATERIALIZED VIEW CONCURRENTLY block_cache_tx_history_day;


-- HOUR
CREATE MATERIALIZED VIEW IF NOT EXISTS block_cache_tx_history_hour AS
SELECT
    DATE_TRUNC('HOUR', block_cache.block_timestamp) AS block_timestamp,
    SUM(block_cache.tx_count) AS tx_count
FROM block_cache
GROUP BY DATE_TRUNC('HOUR', block_cache.block_timestamp)
ORDER BY DATE_TRUNC('HOUR', block_cache.block_timestamp);

CREATE UNIQUE INDEX IF NOT EXISTS block_cache_tx_history_hour_block_timestamp_idx
    ON block_cache_tx_history_hour (block_timestamp);

REFRESH MATERIALIZED VIEW CONCURRENTLY block_cache_tx_history_hour;