CREATE INDEX IF NOT EXISTS nft_scope_specification_id_idx ON nft_scope USING btree ((scope ->> 'specification_id'));
