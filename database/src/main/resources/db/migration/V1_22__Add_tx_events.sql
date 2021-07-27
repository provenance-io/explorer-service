CREATE TABLE IF NOT EXISTS tx_msg_event (
    id                   SERIAL PRIMARY KEY,
    block_height         INT NOT NULL,
    tx_hash_id           INT NOT NULL,
    tx_hash              VARCHAR(64) NOT NULL,
    tx_message_id        INT NOT NULL,
    tx_message_type_id   VARCHAR(128) NOT NULL,
    event_type           VARCHAR(256) NOT NULL,
    tx_message_hash      TEXT NULL
);

CREATE TABLE IF NOT EXISTS tx_msg_event_attr (
    id              SERIAL PRIMARY KEY,
    tx_msg_event_id INT NOT NULL,
    attr_key        VARCHAR(256) NOT NULL,
    attr_value      TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS tx_events_msg_id_idx  ON tx_msg_event (tx_message_id);
CREATE INDEX IF NOT EXISTS tx_events_hash_idx    ON tx_msg_event (tx_hash);
CREATE INDEX IF NOT EXISTS tx_events_hash_id_idx ON tx_msg_event (tx_hash_id);
CREATE INDEX IF NOT EXISTS tx_msg_attr_event_idx ON tx_msg_event_attr (tx_msg_event_id);
CREATE INDEX IF NOT EXISTS tx_msg_attr_key       ON tx_msg_event_attr (attr_key);