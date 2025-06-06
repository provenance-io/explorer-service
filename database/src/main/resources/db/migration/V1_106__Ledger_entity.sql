CREATE TABLE IF NOT EXISTS ledger_entity
(
    id                   SERIAL PRIMARY KEY,
    uuid                 VARCHAR(128) NOT NULL,
    name                 VARCHAR(128) NOT NULL,
    type                 VARCHAR(128) NOT NULL,
    data_source          VARCHAR(128) NOT NULL,
    usd_pricing_exponent INT
);

CREATE INDEX IF NOT EXISTS idx_ledger_entity_uuid ON ledger_entity (uuid);
CREATE INDEX IF NOT EXISTS idx_ledger_entity_type ON ledger_entity (type);

CREATE TABLE IF NOT EXISTS ledger_entity_spec
(
    id               SERIAL PRIMARY KEY,
    entity_uuid      VARCHAR(128) NOT NULL,
    specification_id VARCHAR(128) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ledger_entity_spec_entity_uuid ON ledger_entity_spec (entity_uuid);
CREATE INDEX IF NOT EXISTS idx_ledger_entity_spec_specification_id ON ledger_entity_spec (specification_id);
