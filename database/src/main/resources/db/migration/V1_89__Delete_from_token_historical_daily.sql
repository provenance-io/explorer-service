SELECT 'Deleting from token_historical_daily dates after 8/03/2023' AS comment;

DELETE FROM token_historical_daily WHERE historical_timestamp > '2023-08-03 00:00:00';
