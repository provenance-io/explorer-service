SELECT 'Deleting from tx_message' AS comment;
DELETE FROM tx_message
WHERE id IN
      (SELECT id
       FROM (SELECT id,
                    ROW_NUMBER() OVER ( PARTITION BY tx_hash_id, tx_message_hash, msg_idx ORDER BY id ) AS row_num
             FROM tx_message) t
       WHERE t.row_num > 1);

SELECT 'Deleting from tx_msg_event' AS comment;
DELETE FROM tx_msg_event
WHERE id IN
      (SELECT id
       FROM (SELECT id,
                    ROW_NUMBER() OVER ( PARTITION BY tx_hash_id, tx_message_id, event_type ORDER BY id ) AS row_num
             FROM tx_msg_event) t
       WHERE t.row_num > 1);


SELECT 'Deleting from tx_msg_event_attr' AS comment;
DELETE FROM tx_msg_event_attr
WHERE id IN
      (SELECT id
       FROM (SELECT id,
                    ROW_NUMBER() OVER ( PARTITION BY tx_msg_event_id, attr_key, attr_value ORDER BY id ) AS row_num
             FROM tx_msg_event_attr) t
       WHERE t.row_num > 1);
