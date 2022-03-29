SELECT 'Adding announcements table' AS comment;

CREATE TABLE IF NOT EXISTS announcements
(
    id            SERIAL PRIMARY KEY,
    ann_timestamp TIMESTAMP     NOT NULL,
    title         VARCHAR(1000) NOT NULL,
    body          TEXT          NOT NULL
);

CREATE INDEX IF NOT EXISTS announcements_timestamp_idx ON announcements (ann_timestamp);
