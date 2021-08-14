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
