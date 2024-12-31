CREATE TABLE row_counts
(
    id            VARCHAR(20) NOT NULL PRIMARY KEY,
    current_count BIGINT      NOT NULL
);

CREATE
OR REPLACE FUNCTION row_counts_trigger()
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

CREATE TRIGGER row_counts_trigger
    BEFORE INSERT OR DELETE
ON tx_cache
FOR EACH ROW
EXECUTE FUNCTION row_counts_trigger();
