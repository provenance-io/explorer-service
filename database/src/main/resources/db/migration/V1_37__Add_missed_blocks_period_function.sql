CREATE OR REPLACE FUNCTION missed_block_periods(fromHeight int, toHeight int, address varchar(128) = NULL)
    RETURNS TABLE (val_cons_address varchar(128), blocks int[]) AS
$func$
DECLARE
    r  missed_blocks; -- use table type as row variable
    r0 missed_blocks;
BEGIN

    FOR r IN
        SELECT
            *
        FROM missed_blocks t
        WHERE t.block_height between fromHeight and toHeight
          AND (address IS NULL OR t.val_cons_address = address)
        ORDER BY t.val_cons_address, t.block_height
        LOOP
            IF ( r.val_cons_address,  r.block_height)
                <> (r0.val_cons_address, r0.block_height + 1) THEN -- not true for first row

                RETURN QUERY
                    SELECT r0.val_cons_address, blocks; -- output row

                blocks := ARRAY[r.block_height];     -- start new array
            ELSE
                blocks := blocks || r.block_height;   -- add to array - year can be NULL, too
            END IF;

            r0 := r;                       -- remember last row
        END LOOP;

    RETURN QUERY                      -- output last iteration
        SELECT r0.val_cons_address, blocks;

END
$func$ LANGUAGE plpgsql;
