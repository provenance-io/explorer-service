SELECT 'Updating bad denoms' AS comment;
UPDATE marker_cache
SET status = 'MARKER_STATUS_UNSPECIFIED'
WHERE marker_type = 'DENOM';
