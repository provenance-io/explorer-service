CREATE INDEX IF NOT EXISTS tx_marker_join_hash_idx ON tx_marker_join(tx_hash);
CREATE INDEX IF NOT EXISTS tx_address_join_hash_idx ON tx_address_join(tx_hash);
