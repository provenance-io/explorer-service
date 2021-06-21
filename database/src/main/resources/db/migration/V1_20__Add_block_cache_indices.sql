select 'Altering block_cache' as comment;

CREATE INDEX IF NOT EXISTS block_cache_timestamp_idx ON block_cache(block_timestamp);
CREATE INDEX IF NOT EXISTS block_cache_tx_count_idx ON block_cache(tx_count);

