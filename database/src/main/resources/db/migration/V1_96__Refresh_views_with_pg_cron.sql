SELECT 'Refresh views with pg_cron' AS comment;

CREATE EXTENSION IF NOT EXISTS pg_cron;

SELECT cron.schedule('0 2 * * *', $$
    REFRESH MATERIALIZED VIEW tx_history_chart_data_hourly;
    REFRESH MATERIALIZED VIEW tx_type_data_hourly;
    REFRESH MATERIALIZED VIEW fee_type_data_hourly;
$$);

