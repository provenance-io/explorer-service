SELECT 'Adding asset_pricing table' AS comment;
CREATE TABLE IF NOT EXISTS asset_pricing
(
    marker_id      INT PRIMARY KEY,
    marker_address VARCHAR(128),
    denom          VARCHAR(256),
    pricing        NUMERIC,
    pricing_denom  VARCHAR(256),
    last_updated   TIMESTAMP,
    data           JSONB
);

CREATE UNIQUE INDEX IF NOT EXISTS asset_pricing_unique_idx ON asset_pricing (denom, pricing_denom);
CREATE INDEX IF NOT EXISTS asset_pricing_denom_idx ON asset_pricing (denom);

SELECT 'Adding cache_update table' AS comment;
CREATE TABLE IF NOT EXISTS cache_update
(
    id           SERIAL PRIMARY KEY,
    cache_key    VARCHAR(256),
    description  TEXT,
    cache_value  TEXT,
    last_updated TIMESTAMP
);

INSERT INTO cache_update(cache_key, description, cache_value, last_updated)
VALUES ('pricing_update', 'The last time asset prices were updated from the Figure pricing engine service.',
        '2000-10-31T01:30:00.000-05:00', now());
