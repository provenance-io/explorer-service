CREATE TABLE IF NOT EXISTS missed_blocks
(
    id SERIAL PRIMARY KEY,
    block_height INT NOT NULL,
    val_cons_address VARCHAR(128) NOT NULL,
    running_count INT NOT NULL,
    total_count INT NOT NULL
);

CREATE INDEX IF NOT EXISTS missed_blocks_block_height_idx ON missed_blocks(block_height);
CREATE INDEX IF NOT EXISTS missed_blocks_val_cons_address_idx ON missed_blocks(val_cons_address);
CREATE INDEX IF NOT EXISTS missed_blocks_running_count_idx ON missed_blocks(running_count);
CREATE UNIQUE INDEX IF NOT EXISTS missed_blocks_height_addr_idx ON missed_blocks(block_height, val_cons_address);

