select 'Altering tx_cache' as comment;

-- 3 minutes
ALTER TABLE tx_cache
    DROP CONSTRAINT IF EXISTS transaction_cache_pkey,
    ADD COLUMN IF NOT EXISTS id SERIAL PRIMARY KEY,
    DROP COLUMN IF EXISTS last_hit,
    DROP COLUMN IF EXISTS hit_count;
CREATE UNIQUE INDEX IF NOT EXISTS tx_cache_hash_idx ON tx_cache(hash);

select 'Altering tx_address_join' as comment;
ALTER TABLE tx_address_join
    ADD COLUMN IF NOT EXISTS tx_hash_id INT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS tx_address_join_hash_id_idx ON tx_address_join(tx_hash_id);

select 'Updating tx_address_join' as comment;
-- 4 min 29 sec
UPDATE tx_address_join
SET tx_hash_id = tx_cache.id
FROM tx_cache
WHERE tx_address_join.tx_hash = tx_cache.hash;

select 'Altering tx_marker_join' as comment;
ALTER TABLE tx_marker_join
    ADD COLUMN IF NOT EXISTS tx_hash_id INT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS tx_marker_join_hash_id_idx ON tx_marker_join(tx_hash_id);

select 'Updating tx_marker_join' as comment;
-- 1 min 38 sec
UPDATE tx_marker_join
SET tx_hash_id = tx_cache.id
FROM tx_cache
WHERE tx_marker_join.tx_hash = tx_cache.hash;

select 'Altering tx_message' as comment;
ALTER TABLE tx_message
    ADD COLUMN IF NOT EXISTS tx_hash_id INT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS tx_message_hash_id_idx ON tx_message(tx_hash_id);

select 'Updating tx_message' as comment;
-- 4 min 16 sec
UPDATE tx_message
SET tx_hash_id = tx_cache.id
FROM tx_cache
WHERE tx_message.tx_hash = tx_cache.hash;

select 'Altering account' as comment;
ALTER TABLE account
    DROP CONSTRAINT IF EXISTS account_pkey,
    ADD COLUMN IF NOT EXISTS id SERIAL PRIMARY KEY;
CREATE UNIQUE INDEX IF NOT EXISTS account_address_idx ON account(account_address);

select 'Altering marker_cache' as comment;
ALTER TABLE marker_cache
    DROP CONSTRAINT IF EXISTS marker_cache_pkey,
    ADD COLUMN IF NOT EXISTS id SERIAL PRIMARY KEY;
CREATE UNIQUE INDEX IF NOT EXISTS marker_cache_address_idx ON marker_cache(marker_address);

select 'Altering staking_validator_cache' as comment;
ALTER TABLE staking_validator_cache
    DROP CONSTRAINT IF EXISTS staking_validator_cache_pkey,
    ADD COLUMN IF NOT EXISTS id SERIAL PRIMARY KEY,
    ADD COLUMN IF NOT EXISTS account_address varchar(128) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS consensus_address varchar(128) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS consensus_pubkey varchar(128) NOT NULL DEFAULT '',
    DROP COLUMN IF EXISTS last_hit,
    DROP COLUMN IF EXISTS hit_count;

select 'Updating staking_validator_cache' as comment;
UPDATE staking_validator_cache
SET account_address = validator_addresses.account_address,
    consensus_address = validator_addresses.consensus_address,
    consensus_pubkey = validator_addresses.consensus_pubkey
FROM validator_addresses
WHERE staking_validator_cache.operator_address = validator_addresses.operator_address;

select 'Altering staking_validator_cache Part 2' as comment;
CREATE UNIQUE INDEX IF NOT EXISTS staking_validator_account_idx ON staking_validator_cache(account_address);
CREATE UNIQUE INDEX IF NOT EXISTS staking_validator_operator_idx ON staking_validator_cache(operator_address);
CREATE UNIQUE INDEX IF NOT EXISTS staking_validator_consensus_addr_idx ON staking_validator_cache(consensus_address);
CREATE UNIQUE INDEX IF NOT EXISTS staking_validator_consensus_pubkey_idx ON staking_validator_cache(consensus_pubkey);

select 'Dropping validator_addresses' as comment;
DROP TABLE IF EXISTS validator_addresses;

select 'Altering tx_marker_join' as comment;
ALTER TABLE tx_marker_join
    ADD COLUMN IF NOT EXISTS marker_id INT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS tx_marker_join_marker_id_idx ON tx_marker_join(marker_id);

select 'Updating tx_marker_join' as comment;
-- 2 min 26 sec
UPDATE tx_marker_join
SET marker_id = marker_cache.id
FROM marker_cache
WHERE tx_marker_join.denom = marker_cache.denom;

select 'Altering tx_address_join' as comment;
ALTER TABLE tx_address_join
    ADD COLUMN IF NOT EXISTS address_type varchar(16) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS address_id INT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS tx_address_join_address_type_idx ON tx_address_join(address_type);
CREATE INDEX IF NOT EXISTS tx_address_join_address_id_idx ON tx_address_join(address_id);
CREATE INDEX IF NOT EXISTS tx_address_join_address_type_id_idx ON tx_address_join(address_type, address_id);

select 'Updating tx_address_join' as comment;
-- 40 sec
UPDATE tx_address_join
SET address_type = 'ACCOUNT',
    address_id = account.id
FROM account
WHERE tx_address_join.address = account.account_address;

select 'Updating tx_address_join Part 2' as comment;
-- 4 sec
UPDATE tx_address_join
SET address_type = 'OPERATOR',
    address_id = staking_validator_cache.id
FROM staking_validator_cache
WHERE tx_address_join.address = staking_validator_cache.operator_address;
