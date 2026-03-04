SELECT 'Add ignore_in_value_calculations column to marker_cache' AS comment;

ALTER TABLE marker_cache
    ADD COLUMN IF NOT EXISTS ignore_in_value_calculations BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS marker_cache_ignore_in_value_calculations_idx 
    ON marker_cache(ignore_in_value_calculations) 
    WHERE ignore_in_value_calculations = TRUE;
