SELECT 'Modify `current_validator_state` view' AS comment;
DROP MATERIALIZED VIEW IF EXISTS current_validator_state;

CREATE VIEW IF NOT EXISTS current_validator_state AS
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
    vs.removed,
    ai.image_url
FROM validator_state vs
    JOIN staking_validator_cache svc on vs.operator_addr_id = svc.id
    LEFT JOIN address_image ai ON svc.operator_address = ai.address
ORDER BY vs.operator_addr_id, vs.block_height desc;
