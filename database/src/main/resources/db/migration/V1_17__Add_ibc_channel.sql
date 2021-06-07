CREATE TABLE IF NOT EXISTS ibc_channel
(
    id SERIAL PRIMARY KEY,
    client VARCHAR(128) NOT NULL,
    dst_chain_name VARCHAR(256) NOT NULL,
    src_port VARCHAR(128) NOT NULL,
    src_channel VARCHAR(128) NOT NULL,
    dst_port VARCHAR(128) NOT NULL,
    dst_channel VARCHAR(128) NOT NULL,
    status VARCHAR(64) NOT NULL,
    escrow_address_id INT NOT NULL,
    escrow_address VARCHAR(128) NOT NULL,
    data JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS ibc_channel_dst_chain_name_idx ON ibc_channel(dst_chain_name);
CREATE INDEX IF NOT EXISTS ibc_channel_status_idx ON ibc_channel(status);

