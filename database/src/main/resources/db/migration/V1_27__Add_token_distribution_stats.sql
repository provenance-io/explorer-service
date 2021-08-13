-- Host future and previous migrated data
CREATE TABLE IF NOT EXISTS token_distribution_amounts
(
    id    SERIAL PRIMARY KEY,
    range VARCHAR(8) UNIQUE NOT NULL,
    data  jsonb
);

CREATE TABLE IF NOT EXISTS token_distribution_paginated_results
(
    id            SERIAL PRIMARY KEY,
    owner_address varchar(128) UNIQUE NOT NULL,
    data          jsonb               NOT NULL
);

INSERT INTO token_distribution_amounts(range)
VALUES ('1-5'),
       ('6-10'),
       ('11-50'),
       ('51-100'),
       ('101-500'),
       ('501-1000'),
       ('1001-')
ON CONFLICT DO NOTHING;

-- Update gas stats with latest data
CREATE OR REPLACE PROCEDURE calculate_token_distribution_ranks(varchar[])
    LANGUAGE plpgsql
AS
$$
DECLARE
    x varchar[];
BEGIN

    FOREACH x SLICE 1 IN ARRAY $1
        LOOP
            UPDATE token_distribution_amounts
            SET data = x::jsonb
            WHERE range = (x::jsonb) -> 'range';
        END LOOP;

    RAISE INFO 'UPDATED token distribution amounts';
END;
$$;
