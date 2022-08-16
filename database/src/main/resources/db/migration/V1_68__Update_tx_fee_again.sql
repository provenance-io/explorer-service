SELECT 'Update tx_fee ingest' AS comment;


-- Correct recipient to null if blank
UPDATE tx_fee
SET recipient = null
WHERE msg_type IS NOT NULL
  AND recipient = '';

-- delete the duplicate fee records
DELETE FROM tx_fee
WHERE id IN
      (SELECT id
       FROM
           (SELECT id,
                   ROW_NUMBER() OVER( PARTITION BY block_height, tx_hash, fee_type, COALESCE(msg_type, ''), COALESCE(recipient, '') ORDER BY  id ) AS row_num
            FROM tx_fee ) t
       WHERE t.row_num > 1);

-- Correct index for nullable fields
DROP INDEX IF EXISTS tx_fee_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tx_fee_unique_idx ON tx_fee (tx_hash_id, fee_type, COALESCE(msg_type,''), marker_id, COALESCE(recipient, ''));

create or replace procedure insert_tx_fees(txfees tx_fee[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    tf tx_fee;
BEGIN
    FOREACH tf IN ARRAY txFees
        LOOP
            INSERT INTO tx_fee(tx_hash, fee_type, marker_id, marker, amount, block_height, tx_hash_id, msg_type, recipient, orig_fees)
            VALUES (tf.tx_hash, tf.fee_type, tf.marker_id, tf.marker, tf.amount, tx_height, tx_id, tf.msg_type, tf.recipient, tf.orig_fees)
            ON CONFLICT (tx_hash_id, fee_type, COALESCE(msg_type, ''), marker_id, COALESCE(recipient, '')) DO UPDATE
            SET
                amount = tf.amount,
                orig_fees = tf.orig_fees
            ;
        END LOOP;
END;
$$;
