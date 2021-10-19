SELECT 'Add nft_scope_value_owner_transfer' AS comment;
CREATE TABLE IF NOT EXISTS nft_scope_value_owner_transfer
(
    id           SERIAL PRIMARY KEY,
    scope_id     INT          NOT NULL,
    scope_addr   VARCHAR(128) NOT NULL,
    address      VARCHAR(256) NOT NULL,
    tx_id        INT          NOT NULL,
    block_height INT          NOT NULL,
    tx_hash      VARCHAR(64)  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS nft_scope_vo_transfer_scope_address_tx_idx
    ON nft_scope_value_owner_transfer (scope_id, address, tx_id);
CREATE INDEX IF NOT EXISTS nft_scope_vo_transfer_scope_id_idx ON nft_scope_value_owner_transfer (scope_id);
CREATE INDEX IF NOT EXISTS nft_scope_vo_transfer_scope_addr_idx ON nft_scope_value_owner_transfer (scope_addr);
CREATE INDEX IF NOT EXISTS nft_scope_vo_transfer_address_idx ON nft_scope_value_owner_transfer (address);
CREATE INDEX IF NOT EXISTS nft_scope_vo_transfer_block_height_idx ON nft_scope_value_owner_transfer (block_height);


SELECT 'Add nft_scope_owner_changes' AS comment;
CREATE TABLE IF NOT EXISTS nft_scope_owner_changes
(
    id           SERIAL PRIMARY KEY,
    scope_id     INT          NOT NULL,
    scope_addr   VARCHAR(128) NOT NULL,
    address      VARCHAR(256) NOT NULL,
    role         VARCHAR(256) NOT NULL,
    tx_id        INT          NOT NULL,
    block_height INT          NOT NULL,
    tx_hash      VARCHAR(64)  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS nft_scope_owner_changes_scope_address_role_tx_idx
    ON nft_scope_owner_changes (scope_id, address, role, tx_id);
CREATE INDEX IF NOT EXISTS nft_scope_vo_transfer_scope_id_idx ON nft_scope_owner_changes (scope_id);
CREATE INDEX IF NOT EXISTS nft_scope_vo_transfer_scope_addr_idx ON nft_scope_owner_changes (scope_addr);
CREATE INDEX IF NOT EXISTS nft_scope_vo_transfer_address_idx ON nft_scope_owner_changes (address);
CREATE INDEX IF NOT EXISTS nft_scope_vo_transfer_block_height_idx ON nft_scope_owner_changes (block_height);

SELECT 'Add nft_scope_owner_changes' AS comment;
CREATE TABLE IF NOT EXISTS nft_scope_owner_changes
(
    id           SERIAL PRIMARY KEY,
    scope_id     INT          NOT NULL,
    scope_addr   VARCHAR(128) NOT NULL,
    address      VARCHAR(256),
    role         VARCHAR(256) NOT NULL,
    tx_id        INT          NOT NULL,
    block_height INT          NOT NULL,
    tx_hash      VARCHAR(64)  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS nft_scope_owner_changes_scope_address_role_tx_idx
    ON nft_scope_owner_changes (scope_id, address, role, tx_id);
CREATE INDEX IF NOT EXISTS nft_scope_vo_transfer_scope_id_idx ON nft_scope_owner_changes (scope_id);
CREATE INDEX IF NOT EXISTS nft_scope_vo_transfer_scope_addr_idx ON nft_scope_owner_changes (scope_addr);
CREATE INDEX IF NOT EXISTS nft_scope_vo_transfer_address_idx ON nft_scope_owner_changes (address);
CREATE INDEX IF NOT EXISTS nft_scope_vo_transfer_block_height_idx ON nft_scope_owner_changes (block_height);
