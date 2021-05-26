
select 'Altering marker_cache' as comment;

ALTER TABLE marker_cache
    ALTER COLUMN denom TYPE varchar(256);

DROP INDEX IF EXISTS marker_cache_address_idx;
CREATE INDEX IF NOT EXISTS marker_cache_address_idx ON marker_cache(marker_address);

ALTER TABLE marker_cache
    ALTER COLUMN marker_address DROP NOT NULL,
    ALTER COLUMN data DROP NOT NULL;

select 'Altering tx_marker_join' as comment;
ALTER TABLE tx_marker_join
    ALTER COLUMN denom TYPE varchar(256)
