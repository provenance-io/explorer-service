SELECT 'Updating ingest for IBC' AS comment;

drop procedure if exists insert_ibc_ledger(ibc_ledger[], integer, integer, timestamp);


create or replace procedure insert_ibc_ledger(ibcLedgers ibc_ledger[], tx_id integer)
    language plpgsql as
$$
DECLARE
    ibcl ibc_ledger;
BEGIN
    FOREACH ibcl IN ARRAY ibcLedgers
        LOOP
            INSERT INTO ibc_ledger(dst_chain_name, channel_id, denom, denom_trace, balance_in, balance_out,
                                   from_address, to_address, pass_through_address_id, pass_through_address,
                                   logs, block_height, tx_hash_id, tx_hash, tx_timestamp, acknowledged,
                                   ack_success, sequence, unique_hash)
            VALUES (ibcl.dst_chain_name,
                    ibcl.channel_id,
                    ibcl.denom,
                    ibcl.denom_trace,
                    ibcl.balance_in,
                    ibcl.balance_out,
                    ibcl.from_address,
                    ibcl.to_address,
                    ibcl.pass_through_address_id,
                    ibcl.pass_through_address,
                    ibcl.logs,
                    ibcl.block_height,
                    CASE WHEN ibcl.tx_hash_id < 0 THEN tx_id ELSE ibcl.tx_hash_id END,
                    ibcl.tx_hash,
                    ibcl.tx_timestamp,
                    ibcl.acknowledged,
                    ibcl.ack_success,
                    ibcl.sequence,
                    ibcl.unique_hash)
            ON CONFLICT (unique_hash)
                DO UPDATE
                SET acknowledged = true,
                    ack_success  = ibcl.ack_success;
        END LOOP;
END;
$$;

create or replace procedure insert_ibc_ledger_ack(ibcLedgerAcks ibc_ledger_ack[], tx_id integer)
    language plpgsql as
$$
DECLARE
    ibcla ibc_ledger_ack;
BEGIN
    FOREACH ibcla IN ARRAY ibcLedgerAcks
        LOOP
            INSERT INTO ibc_ledger_ack(ibc_ledger_id, ack_type, logs, block_height, tx_hash_id, tx_hash, tx_timestamp,
                                       changes_effected)
            VALUES (ibcla.ibc_ledger_id,
                    ibcla.ack_type,
                    ibcla.logs,
                    ibcla.block_height,
                    tx_id,
                    ibcla.tx_hash,
                    ibcla.tx_timestamp,
                    ibcla.changes_effected)
            ON CONFLICT (tx_hash_id, ibc_ledger_id, ack_type) DO NOTHING;
        END LOOP;
END;
$$;


create or replace procedure insert_tx_ibc(ibcJoin tx_ibc[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    ij tx_ibc;
BEGIN
    FOREACH ij IN ARRAY ibcJoin
        LOOP
            INSERT INTO tx_ibc(block_height, tx_hash, tx_hash_id, client, channel_id)
            VALUES (tx_height, ij.tx_hash, tx_id, ij.client, ij.channel_id)
            ON CONFLICT (tx_hash_id, client, COALESCE(channel_id,-1)) DO NOTHING;
        END LOOP;
END;
$$;


DROP TYPE IF EXISTS tx_update CASCADE;
DROP TYPE IF EXISTS block_update CASCADE;

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
    sigs             signature_join[],
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

create or replace procedure add_tx(tu tx_update, tx_height integer, timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    tx_id INT; -- tx id
BEGIN

    -- Insert tx record, getting id
    SELECT insert_tx_cache_returning_id((tu).tx, tx_height, timez) INTO tx_id;

    -- insert gas fee
    CALL insert_tx_gas_cache((tu).txGasFee, timez);

    -- insert market rate
    CALL insert_validator_market_rate((tu).valMarketRate, timez, tx_height, tx_id);

    -- insert fees
    CALL insert_tx_fees((tu).txFees, tx_height, tx_id);

    -- insert msgs
    CALL insert_tx_msgs((tu).txMsgs, tx_height, tx_id);

    -- insert single msg record
    CALL insert_tx_single_message_cache((tu).singlemsg, timez);

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

    -- insert sig join
    CALL insert_signature_join((tu).sigs);

    -- insert feepayer
    CALL insert_tx_feepayer((tu).feepayers, tx_height, tx_id);

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
    tx_id     INT; -- tx id
BEGIN

    -- Insert tx record, getting id
    SELECT insert_tx_cache_returning_id((tu).tx, tx_height, timez) INTO tx_id;

    -- insert gas fee
    CALL insert_tx_gas_cache((tu).txGasFee, timez);

    -- insert market rate
    CALL insert_validator_market_rate((tu).valMarketRate, timez, tx_height, tx_id);

    -- insert fees
    CALL insert_tx_fees((tu).txFees, tx_height, tx_id);

    -- insert msgs
    CALL insert_tx_msgs((tu).txMsgs, tx_height, tx_id);

    -- insert single msg record
    CALL insert_tx_single_message_cache((tu).singlemsg, timez);

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

    -- insert sig join
    CALL insert_signature_join((tu).sigs);

    -- insert feepayer
    CALL insert_tx_feepayer((tu).feepayers, tx_height, tx_id);

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
