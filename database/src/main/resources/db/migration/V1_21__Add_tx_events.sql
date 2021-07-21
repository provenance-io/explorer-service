CREATE TABLE IF NOT EXISTS tx_events
(
    id                   SERIAL PRIMARY KEY,
    block_height         INT NOT NULL,
    tx_hash_id           INT NOT NULL,
    tx_hash              VARCHAR(64) NOT NULL,
    tx_message_id        INT NOT NULL,
    tx_message_type_id   INT         NOT NULL,
    event_type           VARCHAR(128) NOT NULL,
    event_attribute_list JSONB NOT NULL
);
