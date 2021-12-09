SELECT 'Add nft_scope_value_owner' AS comment;
CREATE TABLE IF NOT EXISTS nft_scope_value_owner
(
    id           SERIAL PRIMARY KEY,
    scope_id     INT          NOT NULL,
    scope_addr   VARCHAR(128) NOT NULL,
    value_owner  VARCHAR(256) NOT NULL,
    tx_id        INT          NOT NULL,
    block_height INT          NOT NULL,
    tx_hash      VARCHAR(64)  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS nft_scope_vo_scope_address_tx_idx
    ON nft_scope_value_owner (scope_id, value_owner, tx_id);
CREATE INDEX IF NOT EXISTS nft_scope_vo_scope_id_idx ON nft_scope_value_owner (scope_id);
CREATE INDEX IF NOT EXISTS nft_scope_vo_scope_addr_idx ON nft_scope_value_owner (scope_addr);
CREATE INDEX IF NOT EXISTS nft_scope_vo_address_idx ON nft_scope_value_owner (value_owner);
CREATE INDEX IF NOT EXISTS nft_scope_vo_block_height_idx ON nft_scope_value_owner (block_height);


SELECT 'Add nft_scope_owner' AS comment;
CREATE TABLE IF NOT EXISTS nft_scope_owner
(
    id               SERIAL PRIMARY KEY,
    scope_id         INT          NOT NULL,
    scope_addr       VARCHAR(128) NOT NULL,
    owners_data      JSONB        NOT NULL,
    owners_data_hash TEXT         NOT NULL,
    tx_id            INT          NOT NULL,
    block_height     INT          NOT NULL,
    tx_hash          VARCHAR(64)  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS nft_scope_owner_scope_owners_data_tx_idx
    ON nft_scope_owner (scope_id, owners_data_hash, tx_id);
CREATE INDEX IF NOT EXISTS nft_scope_owner_scope_id_idx ON nft_scope_owner (scope_id);
CREATE INDEX IF NOT EXISTS nft_scope_owner_scope_addr_idx ON nft_scope_owner (scope_addr);
CREATE INDEX IF NOT EXISTS nft_scope_owner_block_height_idx ON nft_scope_owner (block_height);

SELECT 'Add nft_scope_data_access' AS comment;
CREATE TABLE IF NOT EXISTS nft_scope_data_access
(
    id               SERIAL PRIMARY KEY,
    scope_id         INT          NOT NULL,
    scope_addr       VARCHAR(128) NOT NULL,
    access_data      JSONB        NOT NULL,
    access_data_hash TEXT         NOT NULL,
    tx_id            INT          NOT NULL,
    block_height     INT          NOT NULL,
    tx_hash          VARCHAR(64)  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS nft_scope_data_access_scope_access_data_tx_idx
    ON nft_scope_data_access (scope_id, access_data_hash, tx_id);
CREATE INDEX IF NOT EXISTS nft_scope_data_access_scope_id_idx ON nft_scope_data_access (scope_id);
CREATE INDEX IF NOT EXISTS nft_scope_data_access_scope_addr_idx ON nft_scope_data_access (scope_addr);
CREATE INDEX IF NOT EXISTS nft_scope_data_access_block_height_idx ON nft_scope_data_access (block_height);
