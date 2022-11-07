SELECT 'Add msg subtype stuff' AS comment;

create table if not exists tx_msg_type_subtype
(
    id                SERIAL PRIMARY KEY,
    tx_msg_id         integer     not null,
    primary_type_id   integer     not null,
    secondary_type_id integer     null default null,
    tx_timestamp      timestamp   not null,
    tx_hash_id        INTEGER     not null,
    block_height      INTEGER     not null,
    tx_hash           VARCHAR(64) not null
);

CREATE UNIQUE INDEX IF NOT EXISTS tx_msg_type_subtype_unique_idx ON tx_msg_type_subtype (tx_msg_id, primary_type_id, COALESCE(secondary_type_id, -1));
CREATE INDEX IF NOT EXISTS tx_msg_type_subtype_primary_type_idx ON tx_msg_type_subtype (primary_type_id);
CREATE INDEX IF NOT EXISTS tx_msg_type_subtype_secondary_type_idx ON tx_msg_type_subtype (secondary_type_id);
CREATE INDEX IF NOT EXISTS tx_msg_type_subtype_tx_timestamp_idx ON tx_msg_type_subtype (tx_timestamp);
CREATE INDEX IF NOT EXISTS tx_msg_type_subtype_tx_hash_id_idx ON tx_msg_type_subtype (tx_hash_id);
CREATE INDEX IF NOT EXISTS tx_msg_type_subtype_block_height_idx ON tx_msg_type_subtype (block_height);

SELECT 'Add subtype query table and insert' AS comment;
create table if not exists tx_msg_type_query
(
    id         serial primary key,
    tx_hash_id integer not null,
    tx_msg_id  integer not null,
    type_id    integer not null
);

create unique index tx_msg_type_query_unique_idx on tx_msg_type_query (tx_hash_id, tx_msg_id, type_id);
create index tx_msg_type_query_tx_hash_id_type_id_idx on tx_msg_type_query (tx_hash_id, type_id);
create index tx_msg_type_query_tx_msg_id_type_id_idx on tx_msg_type_query (tx_msg_id, type_id);
create index tx_msg_type_query_type_id_idx on tx_msg_type_query (type_id);

CREATE OR REPLACE FUNCTION insert_tx_msg_type_query()
    RETURNS TRIGGER AS
$$
BEGIN
    insert into tx_msg_type_query (tx_hash_id, tx_msg_id, type_id)
    select *
    from (select new.tx_hash_id, new.tx_msg_id, unnest(ARRAY [new.primary_type_id, new.secondary_type_id]) type_id) q
    where type_id is not null
    on conflict (tx_hash_id, tx_msg_id, type_id) DO nothing;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_tx_msg_type_subtype_insert_to_query
    AFTER INSERT
    ON tx_msg_type_subtype
    FOR EACH ROW
EXECUTE PROCEDURE insert_tx_msg_type_query();

insert into tx_msg_type_subtype (tx_msg_id, primary_type_id, tx_timestamp, tx_hash_id, block_height, tx_hash)
select id, tx_message_type_id, tx_timestamp, tx_hash_id, block_height, tx_hash
from tx_message;

SELECT 'Redo materialized view' AS comment;
drop materialized view tx_type_data_hourly;

create materialized view tx_type_data_hourly as
SELECT date_trunc('hour'::text, tmts.tx_timestamp)         AS hourly,
       tf.address                                          AS feepayer,
       (tmt.module::text || ' / '::text) || tmt.type::text AS tx_type,
       count(tmt.id)                                       AS tx_type_count
FROM tx_msg_type_subtype tmts
         JOIN tx_message_type tmt ON tmts.primary_type_id = tmt.id
         JOIN tx_feepayer tf ON tmts.tx_hash_id = tf.tx_hash_id
GROUP BY (date_trunc('hour'::text, tmts.tx_timestamp)), tf.address,
         ((tmt.module::text || ' / '::text) || tmt.type::text)
ORDER BY (date_trunc('hour'::text, tmts.tx_timestamp)), tf.address,
         ((tmt.module::text || ' / '::text) || tmt.type::text)
