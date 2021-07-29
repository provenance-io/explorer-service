-- Host future and previous migrated data
CREATE TABLE IF NOT EXISTS tx_single_message_cache
(
    id              SERIAL PRIMARY KEY,
    tx_timestamp    TIMESTAMP          NOT NULL,
    tx_hash         VARCHAR(64) UNIQUE NOT NULL,
    gas_used        INT                NOT NULL,
    tx_message_type VARCHAR(128)       NOT NULL
);

CREATE INDEX IF NOT EXISTS tx_single_message_cache_gas_used_idx
    ON tx_single_message_cache (gas_used);

CREATE INDEX IF NOT EXISTS tx_single_message_cache_tx_message_type_idx
    ON tx_single_message_cache (tx_message_type);

-- Aggregated HOURLY data for GAS statistics.
CREATE TABLE IF NOT EXISTS tx_single_message_gas_stats_day
(
    tx_timestamp    TIMESTAMP    NOT NULL,
    min_gas_used    INT          NOT NULL,
    max_gas_used    INT          NOT NULL,
    avg_gas_used    INT          NOT NULL,
    stddev_gas_used INT          NOT NULL,
    tx_message_type VARCHAR(128) NOT NULL,
    PRIMARY KEY (tx_timestamp, tx_message_type)
);

CREATE TABLE IF NOT EXISTS tx_single_message_gas_stats_hour
(
    tx_timestamp    TIMESTAMP    NOT NULL,
    min_gas_used    INT          NOT NULL,
    max_gas_used    INT          NOT NULL,
    avg_gas_used    INT          NOT NULL,
    stddev_gas_used INT          NOT NULL,
    tx_message_type VARCHAR(128) NOT NULL,
    PRIMARY KEY (tx_timestamp, tx_message_type)
);

-- Migrate historical data to tx_single_message_cache
WITH single_message_txs AS (
    SELECT tm.tx_hash,
           -- avoids adding to GROUP BY
           string_agg(tc.tx_timestamp::TEXT, ',') AS tx_timestamp,
           string_agg(tc.gas_used::TEXT, ',')     AS gas_used,
           string_agg(tmt.type::TEXT, ',')        AS tx_message_type
    FROM tx_message tm
             INNER JOIN tx_cache tc
                        ON tc.id = tm.tx_hash_id
             INNER JOIN tx_message_type tmt
                        ON tm.tx_message_type_id = tmt.id
    GROUP BY tm.tx_hash
    HAVING count(tm.tx_hash) = 1
),
     stats AS (
         SELECT cast(tx_timestamp AS TIMESTAMP) AS tx_timestamp,
                tx_hash,
                cast(gas_used AS INT)           AS gas_used,
                tx_message_type
         FROM single_message_txs
     )
INSERT
INTO tx_single_message_cache (tx_timestamp, tx_hash, gas_used, tx_message_type)
SELECT *
FROM STATS
ON CONFLICT DO NOTHING;

-- Daily aggregate of historical data
-- DANGER: If you run this more than once it will generate duplicate data
INSERT
INTO tx_single_message_gas_stats_day
SELECT date_trunc('DAY', tx_timestamp)           AS tx_timestamp,
       min(gas_used)                             AS min_gas_used,
       max(gas_used)                             AS max_gas_used,
       round(avg(gas_used))                      AS avg_gas_used,
       coalesce(round(stddev_samp(gas_used)), 0) AS stddev_gas_used,
       tx_message_type
FROM tx_single_message_cache
GROUP BY date_trunc('DAY', tx_timestamp),
         tx_message_type
ON CONFLICT DO NOTHING;

-- Hourly aggregate of historical data
-- DANGER: If you run this more than once it will generate duplicate data
INSERT
INTO tx_single_message_gas_stats_hour
SELECT date_trunc('HOUR', tx_timestamp)          AS tx_timestamp,
       min(gas_used)                             AS min_gas_used,
       max(gas_used)                             AS max_gas_used,
       round(avg(gas_used))                      AS avg_gas_used,
       coalesce(round(stddev_samp(gas_used)), 0) AS stddev_gas_used,
       tx_message_type
FROM tx_single_message_cache
GROUP BY date_trunc('HOUR', tx_timestamp),
         tx_message_type
ON CONFLICT DO NOTHING;

-- Update gas stats with latest data
CREATE OR REPLACE PROCEDURE update_gas_stats(TIMESTAMP, VARCHAR[])
    LANGUAGE plpgsql
AS
$$
DECLARE
    var varchar;
BEGIN
    -- Delete old records and update with new aggregate counts
    FOREACH var IN ARRAY $2
        LOOP
            EXECUTE
                format('DELETE
                        FROM %I
                        WHERE tx_timestamp >= date_trunc(%L, %L::TIMESTAMP)'::TEXT
                    , 'tx_single_message_gas_stats_' || var, var, $1);

            EXECUTE
                format('INSERT
                        INTO %I
                        SELECT date_trunc(%L, tx_timestamp)              AS tx_timestamp,
                               min(gas_used)                             AS min_gas_used,
                               max(gas_used)                             AS max_gas_used,
                               round(avg(gas_used))                      AS avg_gas_used,
                               coalesce(round(stddev_samp(gas_used)), 0) AS stddev_gas_used,
                               tx_message_type
                        FROM tx_single_message_cache
                        WHERE date_trunc(%L, tx_timestamp) >= date_trunc(%L, %L::TIMESTAMP)
                        GROUP BY date_trunc(%L, tx_timestamp),
                                 tx_message_type'::TEXT
                    , 'tx_single_message_gas_stats_' || var, var, var, var, $1, var);
        END LOOP;

    RAISE INFO 'UPDATED gas stats for %', $2;
END;
$$;