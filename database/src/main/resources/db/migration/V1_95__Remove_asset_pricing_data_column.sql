SELECT 'Remove asset pricing data column' AS comment;

ALTER TABLE asset_pricing
DROP COLUMN IF EXISTS data;
