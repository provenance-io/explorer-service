CREATE TABLE IF NOT EXISTS ibc_ledger
(
    id SERIAL PRIMARY KEY,
    dst_chain_name VARCHAR(256) NOT NULL,
    channel_id INT NOT NULL,
    denom VARCHAR(256) NOT NULL,
    denom_trace TEXT NOT NULL,
    balance_in DECIMAL NULL,
    balance_out DECIMAL NULL,
    from_address VARCHAR(256) NOT NULL,
    to_address VARCHAR(256) NOT NULL,
    pass_through_address_id INT NOT NULL,
    pass_through_address VARCHAR(128) NOT NULL,
    logs JSONB NOT NULL,
    block_height INT NOT NULL,
    tx_hash_id INT NOT NULL,
    tx_hash       VARCHAR(64)  NOT NULL,
    tx_timestamp TIMESTAMP NOT NULL,
    acknowledged BOOLEAN DEFAULT FALSE,
    ack_success BOOLEAN DEFAULT FALSE,
    ack_logs JSONB NULL,
    ack_block_height INT NULL,
    ack_tx_hash_id INT NULL,
    ack_tx_hash VARCHAR(64) NULL,
    ack_tx_timestamp TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS ibc_ledger_channel_id_idx ON ibc_ledger(channel_id);
CREATE INDEX IF NOT EXISTS ibc_ledger_chain_name_idx ON ibc_ledger(dst_chain_name);
CREATE INDEX IF NOT EXISTS ibc_ledger_tx_timestamp_idx ON ibc_ledger(tx_timestamp);
CREATE INDEX IF NOT EXISTS ibc_ledger_block_height_idx ON ibc_ledger(block_height);
CREATE INDEX IF NOT EXISTS ibc_ledger_channel_id_denom_idx ON ibc_ledger(channel_id, denom);
CREATE INDEX IF NOT EXISTS ibc_ledger_dst_chain_name_denom_idx ON ibc_ledger(dst_chain_name, denom);
CREATE INDEX IF NOT EXISTS ibc_ledger_from_address_idx ON ibc_ledger(from_address);
CREATE INDEX IF NOT EXISTS ibc_ledger_to_address_idx ON ibc_ledger(to_address);
CREATE INDEX IF NOT EXISTS ibc_ledger_pass_through_address_idx ON ibc_ledger(pass_through_address);

