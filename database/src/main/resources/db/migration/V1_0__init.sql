CREATE TABLE block_cache
(
    height   INT PRIMARY KEY,
    block JSONB NOT NULL,
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
    complete BOOLEAN NOT NULL DEFAULT FALSE,
    max_height INT DEFAULT NULL,
    min_height INT NOT NULL,
    index_height INT NOT NULL
);