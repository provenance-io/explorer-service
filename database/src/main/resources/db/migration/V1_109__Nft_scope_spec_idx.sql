DROP INDEX IF EXISTS nft_scope_specification_id_idx;
DROP INDEX IF EXISTS nft_scope_value_owner_gin_idx;

CREATE INDEX IF NOT EXISTS nft_scope_specification_id_idx ON nft_scope ((scope ->> 'specification_id'));
CREATE INDEX IF NOT EXISTS nft_scope_value_owner_idx ON nft_scope ((scope ->> 'value_owner_address'));
