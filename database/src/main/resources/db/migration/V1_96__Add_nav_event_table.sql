SELECT 'Add nav events table' AS comment;

CREATE TABLE nav_events (
    block_height INT,
    block_time TIMESTAMPTZ,
    chain_id INT,
    tx_hash TEXT,
    event_order INT,
    event_type TEXT,
    scope_id TEXT,
    denom TEXT,
    price_amount BIGINT,
    price_denom TEXT,
    volume BIGINT,
    source TEXT,
    PRIMARY KEY (block_height, chain_id, tx_hash, event_order)
);

CREATE INDEX idx_nav_events_block_time ON nav_events(block_time);
CREATE INDEX idx_nav_events_denom ON nav_events(denom);
CREATE INDEX idx_nav_events_scope_id ON nav_events(scope_id);
