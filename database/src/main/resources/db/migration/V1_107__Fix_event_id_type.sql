create or replace procedure insert_tx_msg_event_attr(txAttrs tx_msg_event_attr[], eventId BIGINT)
    language plpgsql as
$$
DECLARE
    attr tx_msg_event_attr;
BEGIN
    FOREACH attr IN ARRAY txAttrs
        LOOP
            INSERT INTO tx_msg_event_attr(tx_msg_event_id, attr_key, attr_value, attr_idx, attr_hash)
            VALUES (eventId, attr.attr_key, attr.attr_value, attr.attr_idx, attr.attr_hash)
            ON CONFLICT (tx_msg_event_id, attr_hash) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_tx_msg_event(txEvents tx_event[], msgId integer, tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    event   tx_event;
    eventId BIGINT;
BEGIN
    FOREACH event IN ARRAY txEvents
        LOOP
            WITH e AS (
                INSERT INTO tx_msg_event (tx_hash, block_height, tx_hash_id, tx_message_id, event_type, tx_msg_type_id)
                    VALUES ((event).txEvent.tx_hash,
                            tx_height,
                            tx_id,
                            msgId,
                            (event).txEvent.event_type,
                            (event).txEvent.tx_msg_type_id)
                    ON CONFLICT (tx_hash_id, tx_message_id, event_type) DO NOTHING
                    RETURNING id
            )
            SELECT *
            FROM e
            UNION
            SELECT id
            FROM tx_msg_event
            WHERE tx_hash_id = tx_id
              AND tx_message_id = msgId
              AND event_type = (event).txEvent.event_type
            INTO eventId;
            CALL insert_tx_msg_event_attr((event).txattrs, eventId);
        END LOOP;
END;
$$;

DO
$$
    DECLARE
v_column_type TEXT;
BEGIN
        -- Check if the column exists and get its current data type
SELECT data_type
INTO v_column_type
FROM information_schema.columns
WHERE table_name = 'tx_msg_event_attr'
  AND column_name = 'tx_msg_event_id';

-- If the column is of type INTEGER, change it to BIGINT
IF v_column_type = 'integer' THEN
ALTER TABLE tx_msg_event_attr ALTER COLUMN tx_msg_event_id SET DATA TYPE BIGINT;
RAISE NOTICE 'Column tx_msg_event_id in table tx_msg_event_attr changed to BIGINT.';
ELSE
            RAISE NOTICE 'Column tx_msg_event_id is already BIGINT or does not exist.';
END IF;
END
$$;
