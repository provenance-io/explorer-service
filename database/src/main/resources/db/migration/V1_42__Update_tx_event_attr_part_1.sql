SELECT 'Update tx_msg_event_attr.attr_idx' AS comment;
DO
$$
    DECLARE
        msg_ids int[];
        msg_id  int;
    BEGIN
        SELECT array_agg(DISTINCT tx_msg_event_id) FROM tx_msg_event_attr INTO msg_ids;

        FOREACH msg_id IN ARRAY msg_ids
            LOOP
                UPDATE tx_msg_event_attr
                SET attr_idx = t.rn - 1
                FROM (
                         SELECT id,
                                tx_msg_event_id,
                                row_number() OVER (PARTITION BY tx_msg_event_id ORDER BY id) AS rn
                         FROM tx_msg_event_attr
                         WHERE tx_msg_event_attr.tx_msg_event_id = msg_id
                     ) t
                WHERE t.id = tx_msg_event_attr.id;
            END LOOP;
    END
$$;
