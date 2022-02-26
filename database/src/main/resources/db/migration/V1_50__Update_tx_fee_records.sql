SELECT 'Deleting from tx_fee table' AS comment;
DELETE FROM tx_fee WHERE true;

-- Necessary for timing issues with testnet
ALTER TABLE tx_fee
DROP COLUMN IF EXISTS msg_type;

DROP INDEX IF EXISTS tx_fee_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tx_fee_unique_idx ON tx_fee (tx_hash_id, fee_type, marker_id);

SELECT 'Inserting tx fees into tx_fee table' AS comment;
WITH base AS (
    SELECT tc.height,
           tc.id,
           tc.hash,
           tc.error_code is null                                 success,
           vmr.market_rate                                       marketRate,
           amounts.denom                                         denom,
           amounts.amount::numeric                               amount,
           (tc.tx_v2 -> 'tx_response' ->> 'gas_wanted')::numeric wanted,
           (tc.tx_v2 -> 'tx_response' ->> 'gas_used')::numeric   used
    FROM tx_cache tc
             JOIN validator_market_rate vmr ON tc.id = vmr.tx_hash_id,
         jsonb_to_recordset(tc.tx_v2 -> 'tx' -> 'auth_info' -> 'fee' -> 'amount') amounts("denom" text, "amount" text)
),
     feeAmount AS (
         SELECT base.id                                              txId,
                mc.id                                                denomId,
                base.denom,
                MAX(CASE
                        WHEN base.denom = 'nhash' AND used > wanted
                            THEN round(amount)
                        WHEN base.denom = 'nhash' AND used <= wanted
                            THEN round(used * marketRate)
                    END)                                             baseFeeUsed,
                MAX(CASE
                        WHEN base.denom = 'nhash' AND used > wanted
                            THEN 0
                        WHEN base.denom = 'nhash' AND used <= wanted
                            THEN round(amount) - round(used * marketRate)
                    END)                                             baseFeeOverage,
                MAX(CASE WHEN base.denom != 'nhash' THEN amount END) msgBasedFee
         FROM base
                  JOIN marker_cache mc ON base.denom = mc.denom
         GROUP BY base.id, mc.id, base.denom
     ),
     arrays AS (
         SELECT txId,
                denomId,
                denom,
                ARRAY [baseFeeUsed, baseFeeOverage, msgBasedFee]             feeArray,
                ARRAY ['BASE_FEE_USED', 'BASE_FEE_OVERAGE', 'MSG_BASED_FEE'] typeArray
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

DELETE FROM token_distribution_amounts WHERE true;
