SELECT '1.13 updates' AS comment;

-- new column_exists function
CREATE OR REPLACE FUNCTION column_exists(ptable TEXT, pcolumn TEXT)
    RETURNS BOOLEAN AS
$BODY$
DECLARE
    result bool;
BEGIN
    -- Does the requested column exist?
    SELECT COUNT(*)
    INTO result
    FROM information_schema.columns
    WHERE table_name = ptable
      and column_name = pcolumn;
    RETURN result;
END
$BODY$
    LANGUAGE plpgsql VOLATILE;

-- new rename_column_if_exists function
CREATE OR REPLACE PROCEDURE rename_column_if_exists(ptable TEXT, pcolumn TEXT, new_name TEXT)
    language plpgsql AS
$$
BEGIN
    -- Rename the column if it exists.
    IF column_exists(ptable, pcolumn) THEN
        EXECUTE FORMAT('ALTER TABLE %I RENAME COLUMN %I TO %I;',
                       ptable, pcolumn, new_name);
    END IF;
END;
$$;

-- Account changes
ALTER TABLE account
    ADD COLUMN IF NOT EXISTS owner           varchar(128) null,
    ADD COLUMN IF NOT EXISTS is_group_policy boolean default false;

-- Governance Changes
DROP INDEX if exists proposal_monitor_proposal_id_idx;

CREATE UNIQUE INDEX IF not exists proposal_monitor_unique_idx on proposal_monitor (proposal_id, matching_data_hash);

call rename_column_if_exists('gov_proposal', 'data', 'data_v1beta1');
call rename_column_if_exists('gov_proposal', 'content', 'content_v1beta1');

ALTER TABLE gov_proposal
    ALTER COLUMN data_v1beta1 DROP NOT NULL,
    ALTER COLUMN content_v1beta1 DROP NOT NULL,
    ALTER COLUMN proposal_type TYPE text,
    ADD COLUMN IF NOT EXISTS data_v1    jsonb NULL,
    ADD COLUMN IF NOT EXISTS content_v1 jsonb NULL;

ALTER TABLE gov_vote
    ADD COLUMN IF NOT EXISTS justification text NULL;

