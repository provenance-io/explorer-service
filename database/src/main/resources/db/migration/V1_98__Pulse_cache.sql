CREATE TABLE IF NOT EXISTS pulse_cache
(
    id                SERIAL PRIMARY KEY,
    cache_date        DATE      NOT NULL,
    updated_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    type              TEXT      NOT NULL,
    data              JSONB     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pulse_cache_date ON pulse_cache (cache_date);
CREATE INDEX IF NOT EXISTS idx_pulse_cache_type ON pulse_cache (type);
CREATE UNIQUE INDEX IF NOT EXISTS idx_pulse_cache_date_type ON pulse_cache (cache_date, type);
