SELECT 'Backfill MSG_BASED_FEE from tx-level additionalfee attribute' AS comment;

-- Backfill tx_fee with MSG_BASED_FEE rows for txs that have tx-level "additionalfee" in events
-- but no existing MSG_BASED_FEE or CUSTOM_FEE row
--
-- Usage: CALL backfill_additionalfee_fees(from_height, to_height);
-- Example: CALL backfill_additionalfee_fees(29000000, 29500000);
--
-- After backfill, to refresh gas fee volume aggregates (tx_gas_fee_volume_day/hour):
-- 1. Mark affected tx_gas_cache rows for reprocessing, e.g.:
--    UPDATE tx_gas_cache SET processed = false WHERE tx_hash_id IN (SELECT id FROM tx_cache WHERE height >= from_height AND height <= to_height);
-- 2. CALL update_gas_fee_volume();

CREATE OR REPLACE PROCEDURE backfill_additionalfee_fees(
    from_height INT,
    to_height INT
)
    LANGUAGE plpgsql
AS
$$
DECLARE
    inserted_count INT;
BEGIN
    WITH tc AS (
        SELECT t.id,
               t.hash,
               t.height,
               t.tx_timestamp AS tx_ts,
               t.tx_v2
        FROM tx_cache t
        WHERE t.height >= from_height
          AND t.height <= to_height
          AND t.error_code IS NULL
          AND t.tx_v2 -> 'tx_response' IS NOT NULL
          AND jsonb_typeof(t.tx_v2 -> 'tx_response' -> 'events') = 'array'
    ),
         -- Txs that have an additionalfee attribute on a tx-type event (one row per tx)
         with_additionalfee AS (
             SELECT tc.id,
                    tc.hash,
                    tc.height,
                    tc.tx_ts,
                    MAX((regexp_match(attr->>'value', '^([0-9]+)(.*)$'))[1]::numeric) AS amount,
                    (array_agg(COALESCE(NULLIF(trim((regexp_match(attr->>'value', '^([0-9]+)(.*)$'))[2]), ''), 'nhash')))[1] AS denom
             FROM tc
                      CROSS JOIN LATERAL jsonb_array_elements(tc.tx_v2 -> 'tx_response' -> 'events') AS ev
                      CROSS JOIN LATERAL jsonb_array_elements(ev->'attributes') AS attr
             WHERE ev->>'type' = 'tx'
               AND attr->>'key' = 'additionalfee'
               AND (regexp_match(attr->>'value', '^([0-9]+)(.*)$'))[1] IS NOT NULL
               AND (regexp_match(attr->>'value', '^([0-9]+)(.*)$'))[1]::numeric > 0
             GROUP BY tc.id, tc.hash, tc.height, tc.tx_ts
         ),
         -- Exclude txs that already have MSG_BASED_FEE or CUSTOM_FEE
         missing AS (
             SELECT w.id, w.hash, w.height, w.tx_ts, w.amount, w.denom
             FROM with_additionalfee w
             WHERE NOT EXISTS (
                 SELECT 1 FROM tx_fee tf
                 WHERE tf.tx_hash_id = w.id
                   AND tf.fee_type IN ('MSG_BASED_FEE', 'CUSTOM_FEE')
             )
         )
    INSERT INTO tx_fee (block_height, tx_hash_id, tx_hash, fee_type, marker_id, marker, amount, msg_type, recipient, orig_fees, tx_timestamp)
    SELECT m.height,
           m.id,
           m.hash,
           'MSG_BASED_FEE',
           mc.id,
           mc.denom,
           m.amount,
           'msg_based_fee',
           NULL,
           NULL,
           m.tx_ts
    FROM missing m
             JOIN marker_cache mc ON mc.denom = m.denom
    ON CONFLICT (tx_hash_id, fee_type, COALESCE(msg_type, ''), marker_id, COALESCE(recipient, '')) DO NOTHING;

    GET DIAGNOSTICS inserted_count = ROW_COUNT;
    RAISE NOTICE 'backfill_additionalfee_fees(%, %): inserted % rows', from_height, to_height, inserted_count;
END;
$$;
