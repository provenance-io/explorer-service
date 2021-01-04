CREATE TABLE block_cache
(
    height   INT PRIMARY KEY,
    tx_count INT NOT NULL DEFAULT(0),
    block_timestamp TIMESTAMP NOT NULL,
    block JSONB NOT NULL,
    last_hit TIMESTAMP NOT NULL,
    hit_count INT NOT NULL
);

CREATE TABLE block_index
(
    id INT PRIMARY KEY,
    max_height_read INT NULL,
    min_height_read INT NULL,
    last_update TIMESTAMP NOT NULL
);

CREATE TABLE validators_cache
(
    height   INT PRIMARY KEY,
    validators JSONB NOT NULL,
    last_hit TIMESTAMP NOT NULL,
    hit_count INT NOT NULL
);

CREATE TABLE validator_cache
(
    addressId   VARCHAR(64) PRIMARY KEY,
    validator JSONB NOT NULL,
    last_hit TIMESTAMP NOT NULL,
    hit_count INT NOT NULL
);

CREATE TABLE staking_validator_cache
(
    operator_address VARCHAR(128) PRIMARY KEY,
    staking_validator JSONB NOT NULL,
    last_hit TIMESTAMP NOT NULL,
    hit_count INT NOT NULL
);

CREATE TABLE validator_delegations_cache
(
    operator_address VARCHAR(128) PRIMARY KEY,
    validator_delegations JSONB NOT NULL,
    last_hit TIMESTAMP NOT NULL,
    hit_count INT NOT NULL
);

CREATE TABLE transaction_cache
(
    hash VARCHAR(64) PRIMARY KEY,
    height INT NOT NULL,
    tx_type VARCHAR(64) NOT NULL,
    signer VARCHAR(128),
    gas_wanted INT NOT NULL,
    gas_used INT NOT NULL,
    tx_timestamp TIMESTAMP NOT NULL,
    error_code INT DEFAULT NULL,
    codespace VARCHAR(16) DEFAULT NULL,
    tx JSONB NOT NULL,
    last_hit TIMESTAMP NOT NULL,
    hit_count INT NOT NULL
);

CREATE TABLE spotlight_cache
(
    id int PRIMARY KEY,
    spotlight JSONB NOT NULL,
    last_hit TIMESTAMP NOT NULL
);

CREATE TABLE validator_addresses (
    consensus_address VARCHAR(96) NOT NULL UNIQUE,
    consensus_pubkey_address VARCHAR(96) NOT NULL UNIQUE,
    operator_address VARCHAR(96) NOT NULL UNIQUE
)