
SELECT 'Delete Incorrect Fees' AS comment;


delete
from tx_fee tf
where id in (
    select
        tf.id
    from tx_message tm
             join tx_fee tf on tm.tx_hash_id = tf.tx_hash_id
    where tf.fee_type = 'MSG_BASED_FEE' and tf.msg_type = 'write_scope'
    group by tm.id, tm.tx_hash, tm.block_height, tf.id
    having array_agg(distinct tm.tx_message_type_id) && ARRAY[116,95]
       and (array_agg(distinct tm.tx_message_type_id) @> ARRAY[38]) is not true
);

REFRESH MATERIALIZED VIEW tx_history_chart_data_hourly;
REFRESH MATERIALIZED VIEW fee_type_data_hourly;
REFRESH MATERIALIZED VIEW tx_type_data_hourly;
