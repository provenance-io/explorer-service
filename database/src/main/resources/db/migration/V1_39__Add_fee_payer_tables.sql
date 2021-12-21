CREATE TABLE IF NOT EXISTS tx_feepayer
(
    id           SERIAL PRIMARY KEY,
    block_height INT          NOT NULL,
    tx_hash_id   INT          NOT NULL,
    tx_hash      VARCHAR(64)  NOT NULL,
    payer_type   VARCHAR(128) NOT NULL,
    address_id   INT          NOT NULL,
    address      VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS tx_feepayer_hash_idx ON tx_feepayer (tx_hash);
CREATE INDEX IF NOT EXISTS tx_feepayer_hash_id_idx ON tx_feepayer (tx_hash_id);
CREATE INDEX IF NOT EXISTS tx_feepayer_address_idx ON tx_feepayer (address);
CREATE INDEX IF NOT EXISTS tx_feepayer_address_id_idx ON tx_feepayer (address_id);
CREATE UNIQUE INDEX IF NOT EXISTS tx_feepayer_unique_idx ON tx_feepayer (tx_hash_id, payer_type, address_id);

CREATE TABLE IF NOT EXISTS tx_fee
(
    id           SERIAL PRIMARY KEY,
    block_height INT          NOT NULL,
    tx_hash_id   INT          NOT NULL,
    tx_hash      VARCHAR(64)  NOT NULL,
    fee_type     VARCHAR(128) NOT NULL,
    marker_id    INT          NOT NULL,
    marker       VARCHAR(256) NOT NULL,
    amount       NUMERIC      NOT NULL
);

CREATE INDEX IF NOT EXISTS tx_fee_height_idx ON tx_fee (block_height);
CREATE INDEX IF NOT EXISTS tx_fee_hash_idx ON tx_fee (tx_hash);
CREATE INDEX IF NOT EXISTS tx_fee_hash_id_idx ON tx_fee (tx_hash_id);
CREATE INDEX IF NOT EXISTS tx_fee_fee_type_idx ON tx_fee (fee_type);
CREATE INDEX IF NOT EXISTS tx_fee_marker_id_idx ON tx_fee (marker_id);
CREATE INDEX IF NOT EXISTS tx_fee_marker_idx ON tx_fee (marker);
CREATE UNIQUE INDEX IF NOT EXISTS tx_fee_unique_idx ON tx_fee (tx_hash_id, fee_type, marker_id);

SELECT 'Inserting tx fees into tx_fee table' AS comment;
WITH base AS (
    SELECT tc.height,
           tc.id,
           tc.hash,
           CASE WHEN bp.min_gas_fee > 100 THEN 1905 ELSE 0.025 END minGasFee,
           amounts.denom                                           denom,
           amounts.amount::numeric                                 amount,
           (tc.tx_v2 -> 'tx_response' ->> 'gas_wanted')::numeric   wanted,
           (tc.tx_v2 -> 'tx_response' ->> 'gas_used')::numeric     used
    FROM tx_cache tc
             JOIN block_proposer bp ON tc.height = bp.block_height,
         jsonb_to_recordset(tc.tx_v2 -> 'tx' -> 'auth_info' -> 'fee' -> 'amount') amounts("denom" text, "amount" text)
),
     feeAmount AS (
         SELECT base.id txId,
                mc.id denomId,
                base.denom,
                MAX(CASE WHEN base.denom = 'nhash' THEN round(used * minGasFee) END)            baseFeeUsed,
                MAX(CASE
                        WHEN base.denom = 'nhash'
                            THEN round(wanted * minGasFee) - round(used * minGasFee) END)  baseFeeOverage,
                MAX(CASE WHEN base.denom = 'nhash' THEN amount - round(wanted * minGasFee) END) priority,
                MAX(CASE WHEN base.denom != 'nhash' THEN amount END)                            msgBasedFee
         FROM base
                  JOIN marker_cache mc ON base.denom = mc.denom
         GROUP BY base.id, mc.id, base.denom
     ),
     arrays AS (
         SELECT txId,
                denomId,
                denom,
                ARRAY [baseFeeUsed, baseFeeOverage, priority, msgBasedFee]                    feeArray,
                ARRAY ['BASE_FEE_USED', 'BASE_FEE_OVERAGE', 'PRIORITY_FEE', 'MSG_BASED_FEE'] typeArray
         FROM feeAmount
     ),
     matched AS (
         SELECT arrays.txId,
                arrays.denomId,
                arrays.denom,
                unnest(arrays.feeArray)  fee,
                unnest(arrays.typeArray) typed
         FROM arrays
     )
INSERT INTO tx_fee (block_height, tx_hash_id, tx_hash, fee_type, marker_id, marker, amount)
SELECT base.height, base.id, base.hash, matched.typed, matched.denomId, matched.denom, matched.fee
FROM base
         JOIN matched ON base.id = matched.txId
WHERE fee IS NOT NULL AND fee != 0
ON CONFLICT (tx_hash_id, fee_type, marker_id) DO NOTHING;

SELECT 'Done inserting tx fees into tx_fee table' AS comment;

SELECT 'Inserting tx feepayer into tx_feepayer table' AS comment;
WITH base AS (
    SELECT tc.height,
           tc.id,
           tc.hash,
           feeObj.granter,
           feeObj.payer,
           signer.public_key
    FROM tx_cache tc,
         jsonb_to_record(tc.tx_v2 -> 'tx' -> 'auth_info' -> 'fee') feeObj("amount" jsonb, "gas_limit" integer, "payer" text, "granter" text),
         jsonb_to_record(tc.tx_v2 -> 'tx' -> 'auth_info' -> 'signer_infos' -> 0) signer("public_key" jsonb, "mode_info" jsonb, "sequence" integer)
),
     signers AS (
         SELECT base.id txId,
                s.id    sigId,
                sj.join_key
         FROM base
                  JOIN signature s ON base.public_key = s.pubkey_object
                  JOIN signature_join sj on s.id = sj.signature_id
         WHERE sj.join_type = 'ACCOUNT'
     ),
     arrays AS (
         SELECT base.id                                                    txId,
                unnest(ARRAY [base.granter, base.payer, signers.join_key]) payer,
                unnest(ARRAY ['GRANTER', 'PAYER', 'FIRST_SIGNER'])         typed
         FROM base
                  JOIN signers on base.id = signers.txId
     ),
     matched AS (
         SELECT arrays.txId,
                arrays.typed payerType,
                account.id   addressId,
                arrays.payer address
         FROM arrays
                  JOIN account ON arrays.payer = account.account_address
     )
INSERT
INTO tx_feepayer (block_height, tx_hash_id, tx_hash, payer_type, address_id, address)
SELECT base.height,
       base.id,
       base.hash,
       matched.payerType,
       matched.addressId,
       matched.address
FROM base
         JOIN matched on base.id = matched.txId
ON CONFLICT (tx_hash_id, payer_type, address_id) DO NOTHING;

SELECT 'Done inserting tx feepayer into tx_feepayer table' AS comment;
