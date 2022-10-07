SELECT 'Update IBC chained denom to actual denom' AS comment;

DO
$$
    DECLARE
        channel_denom marker_cache;
        base_denom    varchar;
        base_match    marker_cache;
        channel_join  tx_marker_join;
    BEGIN

        -- Identify any denoms that contain "channel"
-- get their ids
-- For each channel marker
        FOR channel_denom IN SELECT * FROM marker_cache WHERE denom LIKE '%channel%'
            LOOP
                RAISE NOTICE 'channel denom is (%), id (%)', channel_denom.denom, channel_denom.id;
                -- reduce the channel denom down to its base
                WITH arr AS (SELECT string_to_array(channel_denom.denom, '/') AS ary)
                SELECT ary[array_upper(ary, 1)]
                INTO base_denom
                FROM arr;

                RAISE NOTICE 'base denom is (%)', base_denom;

-- find matching marker_cache record
                SELECT * INTO base_match FROM marker_cache WHERE denom = base_denom LIMIT 1;

                RAISE NOTICE 'base match is (%), id (%)', base_match.denom, base_match.id;

-- find matching tx_marker_join records
                FOR channel_join IN SELECT *
                                    FROM tx_marker_join
                                    WHERE marker_id = channel_denom.id AND denom = channel_denom.denom
                    LOOP
                        perform
                        FROM tx_marker_join
                        WHERE marker_id = base_match.id
                          and denom = base_match.denom
                          AND tx_hash_id = channel_join.tx_hash_id;
                        IF NOT FOUND THEN
                            UPDATE tx_marker_join
                            SET marker_id = base_match.id,
                                denom     = base_match.denom
                            WHERE marker_id = channel_denom.id
                              AND denom = channel_denom.denom
                              AND tx_hash_id = channel_join.tx_hash_id;
                        ELSE
                            DELETE
                            FROM tx_marker_join
                            WHERE marker_id = channel_denom.id
                              AND denom = channel_denom.denom
                              AND tx_hash_id = channel_join.tx_hash_id;
                        END IF;

                    end loop;

-- delete channel denoms
                DELETE
                FROM marker_cache
                WHERE id = channel_denom.id;

            END LOOP;
    END;

$$ LANGUAGE plpgsql;
