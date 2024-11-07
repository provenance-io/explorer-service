SELECT 'Add nav events table' AS comment;

CREATE TABLE nav_events (
    id SERIAL PRIMARY KEY,
    block_height INT NOT NULL,
    block_time TIMESTAMP NOT NULL,
    tx_hash TEXT,
    event_order INT,
    event_type TEXT,
    scope_id TEXT,
    denom TEXT,
    price_amount BIGINT,
    price_denom TEXT,
    volume BIGINT,
    source TEXT,
    UNIQUE (block_height, tx_hash, event_order)
);

CREATE INDEX idx_nav_events_block_time ON nav_events(block_time);
CREATE INDEX idx_nav_events_denom ON nav_events(denom);
CREATE INDEX idx_nav_events_scope_id ON nav_events(scope_id);