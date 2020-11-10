CREATE TABLE block_cache
(
    height   bigint PRIMARY KEY,
    block JSONB NOT NULL,
    last_hit TIMESTAMP NOT NULL,
    hit_count INT NOT NULL
);

CREATE TABLE validators_cache
(
    height   bigint PRIMARY KEY,
    validators JSONB NOT NULL,
    last_hit TIMESTAMP NOT NULL,
    hit_count INT NOT NULL
);
