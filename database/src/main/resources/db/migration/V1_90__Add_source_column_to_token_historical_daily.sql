SELECT 'Add source column to token historical daily' AS comment;
ALTER TABLE token_historical_daily
    ADD COLUMN source TEXT;

UPDATE token_historical_daily
SET source = 'dlob';