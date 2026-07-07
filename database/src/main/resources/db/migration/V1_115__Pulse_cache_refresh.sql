ALTER TABLE pulse_cache
    ADD COLUMN refresh BOOLEAN NOT NULL DEFAULT FALSE;

-- Partial index so the scheduled refresh job can cheaply find flagged rows.
CREATE INDEX IF NOT EXISTS idx_pulse_cache_refresh ON pulse_cache (refresh) WHERE refresh = TRUE;
