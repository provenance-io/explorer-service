SELECT 'Remove running_count and total_count columns from the missed_blocks table' AS comment;

DROP INDEX IF EXISTS missed_blocks_running_count_idx;

ALTER TABLE missed_blocks
DROP COLUMN IF EXISTS running_count,
DROP COLUMN IF EXISTS total_count;