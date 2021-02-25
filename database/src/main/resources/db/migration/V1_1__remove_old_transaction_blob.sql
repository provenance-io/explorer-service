ALTER TABLE transaction_cache
DROP COLUMN IF EXISTS tx;

DROP TABLE IF EXISTS validator_addresses;

CREATE TABLE validator_addresses (
    id                SERIAL PRIMARY KEY,
    account_address   VARCHAR(96) NOT NULL UNIQUE,
    operator_address  VARCHAR(96) NOT NULL UNIQUE,
    consensus_pubkey  VARCHAR(96) NOT NULL UNIQUE,
    consensus_address VARCHAR(96) NOT NULL UNIQUE
);
