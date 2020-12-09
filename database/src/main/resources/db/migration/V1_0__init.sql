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

CREATE TABLE blockchain_cache (
    max_height INT PRIMARY KEY,
    blocks JSONB NOT NULL,
    last_hit TIMESTAMP NOT NULL,
    hit_count INT NOT NULL
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

CREATE TABLE transaction_cache
(
    hash VARCHAR(64) PRIMARY KEY,
    height INT NOT NULL,
    tx_type VARCHAR(64) NOT NULL,
    gas_wanted INT NOT NULL,
    gas_used INT NOT NULL,
    tx_timestamp TIMESTAMP NOT NULL,
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