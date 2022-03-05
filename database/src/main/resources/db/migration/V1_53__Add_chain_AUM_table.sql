SELECT 'Create `chain_um_hourly` table' AS comment;
CREATE TABLE IF NOT EXISTS chain_aum_hourly
(
    id       SERIAL PRIMARY KEY,
    datetime TIMESTAMP    NOT NULL,
    amount   NUMERIC      NOT NULL,
    denom    VARCHAR(256) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS chain_aum_hourly_unique_idx ON chain_aum_hourly(datetime);
CREATE INDEX IF NOT EXISTS chain_aum_hourly_date_idx ON chain_aum_hourly(date_trunc('DAY', datetime));

SELECT 'Inserting into `chain_um_hourly`' AS comment;
INSERT INTO chain_aum_hourly (datetime, amount, denom)
SELECT date_trunc('HOUR', last_hit), amount, denom
FROM (
         SELECT last_hit,
                spotlight -> 'total_aum' ->> 'denom' AS denom,
                (spotlight -> 'total_aum' ->> 'amount')::numeric AS amount
                 , row_number() over (partition by date_trunc('HOUR', last_hit)
             order by last_hit desc) as rn
         FROM spotlight_cache
         where (spotlight -> 'total_aum' ->> 'denom') is not null
         order by last_hit desc
     ) AS X
WHERE rn = 1
ON CONFLICT DO NOTHING ;
