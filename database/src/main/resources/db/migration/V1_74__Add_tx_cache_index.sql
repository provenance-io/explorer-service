SELECT 'Add tx_cache hash index' AS comment;

CREATE INDEX IF NOT EXISTS tx_cache_hash_idx ON tx_cache(hash);
