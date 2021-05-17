CREATE TABLE IF NOT EXISTS nft_scope
(
    id SERIAL PRIMARY KEY,
    uuid VARCHAR(128) NOT NULL,
    address VARCHAR(128) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS nft_scope_uuid_idx ON nft_scope(uuid);
CREATE INDEX IF NOT EXISTS nft_scope_address_idx ON nft_scope(address);
CREATE INDEX IF NOT EXISTS nft_scope_deleted_idx ON nft_scope(deleted);

CREATE TABLE IF NOT EXISTS nft_scope_spec
(
    id SERIAL PRIMARY KEY,
    uuid VARCHAR(128) NOT NULL,
    address VARCHAR(128) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS nft_scope_spec_uuid_idx ON nft_scope_spec(uuid);
CREATE INDEX IF NOT EXISTS nft_scope_spec_address_idx ON nft_scope_spec(address);
CREATE INDEX IF NOT EXISTS nft_scope_spec_deleted_idx ON nft_scope_spec(deleted);

CREATE TABLE IF NOT EXISTS nft_contract_spec
(
    id SERIAL PRIMARY KEY,
    uuid VARCHAR(128) NOT NULL,
    address VARCHAR(128) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS nft_contract_spec_uuid_idx ON nft_contract_spec(uuid);
CREATE INDEX IF NOT EXISTS nft_contract_spec_address_idx ON nft_contract_spec(address);
CREATE INDEX IF NOT EXISTS nft_contract_spec_deleted_idx ON nft_contract_spec(deleted);

CREATE TABLE IF NOT EXISTS tx_nft_join
(
    id            SERIAL PRIMARY KEY,
    block_height  INT          NOT NULL,
    tx_hash_id    INT          NOT NULL,
    tx_hash       VARCHAR(64)  NOT NULL,
    metadata_type varchar(16)  NOT NULL,
    metadata_id   INT          NOT NULL,
    metadata_uuid VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS tx_nft_join_hash_metadata_id_type_idx ON tx_nft_join(tx_hash_id, metadata_id, metadata_type);
CREATE INDEX IF NOT EXISTS tx_nft_join_hash_id_idx ON tx_nft_join(tx_hash_id);
CREATE INDEX IF NOT EXISTS tx_nft_join_hash_idx ON tx_nft_join(tx_hash);
CREATE INDEX IF NOT EXISTS tx_nft_join_hash_metadata_uuid_idx ON tx_nft_join(tx_hash, metadata_uuid);
CREATE INDEX IF NOT EXISTS tx_nft_join_metadata_type_idx ON tx_nft_join(metadata_type);
CREATE INDEX IF NOT EXISTS tx_nft_join_metadata_id_type_idx ON tx_nft_join(metadata_id, metadata_type);
CREATE INDEX IF NOT EXISTS tx_nft_join_metadata_id_idx ON tx_nft_join(metadata_id);
CREATE INDEX IF NOT EXISTS tx_nft_join_metadata_uuid_idx ON tx_nft_join(metadata_uuid);
CREATE INDEX IF NOT EXISTS tx_nft_join_block_height_idx ON tx_nft_join(block_height);
