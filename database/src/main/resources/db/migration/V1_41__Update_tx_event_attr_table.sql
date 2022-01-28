SELECT 'Update tx_msg_event_attr table with attr_idx, attr_hash' AS comment;
ALTER TABLE tx_msg_event_attr
    ADD COLUMN IF NOT EXISTS attr_idx  INT  DEFAULT 0,
    ADD COLUMN IF NOT EXISTS attr_hash TEXT DEFAULT NULL;
