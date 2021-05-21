
select 'Altering staking_validator_cache' as comment;

ALTER TABLE staking_validator_cache
    ADD COLUMN IF NOT EXISTS token_count NUMERIC NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS staking_validator_token_count_idx ON staking_validator_cache(token_count);

UPDATE staking_validator_cache svc
SET token_count = (svc.staking_validator->>'tokens')::numeric
WHERE token_count = 0;
