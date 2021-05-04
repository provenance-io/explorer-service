select 'Altering marker_cache' as comment;

ALTER TABLE marker_cache
    RENAME COLUMN total_supply TO supply;
ALTER TABLE marker_cache
    ADD COLUMN IF NOT EXISTS last_tx_timestamp TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS marker_cache_sort_idx ON marker_cache(last_tx_timestamp, supply, denom);

WITH q AS (
    SELECT tmj.marker_id, MAX(tc.tx_timestamp) AS timestamp
    FROM tx_cache tc
             JOIN tx_marker_join tmj ON tc.id = tmj.tx_hash_id
    GROUP BY tmj.marker_id
)
UPDATE marker_cache mc
SET last_tx_timestamp = q.timestamp
FROM q
WHERE mc.id = q.marker_id
  AND mc.last_tx_timestamp IS NOT NULL;
