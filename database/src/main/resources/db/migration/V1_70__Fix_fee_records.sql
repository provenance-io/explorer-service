SELECT 'Update tx_fee for non-feed txs' AS comment;

with base as (
    select tc.id, tc.height, tc.hash, tf.id as tf_id
    from tx_cache tc
             left join tx_fee tf ON tc.id = tf.tx_hash_id
    where tc.error_code in (8, 32) and tc.codespace = 'sdk'
)
UPDATE tx_fee
SET amount = 0
FROM base
WHERE tx_fee.id = base.tf_id
  and tx_fee.fee_type = 'BASE_FEE_USED';

with base as (
    select tc.id, tc.height, tc.hash, tf.id as tf_id
    from tx_cache tc
             left join tx_fee tf ON tc.id = tf.tx_hash_id
    where tc.error_code in (8, 32) and tc.codespace = 'sdk'
      and tf.fee_type != 'BASE_FEE_USED'
)
DELETE FROM tx_fee
    USING base
WHERE tx_fee.id = base.tf_id;
