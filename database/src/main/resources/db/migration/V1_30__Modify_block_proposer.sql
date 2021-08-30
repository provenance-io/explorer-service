
SELECT 'Updating block_proposer' as comment;
ALTER TABLE block_proposer
    ADD COLUMN IF NOT EXISTS block_latency NUMERIC;

UPDATE block_proposer
SET block_latency = sq.lag
FROM (
         SELECT height,
                extract(EPOCH FROM block_timestamp) -
                extract(EPOCH FROM LAG(block_timestamp) OVER (ORDER BY height)) AS lag
         FROM block_cache
         ORDER BY height
     ) AS sq
WHERE block_height = sq.height;

-- Calc latency for new blocks
CREATE OR REPLACE PROCEDURE update_block_latency()
    LANGUAGE plpgsql
AS
$$
DECLARE
    min_unprocessed INT;
    min_block   INT;
BEGIN
    -- Used to rule out the very first record seeing as it will never have a latency
    SELECT min(height) FROM block_cache INTO min_block;
    SELECT min(block_height)
    FROM block_proposer
    WHERE block_latency IS NULL
      AND block_height != min_block
    INTO min_unprocessed;

    UPDATE block_proposer
    SET block_latency = sq.lag
    FROM (
             SELECT bc.height,
                    extract(EPOCH FROM bc.block_timestamp) -
                    extract(EPOCH FROM LAG(bc.block_timestamp) OVER (ORDER BY bc.height)) AS lag
             FROM block_cache bc
             WHERE bc.height >= min_unprocessed - 1
             ORDER BY bc.height
         ) AS sq
    WHERE block_height = sq.height
      AND block_latency IS NULL;

    RAISE INFO 'UPDATED block_proposer';
END;
$$;

SELECT 'Updating spotlight_cache' as comment;
DROP TABLE IF EXISTS spotlight_cache;

CREATE TABLE IF NOT EXISTS spotlight_cache
(
    id BIGSERIAL PRIMARY KEY,
    spotlight JSONB NOT NULL,
    last_hit TIMESTAMP NOT NULL
);




