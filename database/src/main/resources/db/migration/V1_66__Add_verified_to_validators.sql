SELECT 'Adding validator verified to validator_state' AS comment;


ALTER TABLE validator_state
ADD COLUMN IF NOT EXISTS verified BOOLEAN NOT NULL DEFAULT FALSE;

SELECT 'Modify `current_validator_state` view' AS comment;
DROP MATERIALIZED VIEW IF EXISTS current_validator_state;
CREATE MATERIALIZED VIEW IF NOT EXISTS current_validator_state AS
SELECT DISTINCT ON (vs.operator_addr_id) vs.operator_addr_id,
                                         vs.operator_address,
                                         vs.block_height,
                                         vs.moniker,
                                         vs.status,
                                         vs.jailed,
                                         vs.token_count,
                                         vs.json,
                                         svc.account_address,
                                         svc.consensus_address,
                                         svc.consensus_pubkey,
                                         vs.commission_rate,
                                         vs.verified
FROM validator_state vs
         JOIN staking_validator_cache svc on vs.operator_addr_id = svc.id
ORDER BY vs.operator_addr_id, vs.block_height desc
WITH DATA;


SELECT 'Modify `get_validator_list` function' AS comment;
DROP FUNCTION IF EXISTS get_validator_list(integer, varchar, text, integer, integer, text[]);
CREATE OR REPLACE FUNCTION get_validator_list(active_set integer, active_status character varying, search_state text, search_limit integer, search_offset integer, consensus_set text[] DEFAULT NULL, search_verified boolean DEFAULT NULL)
    returns TABLE(operator_addr_id integer, operator_address character varying, block_height integer, moniker character varying, status character varying, jailed boolean, token_count numeric, json jsonb, account_address character varying, consensus_address character varying, consensus_pubkey character varying, commission_rate numeric, verified boolean, validator_state text)
    language plpgsql
as
$$
BEGIN

    RETURN QUERY
        WITH active AS (
            SELECT cvs.*
            FROM current_validator_state cvs
            WHERE cvs.status = active_status
              AND cvs.jailed = false
            ORDER BY cvs.token_count DESC
            LIMIT active_set
        ),
             jailed AS (
                 SELECT cvs.*
                 FROM current_validator_state cvs
                 WHERE cvs.jailed = true
             ),
             candidate AS (
                 SELECT cvs.*
                 FROM current_validator_state cvs
                          LEFT JOIN active a ON cvs.operator_address = a.operator_address
                 WHERE cvs.jailed = false
                   AND a.operator_address IS NULL
             ),
             state AS (
                 SELECT cvs.operator_address,
                        CASE
                            WHEN a.operator_address IS NOT NULL THEN 'active'
                            WHEN j.operator_address IS NOT NULL THEN 'jailed'
                            WHEN c.operator_address IS NOT NULL THEN 'candidate'
                            END validator_state
                 FROM current_validator_state cvs
                          LEFT JOIN active a ON cvs.operator_address = a.operator_address
                          LEFT JOIN jailed j ON cvs.operator_address = j.operator_address
                          LEFT JOIN candidate c ON cvs.operator_address = c.operator_address
             )
        SELECT cvs.*,
               s.validator_state
        FROM current_validator_state cvs
                 LEFT JOIN state s ON cvs.operator_address = s.operator_address
        WHERE s.validator_state = search_state
          AND (consensus_set IS NULL OR cvs.consensus_address = ANY (consensus_set))
          AND (search_verified IS NULL OR cvs.verified = search_verified)
        ORDER BY s.validator_state, cvs.token_count DESC
        LIMIT search_limit OFFSET search_offset;
END
$$;


SELECT 'Modify `get_all_validator_state` function' AS comment;
DROP FUNCTION IF EXISTS get_all_validator_state(integer, varchar, text[]);
CREATE  OR REPLACE FUNCTION get_all_validator_state(active_set integer, active_status character varying, consensus_set text[] DEFAULT NULL, search_verified boolean DEFAULT NULL)
    returns TABLE(operator_addr_id integer, operator_address character varying, block_height integer, moniker character varying, status character varying, jailed boolean, token_count numeric, json jsonb, account_address character varying, consensus_address character varying, consensus_pubkey character varying, commission_rate numeric, verified boolean, validator_state text)
    language plpgsql
as
$$
BEGIN

    RETURN QUERY
        WITH active AS (
            SELECT cvs.*
            FROM current_validator_state cvs
            WHERE cvs.status = active_status
              AND cvs.jailed = false
            ORDER BY cvs.token_count DESC
            LIMIT active_set
        ),
             jailed AS (
                 SELECT cvs.*
                 FROM current_validator_state cvs
                 WHERE cvs.jailed = true
             ),
             candidate AS (
                 SELECT cvs.*
                 FROM current_validator_state cvs
                          LEFT JOIN active a ON cvs.operator_address = a.operator_address
                 WHERE cvs.jailed = false
                   AND a.operator_address IS NULL
             ),
             state AS (
                 SELECT cvs.operator_address,
                        CASE
                            WHEN a.operator_address IS NOT NULL THEN 'active'
                            WHEN j.operator_address IS NOT NULL THEN 'jailed'
                            WHEN c.operator_address IS NOT NULL THEN 'candidate'
                            END validator_state
                 FROM current_validator_state cvs
                          LEFT JOIN active a ON cvs.operator_address = a.operator_address
                          LEFT JOIN jailed j ON cvs.operator_address = j.operator_address
                          LEFT JOIN candidate c ON cvs.operator_address = c.operator_address
             )
        SELECT cvs.*,
               s.validator_state
        FROM current_validator_state cvs
                 LEFT JOIN state s ON cvs.operator_address = s.operator_address
        WHERE (consensus_set IS NULL OR cvs.consensus_address = ANY (consensus_set))
          AND (search_verified IS NULL OR cvs.verified = search_verified)
        ORDER BY s.validator_state, cvs.token_count DESC;
END
$$;
