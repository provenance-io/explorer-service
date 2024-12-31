CREATE TABLE IF NOT EXISTS row_counts
(
    id            VARCHAR(20) NOT NULL PRIMARY KEY,
    current_count BIGINT      NOT NULL
);

CREATE OR REPLACE FUNCTION tx_cache_row_counts_trigger()
RETURNS TRIGGER
LANGUAGE PLPGSQL
AS
$$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE row_counts
        SET current_count = current_count + 1
        WHERE id = 'tx_cache';
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE row_counts
        SET current_count = current_count - 1
        WHERE id = 'tx_cache';
    END IF;

    RETURN NEW;
END;
$$;

    -- Create the trigger only if it does not already exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'row_counts_trigger'
    ) THEN
CREATE TRIGGER row_counts_trigger
    BEFORE INSERT OR DELETE
ON tx_cache
        FOR EACH ROW
        EXECUTE FUNCTION tx_cache_row_counts_trigger();
END IF;
END;
$$;

INSERT INTO row_counts (id, current_count)
SELECT 'tx_cache', COUNT(*) FROM tx_cache;