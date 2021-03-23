CREATE TABLE IF NOT EXISTS block_proposer
(
    block_height              INT PRIMARY KEY,
    proposer_operator_address VARCHAR(96) NOT NULL,
    min_gas_fee               DOUBLE PRECISION,
    block_timestamp           TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS block_proposer_proposer_operator_address_idx ON block_proposer (proposer_operator_address);
CREATE INDEX IF NOT EXISTS block_proposer_block_timestamp_idx ON block_proposer (block_timestamp);

CREATE TABLE IF NOT EXISTS validator_gas_fee_cache
(
    id  SERIAL PRIMARY KEY ,
    date             DATE        NOT NULL,
    operator_address VARCHAR(96) NOT NULL,
    min_gas_fee      DECIMAL,
    max_gas_fee      DECIMAL,
    avg_gas_fee      DECIMAL,
    UNIQUE (date, operator_address)
);

CREATE INDEX IF NOT EXISTS validator_gas_fee_cache_date_idx ON validator_gas_fee_cache(date);
CREATE INDEX IF NOT EXISTS validator_gas_fee_cache_operator_address_idx ON validator_gas_fee_cache(operator_address);

CREATE TABLE IF NOT EXISTS chain_gas_fee_cache
(
    date             DATE        PRIMARY KEY,
    min_gas_fee      DECIMAL,
    max_gas_fee      DECIMAL,
    avg_gas_fee      DECIMAL
);

CREATE INDEX IF NOT EXISTS chain_gas_fee_cache_date_idx ON chain_gas_fee_cache(date);
