CREATE TABLE IF NOT EXISTS tx_events
(
    id                   SERIAL PRIMARY KEY,
    block_height         INT NOT NULL,
    tx_hash_id           INT NOT NULL,
    tx_hash              VARCHAR(64) NOT NULL,
    tx_message_id        TEXT NOT NULL,
    --tx_message_type_id   INT NOT NULL, -- I don't think we need this right?
    event_type           VARCHAR(256) NOT NULL,
    tx_message_hash      TEXT NULL
);

CREATE TABLE IF NOT EXISTS tx_message_event_attributes(
    id                   SERIAL PRIMARY KEY,
    tx_message_event_id  INT NOT NULL,
    attribute_key        VARCHAR(256) NOT NULL,
    attribute_value TEXT NOT NULL
)

CREATE INDEX IF NOT EXISTS tx_events_message_id_idx        ON tx_events (tx_message_id);
CREATE INDEX IF NOT EXISTS tx_message_attributes_event_idx ON tx_message_event_attributes (tx_message_event_id);
--Do we need to add any other indexes?