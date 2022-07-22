SELECT 'Update tx_fee table' AS comment;

ALTER TABLE tx_fee
    ADD COLUMN IF NOT EXISTS recipient varchar(128),
    ADD COLUMN IF NOT EXISTS orig_fees JSONB;

DROP INDEX IF EXISTS tx_fee_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tx_fee_unique_idx ON tx_fee (tx_hash_id, fee_type, COALESCE(msg_type,''), marker_id, recipient);


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
            ON CONFLICT (tx_hash_id, fee_type, COALESCE(msg_type, ''), marker_id, recipient) DO NOTHING;
END LOOP;
END;
$$;


