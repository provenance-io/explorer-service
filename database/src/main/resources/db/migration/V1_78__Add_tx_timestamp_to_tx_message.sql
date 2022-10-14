SELECT 'Add tx_timestamp to tx_message' AS comment;

-- add tx_timestamp to tx_fee, tx_feepayer, tx_message
-- add tx_hash_id to tx_gas_cache
-- update the ingest procedures associated

ALTER TABLE tx_message
    ADD COLUMN IF NOT EXISTS tx_timestamp timestamp;

UPDATE tx_message
SET tx_timestamp = block_cache.block_timestamp
FROM block_cache
where tx_message.block_height = block_cache.height;

CREATE INDEX IF NOT EXISTS tx_message_tx_timestamp_idx ON tx_message(tx_timestamp);
