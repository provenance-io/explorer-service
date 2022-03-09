create function get_all_validator_state(active_set integer, active_status character varying, consensus_set text[] DEFAULT NULL::text[])
    returns TABLE
            (
                operator_addr_id  integer,
                operator_address  character varying,
                block_height      integer,
                moniker           character varying,
                status            character varying,
                jailed            boolean,
                token_count       numeric,
                json              jsonb,
                account_address   character varying,
                consensus_address character varying,
                consensus_pubkey  character varying,
                validator_state   text
            )
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
        ORDER BY s.validator_state, cvs.token_count DESC;
END
$$;
