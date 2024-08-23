SELECT 'Create tx_processing_failures table' AS comment;

CREATE TABLE IF NOT EXISTS tx_processing_failures (
    block_height INT NOT NULL,
    tx_hash VARCHAR(128) NOT NULL,
    process_type VARCHAR(64) NOT NULL,
    failure_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT DEFAULT NULL,
    retried BOOLEAN NOT NULL DEFAULT FALSE,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (block_height, tx_hash, process_type)
);
