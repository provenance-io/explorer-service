CREATE TABLE block_cache
(
    height   INT PRIMARY KEY,
    block JSONB NOT NULL,
    block_timestamp TIMESTAMP NOT NULL,
    last_hit TIMESTAMP NOT NULL,
    hit_count INT NOT NULL
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
    tx JSONB NOT NULL,
    last_hit TIMESTAMP NOT NULL,
    hit_count INT NOT NULL
);

CREATE TABLE transaction_count_cache
(
    day VARCHAR(16) PRIMARY KEY,
    number_txs INT NOT NULL DEFAULT 0,
    number_tx_blocks INT NOT NULL DEFAULT 0,
    max_height INT DEFAULT NULL,
    min_height INT NOT NULL
);

CREATE TABLE transaction_count_index
(
    id INT PRIMARY KEY,
    max_height_read INT NULL,
    min_height_read INT NULL,
    last_run_start TIMESTAMP NOT NULL,
    last_run_end TIMESTAMP NOT NULL
);
