CREATE TABLE IF NOT EXISTS pulse_cache
(
    cached_timestamp TIMESTAMP PRIMARY KEY,
    type             TEXT  NOT NULL,
    denom            TEXT,
    data             JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pulse_cache_denom ON pulse_cache (denom);
CREATE INDEX IF NOT EXISTS idx_pulse_cache_type ON pulse_cache (type);