create or replace procedure insert_gov_proposal(proposals gov_proposal[], tx_height integer, tx_id integer,
                                                timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    gp gov_proposal;
BEGIN
    FOREACH gp IN ARRAY proposals
        LOOP
            INSERT INTO gov_proposal(proposal_id, proposal_type, address_id, address, is_validator, title, description,
                                     status, data_v1beta1, content_v1beta1, block_height, tx_hash, tx_timestamp,
                                     tx_hash_id,
                                     deposit_param_check_height, voting_param_check_height, data_v1, content_v1)
            VALUES (gp.proposal_id,
                    gp.proposal_type,
                    gp.address_id,
                    gp.address,
                    gp.is_validator,
                    gp.title,
                    gp.description,
                    gp.status,
                    gp.data_v1beta1,
                    gp.content_v1beta1,
                    tx_height,
                    gp.tx_hash,
                    timez,
                    tx_id,
                    gp.deposit_param_check_height,
                    gp.voting_param_check_height,
                    gp.data_v1,
                    gp.content_v1)
            ON CONFLICT (proposal_id) DO UPDATE
                SET status                     = gp.status,
                    tx_hash                    = CASE
                                                     WHEN tx_height < gov_proposal.block_height THEN gp.tx_hash
                                                     ELSE gov_proposal.tx_hash END,
                    tx_timestamp               = CASE
                                                     WHEN tx_height < gov_proposal.block_height THEN timez
                                                     ELSE gov_proposal.tx_timestamp END,
                    block_height               = CASE
                                                     WHEN tx_height < gov_proposal.block_height THEN tx_height
                                                     ELSE gov_proposal.block_height END,
                    tx_hash_id                 = CASE
                                                     WHEN tx_height < gov_proposal.block_height THEN tx_id
                                                     ELSE gov_proposal.tx_hash_id END,
                    deposit_param_check_height = gp.deposit_param_check_height,
                    voting_param_check_height  = gp.voting_param_check_height,
                    data_v1                    = gp.data_v1;
        END LOOP;
END;
$$;

create or replace procedure insert_gov_vote(votes gov_vote[], tx_height integer, tx_id integer,
                                            timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    gvaa integer[];
    gva  integer[];
    gv   gov_vote;
BEGIN
    SELECT DISTINCT ARRAY [(e::gov_vote).proposal_id, (e::gov_vote).address_id] arr
    FROM unnest(votes::gov_vote[]) AS e
    INTO gvaa;

    RAISE INFO 'distinct %', gvaa;

    IF gvaa IS NOT NULL THEN
        FOREACH gva SLICE 1 IN ARRAY gvaa
            LOOP
                DELETE FROM gov_vote WHERE proposal_id = gva[1] AND address_id = gva[2] AND tx_height > block_height;
            END LOOP;
    END IF;

    FOREACH gv IN ARRAY votes
        LOOP
            INSERT INTO gov_vote(proposal_id, address_id, address, block_height, tx_hash, tx_timestamp,
                                 is_validator, vote, tx_hash_id, weight, justification)
            VALUES (gv.proposal_id,
                    gv.address_id,
                    gv.address,
                    tx_height,
                    gv.tx_hash,
                    timez,
                    gv.is_validator,
                    gv.vote,
                    tx_id,
                    gv.weight,
                    gv.justification)
            ON CONFLICT (proposal_id, address_id, vote) DO UPDATE
                SET weight       = gv.weight,
                    block_height = tx_height,
                    tx_hash      = gv.tx_hash,
                    tx_timestamp = timez,
                    tx_hash_id   = tx_id
            WHERE tx_height > gov_vote.block_height;
        END LOOP;
END;
$$;

-- Groups Changes
CREATE TABLE IF NOT EXISTS groups
(
    id            INTEGER PRIMARY KEY,
    admin_address varchar(128) NOT NULL,
    group_data    JSONB        NOT NULL,
    group_members JSONB        NOT NULL,
    ver           integer      not null,
    block_height  INTEGER      NOT NULL,
    tx_hash_id    INTEGER      NOT NULL,
    tx_hash       VARCHAR(64)  NOT NULL,
    tx_timestamp  timestamp    NOT NULL
);

CREATE INDEX IF NOT EXISTS groups_admin_address_idx ON groups (admin_address);

CREATE TABLE IF NOT EXISTS groups_history
(
    id            SERIAL PRIMARY KEY,
    group_id      INTEGER      NOT NULL,
    admin_address varchar(128) NOT NULL,
    group_data    JSONB        NOT NULL,
    group_members JSONB        NOT NULL,
    ver           integer      not null,
    block_height  INTEGER      NOT NULL,
    tx_hash_id    INTEGER      NOT NULL,
    tx_hash       VARCHAR(64)  NOT NULL,
    tx_timestamp  timestamp    NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS groups_history_unique_idx ON groups_history (group_id, block_height, tx_hash_id);
CREATE INDEX IF NOT EXISTS groups_history_group_id_height_idx ON groups_history (group_id, block_height);

CREATE TABLE IF NOT EXISTS groups_policy
(
    id             SERIAL PRIMARY KEY,
    groups_id      INTEGER      NOT NULL,
    policy_address varchar(128) NOT NULL,
    admin_address  varchar(128) NOT NULL,
    policy_data    JSONB        NOT NULL,
    ver            integer      not null,
    block_height   INTEGER      NOT NULL,
    tx_hash_id     INTEGER      NOT NULL,
    tx_hash        VARCHAR(64)  NOT NULL,
    tx_timestamp   timestamp    NOT NULL
);

CREATE INDEX IF NOT EXISTS groups_policy_admin_address_idx ON groups_policy (admin_address);
CREATE INDEX IF NOT EXISTS groups_policy_groups_id_idx ON groups_policy (groups_id);
CREATE UNIQUE INDEX IF NOT EXISTS groups_policy_unique_idx ON groups_policy (groups_id, policy_address);

CREATE TABLE IF NOT EXISTS groups_policy_history
(
    id                SERIAL PRIMARY KEY,
    groups_id         INTEGER      NOT NULL,
    policy_address_id INTEGER      NOT NULL,
    policy_address    varchar(128) NOT NULL,
    policy_data       JSONB        NOT NULL,
    ver               integer      not null,
    block_height      INTEGER      NOT NULL,
    tx_hash_id        INTEGER      NOT NULL,
    tx_hash           VARCHAR(64)  NOT NULL,
    tx_timestamp      timestamp    NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS groups_policy_history_unique_idx ON groups_policy_history (groups_id, policy_address, block_height, tx_hash_id);
CREATE INDEX IF NOT EXISTS groups_policy_history_policy_address_height_idx ON groups_policy_history (policy_address, block_height);

CREATE TABLE IF NOT EXISTS groups_proposal
(
    id                 SERIAL PRIMARY KEY,
    groups_id          INTEGER      NOT NULL,
    policy_address_id  integer      not null,
    policy_address     varchar(128) NOT NULL,
    proposal_id        INTEGER      NOT NULL,
    proposal_data      JSONB        NOT NULL,
    proposal_node_data JSONB        NULL,
    proposal_status    varchar(128) NOT NULL,
    executor_result    varchar(128) NOT NULL,
    block_height       INTEGER      NOT NULL,
    tx_hash_id         INTEGER      NOT NULL,
    tx_hash            VARCHAR(64)  NOT NULL,
    tx_timestamp       timestamp    NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS groups_proposal_unique_idx ON groups_proposal (groups_id, policy_address, proposal_id);

CREATE TABLE IF NOT EXISTS groups_vote
(
    id           SERIAL PRIMARY KEY,
    proposal_id  INTEGER      NOT NULL,
    address_id   INTEGER      NOT NULL,
    address      varchar(128) NOT NULL,
    is_validator BOOLEAN DEFAULT FALSE,
    vote         varchar(128) NOT NULL,
    metadata     text         NOT NULL,
    weight       numeric      NOT NULL,
    block_height INTEGER      NOT NULL,
    tx_hash_id   INTEGER      NOT NULL,
    tx_hash      VARCHAR(64)  NOT NULL,
    tx_timestamp timestamp    NOT NULL
);

create index IF NOT EXISTS groups_vote_proposal_id_idx on groups_vote (proposal_id);
create index IF NOT EXISTS groups_vote_address_id_idx on groups_vote (address_id);
create index IF NOT EXISTS groups_vote_vote_idx on groups_vote (vote);
create index IF NOT EXISTS groups_vote_block_height_idx on groups_vote (block_height);
create unique index IF NOT EXISTS groups_vote_unique_idx on groups_vote (proposal_id, address_id);

create table if not exists tx_groups
(
    id           serial primary key,
    block_height integer     not null,
    tx_hash_id   integer     not null,
    tx_hash      varchar(64) not null,
    groups_id    integer     not null
);

create index if not exists tx_groups_hash_id_idx on tx_groups (tx_hash_id);
create index if not exists tx_groups_hash_idx on tx_groups (tx_hash);
create index if not exists tx_groups_groups_id_idx on tx_groups (groups_id);
create unique index if not exists tx_groups_unique_idx on tx_groups (tx_hash_id, groups_id);

create table if not exists tx_groups_policy
(
    id                serial primary key,
    block_height      integer      not null,
    tx_hash_id        integer      not null,
    tx_hash           varchar(64)  not null,
    policy_address_id integer      not null,
    policy_address    varchar(128) NOT NULL
);

create index if not exists tx_groups_policy_hash_id_idx on tx_groups_policy (tx_hash_id);
create index if not exists tx_groups_policy_hash_idx on tx_groups_policy (tx_hash);
create index if not exists tx_groups_policy_policy_address_id_idx on tx_groups_policy (policy_address_id);
create unique index if not exists tx_groups_policy_unique_idx on tx_groups_policy (tx_hash_id, policy_address_id);


-- ingest procedures updates
DROP TYPE IF EXISTS groups_policy_data CASCADE;
DROP TYPE IF EXISTS tx_update CASCADE;
DROP TYPE IF EXISTS block_update CASCADE;

create type groups_policy_data as
(
    groupsPolicy     groups_policy,
    groupsPolicyJoin tx_groups_policy[]
);

CREATE TYPE tx_update AS
(
    tx                  tx_cache,
    txGasFee            tx_gas_cache,
    txFees              tx_fee[],
    txMsgs              tx_msg[],
    singleMsg           tx_single_message_cache[],
    addressJoin         tx_address_join[],
    markerJoin          tx_marker_join[],
    nftJoin             tx_nft_join[],
    ibcJoin             tx_ibc[],
    proposals           gov_proposal[],
    proposalMonitors    proposal_monitor[],
    deposits            gov_deposit[],
    votes               gov_vote[],
    ibcLedgers          ibc_ledger[],
    ibcLedgerAcks       ibc_ledger_ack[],
    smCodes             tx_sm_code[],
    smContracts         tx_sm_contract[],
    sigs                signature_tx[],
    feepayers           tx_feepayer[],
    valMarketRate       validator_market_rate,
    groupsList          groups[],
    groupsJoin          tx_groups[],
    groupsPolicies      groups_policy_data[],
    groupsPolicyJoinAlt tx_groups_policy[],
    groupsProposals     groups_proposal[],
    groupsVotes         groups_vote[]
);

CREATE TYPE block_update AS
(
    blocks         block_cache,
    proposer       block_proposer,
    validatorCache validators_cache,
    txs            tx_update[]
);

create or replace procedure insert_groups(groupsList groups[], tx_height integer, tx_id integer,
                                          timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    gp groups;
BEGIN
    FOREACH gp IN ARRAY groupsList
        LOOP
            INSERT INTO groups(id, admin_address, group_data, group_members, ver, block_height, tx_hash_id, tx_hash,
                               tx_timestamp)
            VALUES (gp.id, gp.admin_address, gp.group_data, gp.group_members, gp.ver, tx_height, tx_id, gp.tx_hash,
                    timez)
            ON CONFLICT (id) DO UPDATE
                SET admin_address = gp.admin_address,
                    group_data    = gp.group_data,
                    group_members = gp.group_members,
                    ver           = gp.ver,
                    block_height  = tx_height,
                    tx_hash_id    = tx_id,
                    tx_hash       = gp.tx_hash,
                    tx_timestamp  = timez
            where tx_height > groups.block_height;

            insert into groups_history (group_id, admin_address, group_data, group_members, ver, block_height,
                                        tx_hash_id, tx_hash,
                                        tx_timestamp)
            select gp.id,
                   gp.admin_address,
                   gp.group_data,
                   gp.group_members,
                   gp.ver,
                   tx_height,
                   tx_id,
                   gp.tx_hash,
                   timez
            on conflict (group_id, block_height, tx_hash_id) DO nothing;
        END LOOP;
END;
$$;

create or replace procedure insert_tx_groups(groupsjoin tx_groups[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    tg tx_groups;
BEGIN
    FOREACH tg IN ARRAY groupsjoin
        LOOP
            INSERT INTO tx_groups(block_height, tx_hash_id, tx_hash, groups_id)
            VALUES (tx_height, tx_id, tg.tx_hash, tg.groups_id)
            ON CONFLICT (tx_hash_id, groups_id) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_tx_groups_policy(policyjoin tx_groups_policy[], tx_height integer, tx_id integer,
                                                    policyId integer default null)
    language plpgsql as
$$
DECLARE
    tgp tx_groups_policy;
BEGIN
    FOREACH tgp IN ARRAY policyjoin
        LOOP
            INSERT INTO tx_groups_policy(block_height, tx_hash_id, tx_hash, policy_address_id, policy_address)
            VALUES (tx_height, tx_id, tgp.tx_hash, COALESCE(policyId, tgp.policy_address_id), tgp.policy_address)
            ON CONFLICT (tx_hash_id, policy_address_id) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_groups_policy(policies groups_policy_data[], tx_height integer, tx_id integer,
                                                 timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    grpPolicy groups_policy_data;
    policyId  integer;
BEGIN
    FOREACH grpPolicy IN ARRAY policies
        LOOP
            WITH p AS (
                INSERT INTO groups_policy (groups_id, policy_address, admin_address, policy_data, ver, block_height,
                                           tx_hash_id, tx_hash,
                                           tx_timestamp)
                    VALUES ((grpPolicy).groupsPolicy.groups_id,
                            (grpPolicy).groupsPolicy.policy_address,
                            (grpPolicy).groupsPolicy.admin_address,
                            (grpPolicy).groupsPolicy.policy_data,
                            (grpPolicy).groupsPolicy.ver,
                            tx_height,
                            tx_id,
                            (grpPolicy).groupsPolicy.tx_hash,
                            timez)
                    ON CONFLICT (groups_id, policy_address) DO UPDATE
                        SET admin_address = (grpPolicy).groupsPolicy.admin_address,
                            policy_data = (grpPolicy).groupsPolicy.policy_data,
                            ver = (grpPolicy).groupsPolicy.ver,
                            block_height = tx_height,
                            tx_hash_id = tx_id,
                            tx_hash = (grpPolicy).groupsPolicy.tx_hash,
                            tx_timestamp = timez
                        where tx_height > groups_policy.block_height
                    RETURNING id)
            SELECT *
            FROM p
            UNION
            SELECT id
            FROM groups_policy
            WHERE groups_id = (grpPolicy).groupsPolicy.groups_id
              AND policy_address = (grpPolicy).groupsPolicy.policy_address
            INTO policyId;
            -- Insert tx_groups_policy
            CALL insert_tx_groups_policy((grpPolicy).groupsPolicyJoin, tx_height, tx_id, policyId);

            insert into groups_policy_history (groups_id, policy_address_id, policy_address, policy_data, ver,
                                               block_height,
                                               tx_hash_id, tx_hash, tx_timestamp)
            select (grpPolicy).groupsPolicy.groups_id,
                   policyId,
                   (grpPolicy).groupsPolicy.policy_address,
                   (grpPolicy).groupsPolicy.policy_data,
                   (grpPolicy).groupsPolicy.ver,
                   tx_height,
                   tx_id,
                   (grpPolicy).groupsPolicy.tx_hash,
                   timez
            on conflict (groups_id, policy_address, block_height, tx_hash_id) DO nothing;
        END LOOP;
END;
$$;

create or replace procedure insert_groups_proposal(proposals groups_proposal[], tx_height integer, tx_id integer,
                                                   timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    gp groups_proposal;
BEGIN
    FOREACH gp IN ARRAY proposals
        LOOP
            INSERT INTO groups_proposal(groups_id, policy_address_id, policy_address, proposal_id, proposal_data,
                                        proposal_node_data, proposal_status, executor_result, block_height, tx_hash_id,
                                        tx_hash, tx_timestamp)
            VALUES (gp.groups_id, gp.policy_address_id, gp.policy_address, gp.proposal_id, gp.proposal_data,
                    gp.proposal_node_data, gp.proposal_status, gp.executor_result, tx_height, tx_id, gp.tx_hash, timez)
            ON CONFLICT (groups_id, policy_address, proposal_id) DO UPDATE
                SET proposal_data      = gp.proposal_data,
                    proposal_node_data = coalesce(gp.proposal_node_data, groups_proposal.proposal_node_data),
                    proposal_status    = gp.proposal_status,
                    executor_result    = gp.executor_result,
                    tx_hash            = CASE
                                             WHEN tx_height < groups_proposal.block_height THEN gp.tx_hash
                                             ELSE groups_proposal.tx_hash END,
                    tx_timestamp       = CASE
                                             WHEN tx_height < groups_proposal.block_height THEN timez
                                             ELSE groups_proposal.tx_timestamp END,
                    block_height       = CASE
                                             WHEN tx_height < groups_proposal.block_height THEN tx_height
                                             ELSE groups_proposal.block_height END,
                    tx_hash_id         = CASE
                                             WHEN tx_height < groups_proposal.block_height THEN tx_id
                                             ELSE groups_proposal.tx_hash_id END;
        END LOOP;
END;
$$;

create or replace procedure insert_groups_vote(votes groups_vote[], tx_height integer, tx_id integer,
                                               timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    gv groups_vote;
BEGIN
    FOREACH gv IN ARRAY votes
        LOOP
            INSERT INTO groups_vote(proposal_id, address_id, address, is_validator, vote, metadata, weight,
                                    block_height, tx_hash_id, tx_hash, tx_timestamp)
            VALUES (gv.proposal_id,
                    gv.address_id,
                    gv.address,
                    gv.is_validator,
                    gv.vote,
                    gv.metadata,
                    gv.weight,
                    tx_height,
                    tx_id,
                    gv.tx_hash,
                    timez)
            ON CONFLICT (proposal_id, address_id) DO UPDATE
                SET vote         = gv.vote,
                    weight       = gv.weight,
                    metadata     = gv.metadata,
                    block_height = tx_height,
                    tx_hash_id   = tx_id,
                    tx_hash      = gv.tx_hash,
                    tx_timestamp = timez
            WHERE tx_height > groups_vote.block_height;
        END LOOP;
END;
$$;

create or replace procedure add_tx(tu tx_update, tx_height integer, timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    tx_id INT; -- tx id
BEGIN

    -- Insert tx record, getting id
    SELECT insert_tx_cache_returning_id((tu).tx, tx_height, timez) INTO tx_id;

    -- insert gas fee
    CALL insert_tx_gas_cache((tu).txGasFee, timez, tx_height, tx_id);

    -- insert market rate
    CALL insert_validator_market_rate((tu).valMarketRate, timez, tx_height, tx_id);

    -- insert fees
    CALL insert_tx_fees((tu).txFees, tx_height, tx_id, timez);

    -- insert msgs
    CALL insert_tx_msgs((tu).txMsgs, tx_height, tx_id, timez);

    -- insert single msg record
    CALL insert_tx_single_message_cache((tu).singlemsg, timez, tx_height);

    -- insert address join
    CALL insert_tx_address_join((tu).addressJoin, tx_height, tx_id);

    -- insert marker join
    CALL insert_tx_marker_join((tu).markerjoin, tx_height, tx_id);

    -- insert scope join
    CALL insert_tx_nft_join((tu).nftJoin, tx_height, tx_id);

    -- insert ibc join
    CALL insert_tx_ibc((tu).ibcJoin, tx_height, tx_id);

    -- insert proposal
    CALL insert_gov_proposal((tu).proposals, tx_height, tx_id, timez);

    -- insert monitor
    CALL insert_proposal_monitor((tu).proposalmonitors);

    -- insert deposit
    CALL insert_gov_deposit((tu).deposits, tx_height, tx_id, timez);

    -- insert vote
    CALL insert_gov_vote((tu).votes, tx_height, tx_id, timez);

    -- insert ledger
    CALL insert_ibc_ledger((tu).ibcLedgers, tx_id);

    -- insert ledger ack
    CALL insert_ibc_ledger_ack((tu).ibcLedgerAcks, tx_id);

    -- insert sm join
    CALL insert_tx_sm_code((tu).smCodes, tx_height, tx_id);

    CALL insert_tx_sm_contract((tu).smContracts, tx_height, tx_id);

    -- insert sig tx
    CALL insert_signature_tx((tu).sigs, tx_height, tx_id);

    -- insert feepayer
    CALL insert_tx_feepayer((tu).feepayers, tx_height, tx_id, timez);

    -- insert groups
    CALL insert_groups((tu).groupsList, tx_height, tx_id, timez);

    -- insert tx_groups
    CALL insert_tx_groups((tu).groupsJoin, tx_height, tx_id);

    -- insert groups_policy
    CALL insert_groups_policy((tu).groupsPolicies, tx_height, tx_id, timez);

    -- insert tx_groups_policy
    CALL insert_tx_groups_policy((tu).groupsPolicyJoinAlt, tx_height, tx_id, null);

    -- insert groups_proposal
    CALL insert_groups_proposal((tu).groupsProposals, tx_height, tx_id, timez);

    -- insert groups_vote
    CALL insert_groups_vote((tu).groupsVotes, tx_height, tx_id, timez);

    RAISE INFO 'UPDATED tx';
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error saving tx %', (tu).tx.hash;
END;
$$;

create or replace procedure add_tx_debug(tu tx_update, tx_height integer, timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    tx_id INT; -- tx id
BEGIN

    -- Insert tx record, getting id
    SELECT insert_tx_cache_returning_id((tu).tx, tx_height, timez) INTO tx_id;

    -- insert gas fee
    CALL insert_tx_gas_cache((tu).txGasFee, timez, tx_height, tx_id);

    -- insert market rate
    CALL insert_validator_market_rate((tu).valMarketRate, timez, tx_height, tx_id);

    -- insert fees
    CALL insert_tx_fees((tu).txFees, tx_height, tx_id, timez);

    -- insert msgs
    CALL insert_tx_msgs((tu).txMsgs, tx_height, tx_id, timez);

    -- insert single msg record
    CALL insert_tx_single_message_cache((tu).singlemsg, timez, tx_height);

    -- insert address join
    CALL insert_tx_address_join((tu).addressJoin, tx_height, tx_id);

    -- insert marker join
    CALL insert_tx_marker_join((tu).markerjoin, tx_height, tx_id);

    -- insert scope join
    CALL insert_tx_nft_join((tu).nftJoin, tx_height, tx_id);

    -- insert ibc join
    CALL insert_tx_ibc((tu).ibcJoin, tx_height, tx_id);

    -- insert proposal
    CALL insert_gov_proposal((tu).proposals, tx_height, tx_id, timez);

    -- insert monitor
    CALL insert_proposal_monitor((tu).proposalmonitors);

    -- insert deposit
    CALL insert_gov_deposit((tu).deposits, tx_height, tx_id, timez);

    -- insert vote
    CALL insert_gov_vote((tu).votes, tx_height, tx_id, timez);

    -- insert ledger
    CALL insert_ibc_ledger((tu).ibcLedgers, tx_id);

    -- insert ledger ack
    CALL insert_ibc_ledger_ack((tu).ibcLedgerAcks, tx_id);

    -- insert sm join
    CALL insert_tx_sm_code((tu).smCodes, tx_height, tx_id);

    CALL insert_tx_sm_contract((tu).smContracts, tx_height, tx_id);

    -- insert sig tx
    CALL insert_signature_tx((tu).sigs, tx_height, tx_id);

    -- insert feepayer
    CALL insert_tx_feepayer((tu).feepayers, tx_height, tx_id, timez);

    -- insert groups
    CALL insert_groups((tu).groupsList, tx_height, tx_id, timez);

    -- insert tx_groups
    CALL insert_tx_groups((tu).groupsJoin, tx_height, tx_id);

    -- insert groups_policy
    CALL insert_groups_policy((tu).groupsPolicies, tx_height, tx_id, timez);

    -- insert tx_groups_policy
    CALL insert_tx_groups_policy((tu).groupsPolicyJoinAlt, tx_height, tx_id, null);

    -- insert groups_proposal
    CALL insert_groups_proposal((tu).groupsProposals, tx_height, tx_id, timez);

    -- insert groups_vote
    CALL insert_groups_vote((tu).groupsVotes, tx_height, tx_id, timez);

    RAISE INFO 'UPDATED tx';
END;
$$;

CREATE OR REPLACE PROCEDURE add_block(bd block_update)
    LANGUAGE plpgsql
AS
$$
DECLARE
    tx_height INT; --block height
    timez     TIMESTAMP; -- block timestamp
    tu        tx_update;
BEGIN
    SELECT (bd).blocks.height INTO tx_height;
    SELECT (bd).blocks.block_timestamp INTO timez;
    -- insert block
    INSERT INTO block_cache(height, tx_count, block_timestamp, block, last_hit, hit_count)
    VALUES (tx_height,
            (bd).blocks.tx_count,
            timez,
            (bd).blocks.block,
            (bd).blocks.last_hit,
            (bd).blocks.hit_count)
    ON CONFLICT (height) DO NOTHING;
    -- insert block tx count
    INSERT INTO block_tx_count_cache(block_height, block_timestamp, tx_count)
    VALUES (tx_height, timez, (bd).blocks.tx_count)
    ON CONFLICT (block_height) DO NOTHING;
    -- insert block proposer fee
    INSERT INTO block_proposer(block_height, block_timestamp, proposer_operator_address, block_latency)
    VALUES (tx_height,
            timez,
            (bd).proposer.proposer_operator_address,
            (bd).proposer.block_latency)
    ON CONFLICT (block_height) DO NOTHING;
    -- insert validator cache
    INSERT INTO validators_cache(height, validators, last_hit, hit_count)
    VALUES (tx_height, (bd).validatorCache.validators, (bd).validatorCache.last_hit, (bd).validatorCache.hit_count)
    ON CONFLICT (height) DO NOTHING;
    -- for each tx
    FOREACH tu IN ARRAY (bd).txs
        LOOP
            CALL add_tx(tu, tx_height, timez);
        END LOOP;
    RAISE INFO 'UPDATED block';
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error saving block %', (bd).blocks.height;
END;
$$;

create or replace procedure add_block_debug(bd block_update)
    language plpgsql
as
$$
DECLARE
    tx_height INT; --block height
    timez     TIMESTAMP; -- block timestamp
    tu        tx_update;
BEGIN
    SELECT (bd).blocks.height INTO tx_height;
    SELECT (bd).blocks.block_timestamp INTO timez;
    -- insert block
    INSERT INTO block_cache(height, tx_count, block_timestamp, block, last_hit, hit_count)
    VALUES (tx_height,
            (bd).blocks.tx_count,
            timez,
            (bd).blocks.block,
            (bd).blocks.last_hit,
            (bd).blocks.hit_count)
    ON CONFLICT (height) DO NOTHING;
    -- insert block tx count
    INSERT INTO block_tx_count_cache(block_height, block_timestamp, tx_count)
    VALUES (tx_height, timez, (bd).blocks.tx_count)
    ON CONFLICT (block_height) DO NOTHING;
    -- insert block proposer fee
    INSERT INTO block_proposer(block_height, block_timestamp, proposer_operator_address, block_latency)
    VALUES (tx_height,
            timez,
            (bd).proposer.proposer_operator_address,
            (bd).proposer.block_latency)
    ON CONFLICT (block_height) DO NOTHING;
    -- insert validator cache
    INSERT INTO validators_cache(height, validators, last_hit, hit_count)
    VALUES (tx_height, (bd).validatorCache.validators, (bd).validatorCache.last_hit, (bd).validatorCache.hit_count)
    ON CONFLICT (height) DO NOTHING;
    -- for each tx
    FOREACH tu IN ARRAY (bd).txs
        LOOP
            CALL add_tx_debug(tu, tx_height, timez);
        END LOOP;
    RAISE INFO 'UPDATED block';
END;
$$;

create or replace function get_tx_associated_values(tx_hash text, tx_height integer)
    returns TABLE
            (
                value character varying,
                type  character varying
            )
    language plpgsql
as
$$
BEGIN
    RETURN QUERY
-- BASE tx info
        with tx AS (select id
                    from tx_cache
                    where hash = tx_hash
                      and height = tx_height)
-- denom match
        select tmj.denom as value,
               'DENOM'   as type
        from tx_marker_join tmj,
             tx
        where tmj.tx_hash_id = tx.id
        union all
-- nft uuids
        select tnj.metadata_uuid as value,
               tnj.metadata_type as type
        from tx_nft_join tnj,
             tx
        where tnj.tx_hash_id = tx.id
        union all
-- nft scope addr
        select ns.address        as value,
               tnj.metadata_type as type
        from tx_nft_join tnj
                 join nft_scope ns ON tnj.metadata_type = 'SCOPE' AND tnj.metadata_id = ns.id,
             tx
        where tnj.tx_hash_id = tx.id
        union all
-- nft scope spec addr
        select nss.address       as value,
               tnj.metadata_type as type
        from tx_nft_join tnj
                 join nft_scope_spec nss ON tnj.metadata_type = 'SCOPE_SPEC' AND tnj.metadata_id = nss.id,
             tx
        where tnj.tx_hash_id = tx.id
        union all
-- nft contract spec addr
        select ncs.address       as value,
               tnj.metadata_type as type
        from tx_nft_join tnj
                 join nft_contract_spec ncs ON tnj.metadata_type = 'CONTRACT_SPEC' AND tnj.metadata_id = ncs.id,
             tx
        where tnj.tx_hash_id = tx.id
        union all
-- SC CODE
        select tmc.sm_code::text as value,
               'CODE'            as type
        from tx_sm_code tmc,
             tx
        where tmc.tx_hash_id = tx.id
        union all
-- SC Contract
        select tmct.sm_contract_address as value,
               'CONTRACT'               as type
        from tx_sm_contract tmct,
             tx
        where tmct.tx_hash_id = tx.id
        union all
-- address match, both account and operator (validator)
        select taj.address      as value,
               taj.address_type as type
        from tx_address_join taj
                 left join account on taj.address = account.account_address,
             tx
        where taj.tx_hash_id = tx.id
          and (account.is_contract is null or account.is_contract = false)
          and (account.is_group_policy is null or account.is_group_policy = false)
        union all
-- proposal match from proposals
        select gp.proposal_id::text as value,
               'PROPOSAL'           as type
        from gov_proposal gp,
             tx
        where gp.tx_hash_id = tx.id
        union all
-- proposal match from deposits
        select gd.proposal_id::text as value,
               'PROPOSAL'           as type
        from gov_deposit gd,
             tx
        where gd.tx_hash_id = tx.id
        union all
-- proposal match from votes
        select gv.proposal_id::text as value,
               'PROPOSAL'           as type
        from gov_vote gv,
             tx
        where gv.tx_hash_id = tx.id
        union all
-- ibc client
        select ti.client    as value,
               'IBC_CLIENT' as type
        from tx_ibc ti,
             tx
        where ti.tx_hash_id = tx.id
        union all
-- ibc channel
        select (ic.src_port || ', ' || ic.src_channel) as value,
               'IBC_CHANNEL'                           as type
        from tx_ibc ti
                 join ibc_channel ic on ti.channel_id = ic.id,
             tx
        where ti.tx_hash_id = tx.id
        union all
-- groups proposal match from groups_proposal
        select gp.proposal_id::text as value,
               'GROUP_PROPOSAL'     as type
        from groups_proposal gp,
             tx
        where gp.tx_hash_id = tx.id
        union all
-- groups proposal match from groups_vote
        select gv.proposal_id::text as value,
               'GROUP_PROPOSAL'     as type
        from groups_vote gv,
             tx
        where gv.tx_hash_id = tx.id
        union all
-- group
        select tg.groups_id::text as value,
               'GROUP'            as type
        from tx_groups tg,
             tx
        where tg.tx_hash_id = tx.id
        union all
-- group policy
        select tgp.policy_address as value,
               'GROUP_POLICY'     as type
        from tx_groups_policy tgp,
             tx
        where tgp.tx_hash_id = tx.id;

END
$$;

-- insert blocks to be rerun

with base as (select distinct tc.height
              from tx_cache tc
                       join tx_msg_type_query tmtq on tc.id = tmtq.tx_hash_id
                       join tx_message_type tmt on tmtq.type_id = tmt.id
              where tmt.module = 'group')
insert
into block_tx_retry (height)
select height
from base
on conflict (height) do update
    set retried = false
where block_tx_retry.retried = true
  and block_tx_retry.success = false;
