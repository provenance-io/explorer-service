INSERT INTO cache_update(cache_key, description, cache_value, last_updated)
VALUES ('spotlight_processing', 'True to process spotlight, else false', 'false', now());

INSERT INTO cache_update(cache_key, description, cache_value, last_updated)
VALUES ('standard_block_time', 'The default value of time between blocks', '6.00', now());
