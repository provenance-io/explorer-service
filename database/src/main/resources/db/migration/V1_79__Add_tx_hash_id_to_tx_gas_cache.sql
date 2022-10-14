SELECT 'Add tx_hash_id to tx_gas_cache' AS comment;

-- add tx_timestamp to tx_fee, tx_feepayer, tx_message
-- add tx_hash_id to tx_gas_cache
-- update the ingest procedures associated

ALTER TABLE tx_gas_cache
    ADD COLUMN IF NOT EXISTS tx_hash_id integer;

UPDATE tx_gas_cache
SET tx_hash_id = tx_cache.id
FROM tx_cache
WHERE tx_gas_cache.height = tx_cache.height and tx_gas_cache.hash = tx_cache.hash;

CREATE INDEX IF NOT EXISTS tx_gas_cache_tx_hash_id_idx ON tx_gas_cache(tx_hash_id);
