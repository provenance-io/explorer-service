SELECT 'Adding marker_unit table' AS comment;

CREATE TABLE IF NOT EXISTS marker_unit
(
    id        SERIAL PRIMARY KEY,
    marker_id INT          NOT NULL,
    marker    VARCHAR(256) NOT NULL,
    unit      VARCHAR(256) NOT NULL,
    exponent  INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS marker_unit_unique_idx ON marker_unit(marker_id, unit);
