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
          AND column_name = 'id';

        -- If the column is of type INTEGER, change it to BIGINT
        IF v_column_type = 'integer' THEN
            ALTER TABLE tx_msg_event_attr ALTER COLUMN id SET DATA TYPE BIGINT;
            ALTER SEQUENCE tx_msg_event_attr_id_seq AS BIGINT;
            RAISE NOTICE 'Column id in table tx_msg_event_attr changed to BIGINT.';
        ELSE
            RAISE NOTICE 'Column tx_msg_event_attr is already BIGINT or does not exist.';
        END IF;
    END
$$;

