CREATE TABLE IF NOT EXISTS pulse_cache
(
    id               SERIAL PRIMARY KEY,
    cached_timestamp TIMESTAMP NOT NULL,
    type             TEXT      NOT NULL,
    denom            TEXT,
    data             JSONB     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pulse_cached_timestamp ON pulse_cache (cached_timestamp);
CREATE INDEX IF NOT EXISTS idx_pulse_cache_denom ON pulse_cache (denom);
CREATE INDEX IF NOT EXISTS idx_pulse_cache_type ON pulse_cache (type);
CREATE UNIQUE INDEX IF NOT EXISTS idx_pulse_cache_time_type ON pulse_cache (cached_timestamp, type);
