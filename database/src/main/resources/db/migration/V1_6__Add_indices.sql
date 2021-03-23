CREATE INDEX IF NOT EXISTS tx_cache_block_idx ON tx_cache (height);
CREATE INDEX IF NOT EXISTS tx_cache_gas_used_idx ON tx_cache (gas_used);
CREATE INDEX IF NOT EXISTS tx_cache_timestamp_idx ON tx_cache (tx_timestamp);
CREATE INDEX IF NOT EXISTS tx_cache_error_code_idx ON tx_cache (error_code);
CREATE INDEX IF NOT EXISTS marker_cache_denom_idx ON marker_cache (denom);
CREATE INDEX IF NOT EXISTS tx_message_type_type_idx ON tx_message_type(type);
CREATE INDEX IF NOT EXISTS tx_message_type_module_idx ON tx_message_type(module);
