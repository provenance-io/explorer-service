CREATE TABLE IF NOT EXISTS signature
(
    id               SERIAL PRIMARY KEY,
    base_64_sig      VARCHAR(128) NOT NULL,
    pubkey_type      VARCHAR(128) NOT NULL,
    pubkey_object    JSONB        NOT NULL,
    multi_sig_object JSONB        NULL
);

-- Can join to transaction, validator, account. Whatever has a pubkey attached to it
CREATE TABLE IF NOT EXISTS signature_join
(
    id           SERIAL PRIMARY KEY,
    join_type    VARCHAR(128) NOT NULL,
    join_key     VARCHAR(128) NOT NULL,
    signature_id INT          NOT NULL
);

ALTER TABLE transaction_cache
    DROP COLUMN IF EXISTS signer;

ALTER TABLE account
ADD PRIMARY KEY (account_address);
