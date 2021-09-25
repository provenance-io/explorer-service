SELECT 'Add sm_code' AS comment;
CREATE TABLE IF NOT EXISTS sm_code
(
    id              INT          NOT NULL PRIMARY KEY,
    creation_height INT          NOT NULL,
    creator         VARCHAR(128) NULL,
    data_hash       VARCHAR(256) NULL,
    data            JSONB        NULL
);

CREATE INDEX IF NOT EXISTS sm_code_creator_idx ON sm_code(creator);

SELECT 'Add tx_sm_code' AS comment;
CREATE TABLE IF NOT EXISTS tx_sm_code
(
    id           SERIAL PRIMARY KEY,
    block_height INT         NOT NULL,
    tx_hash_id   INT         NOT NULL,
    tx_hash      VARCHAR(64) NOT NULL,
    sm_code      INT         NOT NULL
);

CREATE INDEX IF NOT EXISTS tx_sm_code_hash_sm_code_idx ON tx_sm_code(tx_hash_id, sm_code);
CREATE INDEX IF NOT EXISTS tx_sm_code_hash_id_idx ON tx_sm_code(tx_hash_id);
CREATE INDEX IF NOT EXISTS tx_sm_code_hash_idx ON tx_sm_code(tx_hash);
CREATE INDEX IF NOT EXISTS tx_sm_code_sm_code_idx ON tx_sm_code(sm_code);

SELECT 'Add sm_contract' AS comment;
CREATE TABLE IF NOT EXISTS sm_contract
(
    id               SERIAL PRIMARY KEY,
    contract_address VARCHAR(128) NOT NULL,
    creation_height  INT          NOT NULL,
    code_id          INT          NOT NULL,
    creator          VARCHAR(128) NOT NULL,
    admin            VARCHAR(128) NULL,
    label            TEXT         NULL,
    data             JSONB        NOT NULL
);

CREATE INDEX IF NOT EXISTS sm_contract_code_id_idx ON sm_contract(code_id);
CREATE INDEX IF NOT EXISTS sm_contract_creator_idx ON sm_contract(creator);

SELECT 'Add tx_sm_contract' AS comment;
CREATE TABLE IF NOT EXISTS tx_sm_contract
(
    id                  SERIAL PRIMARY KEY,
    block_height        INT          NOT NULL,
    tx_hash_id          INT          NOT NULL,
    tx_hash             VARCHAR(64)  NOT NULL,
    sm_contract_id      INT          NOT NULL,
    sm_contract_address VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS tx_sm_contract_hash_sm_code_idx ON tx_sm_contract(tx_hash_id, sm_contract_id);
CREATE INDEX IF NOT EXISTS tx_sm_contract_hash_id_idx ON tx_sm_contract(tx_hash_id);
CREATE INDEX IF NOT EXISTS tx_sm_contract_hash_idx ON tx_sm_contract(tx_hash);
CREATE INDEX IF NOT EXISTS tx_sm_contract_sm_contract_id_idx ON tx_sm_contract(sm_contract_id);

SELECT 'Alter account' AS comment;
ALTER TABLE account
    ADD COLUMN IF NOT EXISTS is_contract BOOLEAN DEFAULT FALSE;
