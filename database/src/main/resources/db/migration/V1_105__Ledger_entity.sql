CREATE TABLE IF NOT EXISTS ledger_entity
(
    id                   SERIAL PRIMARY KEY,
    uuid                 VARCHAR(128) NOT NULL,
    address              VARCHAR(128) NOT NULL,
    name                 VARCHAR(128) NOT NULL,
    type                 VARCHAR(128) NOT NULL,
    owner_type           VARCHAR(128) NOT NULL,
    usd_pricing_exponent INT NOT NULL,
);

CREATE INDEX IF NOT EXISTS idx_ledger_entity_uuid ON ledger_entity (uuid);
CREATE INDEX IF NOT EXISTS idx_ledger_entity_address ON ledger_entity (address);
CREATE INDEX IF NOT EXISTS idx_ledger_entity_type ON ledger_entity (type);