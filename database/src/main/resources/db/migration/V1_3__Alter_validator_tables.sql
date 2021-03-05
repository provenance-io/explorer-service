DROP TABLE IF EXISTS validator_delegations_cache;

ALTER TABLE staking_validator_cache
    ADD COLUMN moniker VARCHAR(128) NULL,
    ADD COLUMN status  VARCHAR(64)  NULL,
    ADD COLUMN jailed  BOOLEAN DEFAULT FALSE;

CREATE INDEX staking_validator_status_idx ON staking_validator_cache (status);
CREATE INDEX staking_validator_jailed_idx ON staking_validator_cache (jailed);
CREATE INDEX staking_validator_status_jailed_idx ON staking_validator_cache (status, jailed);

CREATE TABLE IF NOT EXISTS tx_message_type
(
    id         SERIAL PRIMARY KEY,
    type       VARCHAR(128) NOT NULL,
    module     VARCHAR(128) NOT NULL,
    proto_type VARCHAR(256) NOT NULL,
    category   VARCHAR(128) NULL
);

CREATE TABLE IF NOT EXISTS tx_message
(
    id                 SERIAL PRIMARY KEY,
    block_height       INT NOT NULL,
    tx_hash            VARCHAR(64) NOT NULL,
    tx_message_type_id INT         NOT NULL,
    tx_message         JSONB       NOT NULL
);

CREATE INDEX tx_message_block_idx ON tx_message (block_height);
CREATE INDEX tx_message_hash_idx ON tx_message (tx_hash);
CREATE INDEX tx_message_type_idx ON tx_message (tx_message_type_id);

ALTER TABLE transaction_cache
    DROP COLUMN tx_type;

CREATE TABLE IF NOT EXISTS tx_address_join (
    id SERIAL PRIMARY KEY,
    block_height INT NOT NULL,
    tx_hash VARCHAR(64) NOT NULL,
    address VARCHAR(128) NOT NULL
);

CREATE INDEX tx_address_join_address_idx ON tx_address_join (address);
CREATE INDEX tx_address_join_hash_address_idx ON tx_address_join (tx_hash, address);

ALTER TABLE transaction_cache
    RENAME TO tx_cache;

CREATE INDEX signature_join_idx ON signature_join (join_type, join_key, signature_id);
CREATE INDEX signature_idx ON signature (base_64_sig, pubkey_type);

