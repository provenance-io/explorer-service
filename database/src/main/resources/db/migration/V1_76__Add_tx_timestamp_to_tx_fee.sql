SELECT 'Add tx_timestamp to tx_fee' AS comment;

-- add tx_timestamp to tx_fee, tx_feepayer, tx_message
-- add tx_hash_id to tx_gas_cache
-- update the ingest procedures associated

ALTER TABLE tx_fee
    ADD COLUMN IF NOT EXISTS tx_timestamp timestamp;

UPDATE tx_fee
SET tx_timestamp = block_cache.block_timestamp
FROM block_cache
where tx_fee.block_height = block_cache.height;

CREATE INDEX IF NOT EXISTS tx_fee_tx_timestamp_idx ON tx_fee(tx_timestamp);
