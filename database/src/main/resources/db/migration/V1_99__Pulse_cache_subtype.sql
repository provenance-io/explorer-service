ALTER TABLE pulse_cache
    ADD COLUMN subtype TEXT;

CREATE INDEX IF NOT EXISTS idx_pulse_cache_subtype ON pulse_cache (subtype);
DROP INDEX IF EXISTS idx_pulse_cache_date_type;
CREATE UNIQUE INDEX IF NOT EXISTS idx_pulse_cache_date_type_subtype ON pulse_cache (cache_date, type, subtype);