with data;

SELECT 'Update ingest procedures' AS comment;
DROP TYPE IF EXISTS tx_msg CASCADE;
DROP TYPE IF EXISTS tx_update CASCADE;
DROP TYPE IF EXISTS block_update CASCADE;

create type tx_msg as
(
    txmsg      tx_message,
    txmsgTypes tx_msg_type_subtype[],
    txevents   tx_event[]
);

CREATE TYPE tx_update AS
(
    tx               tx_cache,
    txGasFee         tx_gas_cache,
    txFees           tx_fee[],
    txMsgs           tx_msg[],
    singleMsg        tx_single_message_cache[],
    addressJoin      tx_address_join[],
    markerJoin       tx_marker_join[],
    nftJoin          tx_nft_join[],
    ibcJoin          tx_ibc[],
    proposals        gov_proposal[],
    proposalMonitors proposal_monitor[],
    deposits         gov_deposit[],
    votes            gov_vote[],
    ibcLedgers       ibc_ledger[],
    ibcLedgerAcks    ibc_ledger_ack[],
    smCodes          tx_sm_code[],
    smContracts      tx_sm_contract[],
    sigs             signature_tx[],
    feepayers        tx_feepayer[],
    valMarketRate    validator_market_rate
);

CREATE TYPE block_update AS
(
    blocks         block_cache,
    proposer       block_proposer,
    validatorCache validators_cache,
    txs            tx_update[]
);

create or replace procedure insert_tx_msg_type_subtype(txmsgTypes tx_msg_type_subtype[], msgId integer,
                                                       tx_height integer, tx_id integer,
                                                       timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    msgType tx_msg_type_subtype;
BEGIN
    FOREACH msgType IN ARRAY txmsgTypes
        LOOP
            INSERT INTO tx_msg_type_subtype(tx_timestamp, tx_hash_id, tx_hash, block_height, tx_msg_id, primary_type_id,
                                            secondary_type_id)
            VALUES (timez, tx_id, msgType.tx_hash, tx_height, msgId, msgType.primary_type_id, msgType.secondary_type_id)
            ON CONFLICT (tx_msg_id, primary_type_id, COALESCE(secondary_type_id, -1)) DO NOTHING;
        END LOOP;
END;
$$;

create or replace procedure insert_tx_msgs(txmsgs tx_msg[], tx_height integer, tx_id integer,
                                           timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    msg   tx_msg;
    msgId integer;
BEGIN
    FOREACH msg IN ARRAY txMsgs
        LOOP
            WITH m AS (
                INSERT INTO tx_message (tx_hash, block_height, tx_hash_id, tx_message_hash,
                                        msg_idx, "tx_message", tx_timestamp)
                    VALUES ((msg).txMsg.tx_hash,
                            tx_height,
                            tx_id,
                            (msg).txMsg.tx_message_hash,
                            (msg).txMsg.msg_idx,
                            (msg).txMsg.tx_message,
                            timez)
                    ON CONFLICT (tx_hash_id, tx_message_hash, msg_idx) DO NOTHING
                    RETURNING id)
            SELECT *
            FROM m
            UNION
            SELECT id
            FROM tx_message
            WHERE tx_hash_id = tx_id
              AND tx_message_hash = (msg).txMsg.tx_message_hash
              AND msg_idx = (msg).txMsg.msg_idx
            INTO msgId;
            -- Insert tx_msg_type_subtype
            CALL insert_tx_msg_type_subtype((msg).txmsgTypes, msgId, tx_height, tx_id, timez);
            -- insert events
            CALL insert_tx_msg_event((msg).txevents, msgId, tx_height, tx_id);
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

alter table tx_message
    drop column if exists tx_message_type_id;

drop procedure if exists insert_tx_msg_type_subtype(tx_msg_type_subtype[], integer);

INSERT INTO cache_update(cache_key, description, cache_value, last_updated)
VALUES ('authz_processing', 'Boolean to continue processing authz messages for subtypes', 'true', now());
