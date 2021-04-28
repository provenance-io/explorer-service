CREATE INDEX IF NOT EXISTS tx_message_hash_id_type_id_idx ON tx_message(tx_hash_id, tx_message_type_id);

CREATE INDEX IF NOT EXISTS tx_address_join_hash_address_id_type_idx ON tx_address_join(tx_hash_id, address_id, address_type);

CREATE INDEX IF NOT EXISTS tx_marker_join_hash_marker_id_idx ON tx_marker_join(tx_hash_id, marker_id);
