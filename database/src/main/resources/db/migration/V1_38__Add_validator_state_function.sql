CREATE OR REPLACE FUNCTION get_validator_list(active_set integer, active_status varchar(64), search_state text,
                                              search_limit integer, search_offset integer,
                                              consensus_set text[] DEFAULT NULL)
    RETURNS TABLE
            (
                operator_addr_id  integer,
                operator_address  varchar(128),
                block_height      integer,
                moniker           varchar(128),
                status            varchar(64),
                jailed            boolean,
                token_count       numeric,
                json              jsonb,
                account_address   varchar(128),
                consensus_address varchar(128),
                consensus_pubkey  varchar(128),
                validator_state   text
            )
AS
$func$
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
        ORDER BY s.validator_state, cvs.token_count DESC
        LIMIT search_limit OFFSET search_offset;
END
$func$ LANGUAGE plpgsql;
