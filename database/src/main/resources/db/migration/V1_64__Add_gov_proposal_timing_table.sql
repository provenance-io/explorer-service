SELECT 'Updating gov_proposal table' AS comment;

ALTER TABLE gov_proposal
    ADD COLUMN IF NOT EXISTS deposit_param_check_height INT NOT NULL DEFAULT -1,
    ADD COLUMN IF NOT EXISTS voting_param_check_height  INT NOT NULL DEFAULT -1;

CREATE INDEX IF NOT EXISTS block_timestamp_minute_idx ON block_cache (date_trunc('minute', block_timestamp));

SELECT 'Inserting new gov_proposal data columns' AS comment;
WITH base AS (
    SELECT proposal_id,
           CASE
               WHEN data ->> 'voting_start_time' = '0001-01-01T00:00:00Z' THEN (data ->> 'deposit_end_time')::timestamp
               ELSE
                   (data ->> 'voting_start_time')::timestamp END AS vote_start,
           data ->> 'voting_end_time'                            AS voting_end_str,
           CASE
               WHEN data ->> 'voting_end_time' = '0001-01-01T00:00:00Z' THEN null
               ELSE
                   (data ->> 'voting_end_time')::timestamp END   AS voting_end
    FROM gov_proposal gp
    ORDER BY proposal_id
),
     deposit_blocks AS (
         SELECT height,
                block_timestamp
         FROM block_cache bc,
              base
         WHERE date_trunc('minute', bc.block_timestamp) = date_trunc('minute', base.vote_start)
            -- allows us to capture the blocks on the frontside or backside of the minute interval
            OR date_trunc('minute', bc.block_timestamp) = date_trunc('minute', base.vote_start) + INTERVAL '1 MINUTE'
            OR date_trunc('minute', bc.block_timestamp) = date_trunc('minute', base.vote_start) - INTERVAL '1 MINUTE'
     ),
     deposit_match AS (
         SELECT base.proposal_id,
                base.vote_start,
                max(db.height) AS height
         FROM deposit_blocks db,
              base
         WHERE block_timestamp <= base.vote_start
         GROUP BY base.proposal_id, base.vote_start
     ),
     voting_blocks AS (
         SELECT height,
                block_timestamp
         FROM block_cache bc,
              base
         WHERE date_trunc('minute', bc.block_timestamp) = date_trunc('minute', base.voting_end)
            -- allows us to capture the blocks on the frontside or backside of the minute interval
            OR date_trunc('minute', bc.block_timestamp) = date_trunc('minute', base.voting_end) + INTERVAL '1 MINUTE'
            OR date_trunc('minute', bc.block_timestamp) = date_trunc('minute', base.voting_end) - INTERVAL '1 MINUTE'
     ),
     voting_match AS (
         SELECT base.proposal_id,
                base.voting_end,
                max(vb.height) AS height
         FROM voting_blocks vb,
              base
         WHERE base.voting_end IS NOT NULL
           AND block_timestamp <= base.voting_end
         GROUP BY base.proposal_id, base.voting_end
     )
UPDATE gov_proposal gp
SET deposit_param_check_height = q.deposit_height,
    voting_param_check_height  = q.voting_height
FROM (
         SELECT base.proposal_id,
                base.vote_start AS      deposit_param_height,
                COALESCE(dm.height, -1) deposit_height,
                base.voting_end AS      voting_param_height,
                COALESCE(vm.height, -1) voting_height
         FROM base
                  LEFT JOIN deposit_match dm ON base.proposal_id = dm.proposal_id
                  LEFT JOIN voting_match vm ON base.proposal_id = vm.proposal_id
     ) q
WHERE gp.proposal_id = q.proposal_id;

SELECT 'Updating insert_gov_proposal() procedure' AS comment;
create or replace procedure insert_gov_proposal(proposals gov_proposal[], tx_height integer, tx_id integer,
                                                timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    gp gov_proposal;
BEGIN
    FOREACH gp IN ARRAY proposals
        LOOP
            INSERT INTO gov_proposal(proposal_id, proposal_type, address_id, address, is_validator, title, description,
                                     status, data, content, block_height, tx_hash, tx_timestamp, tx_hash_id,
                                     deposit_param_check_height, voting_param_check_height)
            VALUES (gp.proposal_id,
                    gp.proposal_type,
                    gp.address_id,
                    gp.address,
                    gp.is_validator,
                    gp.title,
                    gp.description,
                    gp.status,
                    gp.data,
                    gp.content,
                    tx_height,
                    gp.tx_hash,
                    timez,
                    tx_id,
                    gp.deposit_param_check_height,
                    gp.voting_param_check_height)
            ON CONFLICT (proposal_id) DO UPDATE
                SET status                     = gp.status,
                    data                       = gp.data,
                    tx_hash                    = gp.tx_hash,
                    tx_timestamp               = timez,
                    block_height               = tx_height,
                    deposit_param_check_height = gp.deposit_param_check_height,
                    voting_param_check_height  = gp.voting_param_check_height;
        END LOOP;
END;
$$;

SELECT 'Creating get_last_block_before_timestamp() function' AS comment;
CREATE OR REPLACE FUNCTION get_last_block_before_timestamp(input timestamp without time zone DEFAULT NULL::timestamp without time zone)
    RETURNS integer
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF (input IS NULL) THEN
        RETURN -1;
    ELSE
        RETURN (
            WITH voting_blocks AS (
                SELECT height, block_timestamp
                FROM block_cache bc
                WHERE date_trunc('minute', bc.block_timestamp) = date_trunc('minute', input)
                   -- allows us to capture the blocks on the frontside or backside of the minute interval
                   OR date_trunc('minute', bc.block_timestamp) = date_trunc('minute', input) + INTERVAL '1 MINUTE'
                   OR date_trunc('minute', bc.block_timestamp) = date_trunc('minute', input) - INTERVAL '1 MINUTE')
            SELECT COALESCE(max(vb.height), -1) AS height
            FROM voting_blocks vb
            WHERE input IS NOT NULL
              AND block_timestamp <= input
            LIMIT 1
        );
    END IF;
END
$$;
