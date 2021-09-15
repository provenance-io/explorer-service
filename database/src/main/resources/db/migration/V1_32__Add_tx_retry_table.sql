
SELECT 'Add block_tx_retry' AS comment;
CREATE TABLE IF NOT EXISTS block_tx_retry(
    height INT NOT NULL PRIMARY KEY,
    retried BOOLEAN NOT NULL DEFAULT FALSE,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    error_block TEXT DEFAULT NULL
);

INSERT INTO block_tx_retry (height)
SELECT height
FROM (
         SELECT bc.height, bc.tx_count, count(tc.id) count
         FROM block_cache bc
                  LEFT JOIN tx_cache tc ON bc.height = tc.height
         WHERE bc.tx_count > 0
         GROUP BY bc.height, bc.tx_count
         ORDER BY bc.height DESC
     ) q
WHERE count != tx_count
ON CONFLICT DO NOTHING;

