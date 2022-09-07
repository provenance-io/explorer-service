INSERT INTO cache_update(cache_key, description, cache_value, last_updated)
VALUES ('utility_token_latest', 'Utility token latest data', null, now());

CREATE TABLE IF NOT EXISTS token_historical_daily(
    historical_timestamp TIMESTAMP PRIMARY KEY,
    data JSONB NOT NULL
);

