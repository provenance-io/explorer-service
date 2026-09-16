-- Current on-chain metadata scope NAV snapshot. Upserted by a dedicated Pulse
-- job. One row per scope address.
CREATE TABLE IF NOT EXISTS scope_nav_snapshot (
    id SERIAL PRIMARY KEY,
    scope_uuid VARCHAR(128) NOT NULL,
    scope_address VARCHAR(128) NOT NULL,
    price_amount BIGINT,
    price_denom VARCHAR(128),
    volume BIGINT,
    updated_block_height BIGINT,
    snapshot_at TIMESTAMPTZ NOT NULL,
    query_error TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS scope_nav_snapshot_address_uk
    ON scope_nav_snapshot (scope_address);

CREATE INDEX IF NOT EXISTS scope_nav_snapshot_uuid_idx
    ON scope_nav_snapshot (scope_uuid);
