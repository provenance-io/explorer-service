ALTER TABLE nft_scope
    ADD COLUMN IF NOT EXISTS scope jsonb NULL;

CREATE INDEX nft_scope_value_owner_gin_idx ON nft_scope USING btree ((scope ->> 'valueOwnerAddress'));
CREATE INDEX nft_scope_owners_gin_idx ON nft_scope USING gin ( (scope -> 'owners') jsonb_path_ops);