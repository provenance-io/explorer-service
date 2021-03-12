CREATE TABLE IF NOT EXISTS tx_marker_join (
    id SERIAL PRIMARY KEY,
    block_height INT NOT NULL,
    tx_hash VARCHAR(64) NOT NULL,
    denom VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS tx_marker_join_denom_idx ON tx_marker_join (denom);
CREATE INDEX IF NOT EXISTS tx_marker_join_hash_denom_idx ON tx_marker_join (tx_hash, denom);


ALTER TABLE tx_message
ADD COLUMN IF NOT EXISTS tx_message_hash TEXT NULL;

CREATE INDEX IF NOT EXISTS tx_message_hash_message_hash_idx ON tx_message (tx_hash, tx_message_hash);
