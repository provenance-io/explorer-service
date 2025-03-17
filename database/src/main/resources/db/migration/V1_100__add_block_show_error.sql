CREATE OR REPLACE PROCEDURE add_block(bd block_update)
    LANGUAGE plpgsql
AS
$$
DECLARE
    tx_height INT; --block height
    timez     TIMESTAMP; -- block timestamp
    tu        tx_update;
BEGIN
    SELECT (bd).blocks.height INTO tx_height;
    SELECT (bd).blocks.block_timestamp INTO timez;
    -- insert block
    INSERT INTO block_cache(height, tx_count, block_timestamp, block, last_hit, hit_count)
    VALUES (tx_height,
            (bd).blocks.tx_count,
            timez,
            (bd).blocks.block,
            (bd).blocks.last_hit,
            (bd).blocks.hit_count)
    ON CONFLICT (height) DO NOTHING;
    -- insert block tx count
    INSERT INTO block_tx_count_cache(block_height, block_timestamp, tx_count)
    VALUES (tx_height, timez, (bd).blocks.tx_count)
    ON CONFLICT (block_height) DO NOTHING;
    -- insert block proposer fee
    INSERT INTO block_proposer(block_height, block_timestamp, proposer_operator_address, block_latency)
    VALUES (tx_height,
            timez,
            (bd).proposer.proposer_operator_address,
            (bd).proposer.block_latency)
    ON CONFLICT (block_height) DO NOTHING;
    -- insert validator cache
    INSERT INTO validators_cache(height, validators, last_hit, hit_count)
    VALUES (tx_height, (bd).validatorCache.validators, (bd).validatorCache.last_hit, (bd).validatorCache.hit_count)
    ON CONFLICT (height) DO NOTHING;
    -- for each tx
    FOREACH tu IN ARRAY (bd).txs
        LOOP
            CALL add_tx(tu, tx_height, timez);
        END LOOP;
    RAISE INFO 'UPDATED block';
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error saving block %. Error Code: %, Message: %.', (bd).blocks.height, SQLSTATE, SQLERRM;
END;
$$;
