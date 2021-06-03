CREATE TABLE IF NOT EXISTS gov_proposal
(
    id SERIAL PRIMARY KEY,
    proposal_id INT NOT NULL UNIQUE,
    proposal_type VARCHAR(128) NOT NULL,
    address_id INT NOT NULL,
    address VARCHAR(128) NOT NULL,
    is_validator BOOLEAN DEFAULT FALSE,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(128) NOT NULL,
    data JSONB NOT NULL,
    content JSONB NOT NULL,
    block_height  INT          NOT NULL,
    tx_hash       VARCHAR(64)  NOT NULL,
    tx_timestamp TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS gov_proposal_id_idx ON gov_proposal(proposal_id);
CREATE INDEX IF NOT EXISTS gov_proposal_status_idx ON gov_proposal(status);

CREATE TABLE IF NOT EXISTS gov_vote
(
    id SERIAL PRIMARY KEY,
    proposal_id INT NOT NULL,
    address_id INT NOT NULL,
    address VARCHAR(128) NOT NULL,
    is_validator BOOLEAN DEFAULT FALSE,
    vote VARCHAR(128) NOT NULL,
    block_height  INT          NOT NULL,
    tx_hash       VARCHAR(64)  NOT NULL,
    tx_timestamp TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS gov_vote_proposal_id_address_id_idx ON gov_vote(proposal_id, address_id);
CREATE INDEX IF NOT EXISTS gov_vote_proposal_id_idx ON gov_vote(proposal_id);
CREATE INDEX IF NOT EXISTS gov_vote_address_id_idx ON gov_vote(address_id);
CREATE INDEX IF NOT EXISTS gov_vote_vote_idx ON gov_vote(vote);
CREATE INDEX IF NOT EXISTS gov_vote_block_height_idx ON gov_vote(block_height);

CREATE TABLE IF NOT EXISTS gov_deposit
(
    id SERIAL PRIMARY KEY,
    proposal_id INT NOT NULL,
    address_id INT NOT NULL,
    address VARCHAR(128) NOT NULL,
    is_validator BOOLEAN DEFAULT FALSE,
    deposit_type VARCHAR(128) NOT NULL,
    amount DECIMAL NOT NULL,
    denom VARCHAR(256) NOT NULL,
    block_height  INT          NOT NULL,
    tx_hash       VARCHAR(64)  NOT NULL,
    tx_timestamp TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS gov_deposit_proposal_id_idx ON gov_deposit(proposal_id);
CREATE INDEX IF NOT EXISTS gov_deposit_address_id_idx ON gov_deposit(address_id);
CREATE INDEX IF NOT EXISTS gov_deposit_block_height_idx ON gov_deposit(block_height);

