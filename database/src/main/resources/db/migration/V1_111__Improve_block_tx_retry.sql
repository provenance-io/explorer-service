-- Add retry tracking fields
ALTER TABLE block_tx_retry
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_retry_timestamp TIMESTAMP DEFAULT NULL;
