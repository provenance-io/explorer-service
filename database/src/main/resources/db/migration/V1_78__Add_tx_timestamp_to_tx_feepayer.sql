SELECT 'Add tx_timestamp to tx_feepayer' AS comment;

-- add tx_timestamp to tx_fee, tx_feepayer, tx_message
-- add tx_hash_id to tx_gas_cache
-- update the ingest procedures associated

ALTER TABLE tx_feepayer
    ADD COLUMN IF NOT EXISTS tx_timestamp timestamp;

UPDATE tx_feepayer
SET tx_timestamp = block_cache.block_timestamp
FROM block_cache
where tx_feepayer.block_height = block_cache.height;

CREATE INDEX IF NOT EXISTS tx_feepayer_tx_timestamp_idx ON tx_feepayer(tx_timestamp);
