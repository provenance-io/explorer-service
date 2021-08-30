DROP INDEX IF EXISTS staking_validator_jailed_idx;
DROP INDEX IF EXISTS staking_validator_status_idx;
DROP INDEX IF EXISTS staking_validator_status_jailed_idx;
DROP INDEX IF EXISTS staking_validator_token_count_idx;

CREATE TABLE IF NOT EXISTS validator_state
(
    id               BIGSERIAL PRIMARY KEY,
    block_height     INT,
    operator_addr_id INT,
    operator_address VARCHAR(128),
    moniker          VARCHAR(128),
    status           VARCHAR(64),
    jailed           BOOLEAN          DEFAULT FALSE,
    token_count      NUMERIC NOT NULL DEFAULT 0,
    json             jsonb
);

CREATE UNIQUE INDEX IF NOT EXISTS validator_state_height_val_id_idx ON validator_state (block_height, operator_addr_id);

INSERT INTO validator_state(block_height, operator_addr_id, operator_address, moniker, status, jailed, token_count,
                            json)
SELECT bi.max_height_read,
       svc.id,
       svc.operator_address,
       svc.moniker,
       svc.status,
       svc.jailed,
       svc.token_count,
       svc.staking_validator
FROM staking_validator_cache svc,
     block_index bi;

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
                                         svc.consensus_pubkey
FROM validator_state vs
         JOIN staking_validator_cache svc on vs.operator_addr_id = svc.id
ORDER BY vs.operator_addr_id, vs.block_height desc
WITH DATA;

ALTER TABLE IF EXISTS staking_validator_cache
    DROP COLUMN IF EXISTS staking_validator,
    DROP COLUMN IF EXISTS moniker,
    DROP COLUMN IF EXISTS status,
    DROP COLUMN IF EXISTS jailed,
    DROP COLUMN IF EXISTS token_count;
