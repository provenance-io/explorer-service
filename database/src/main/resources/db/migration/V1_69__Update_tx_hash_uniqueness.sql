SELECT 'Update tx_cache' AS comment;

DROP INDEX IF EXISTS tx_cache_hash_idx;
DROP INDEX IF EXISTS tx_cache_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tx_cache_unique_idx ON tx_cache (height, hash);
DROP INDEX IF EXISTS tx_message_hash_id_msg_hash_msg_idx;

create or replace function insert_tx_cache_returning_id(tx tx_cache, tx_height integer, timez timestamp without time zone, OUT txid integer) returns integer
    language plpgsql as
$$
BEGIN
    WITH t AS (
        INSERT INTO tx_cache (hash, height, gas_wanted, gas_used, tx_timestamp, error_code, codespace, tx_v2)
            VALUES (tx.hash,
                    tx_height,
                    tx.gas_wanted,
                    tx.gas_used,
                    timez,
                    tx.error_code,
                    tx.codespace,
                    tx.tx_v2)
            ON CONFLICT (height, hash) DO NOTHING
            RETURNING id
    )
    SELECT * FROM t
    UNION SELECT id FROM tx_cache WHERE hash = tx.hash and height = tx_height
    INTO txId;
END
$$;


SELECT 'Update gov_deposit' AS comment;

DROP INDEX IF EXISTS gov_deposit_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS gov_deposit_unique_idx ON gov_deposit (proposal_id, tx_hash_id, deposit_type, address_id, denom);

create or replace procedure insert_gov_deposit(deposits gov_deposit[], tx_height integer, tx_id integer, timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    gd gov_deposit;
BEGIN
    FOREACH gd IN ARRAY deposits
        LOOP
            INSERT INTO gov_deposit(proposal_id, address_id, address, block_height, tx_hash, tx_timestamp, is_validator,
                                    deposit_type, amount, denom, tx_hash_id)
            VALUES (gd.proposal_id,
                    gd.address_id,
                    gd.address,
                    tx_height,
                    gd.tx_hash,
                    timez,
                    gd.is_validator,
                    gd.deposit_type,
                    gd.amount,
                    gd.denom,
                    tx_id)
            ON CONFLICT (proposal_id, tx_hash_id, deposit_type, address_id, denom) DO NOTHING;
        END LOOP;
END;
$$;

SELECT 'Update tx_address_join' AS comment;

DROP INDEX IF EXISTS tx_address_join_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tx_address_join_unique_idx ON tx_address_join (tx_hash_id, address);

create or replace procedure insert_tx_address_join(addressjoin tx_address_join[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    aj tx_address_join;
BEGIN
    FOREACH aj IN ARRAY addressJoin
        LOOP
            INSERT INTO tx_address_join(block_height, tx_hash, address, tx_hash_id, address_id, address_type)
            VALUES (tx_height, aj.tx_hash, aj.address, tx_id, aj.address_id, aj.address_type)
            ON CONFLICT (tx_hash_id, address) DO NOTHING;
        END LOOP;
END;
$$;

SELECT 'Update tx_feepayer' AS comment;

DROP INDEX IF EXISTS tx_feepayer_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tx_feepayer_unique_idx ON tx_feepayer (tx_hash_id, payer_type, address_id);

DROP INDEX IF EXISTS tx_feepayer_unique_no_ids_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tx_feepayer_unique_no_ids_idx ON tx_feepayer (tx_hash_id, payer_type, address_id);

create or replace procedure insert_tx_feepayer(feepayers tx_feepayer[], tx_height integer, tx_id integer)
                                    language plpgsql as
$$
DECLARE
    tfp tx_feepayer;
BEGIN
    FOREACH tfp IN ARRAY feepayers
        LOOP
            INSERT INTO tx_feepayer(block_height, tx_hash_id, tx_hash, payer_type, address_id, address)
            VALUES (tx_height, tx_id, tfp.tx_hash, tfp.payer_type, tfp.address_id, tfp.address)
            ON CONFLICT (tx_hash_id, payer_type, address_id) DO NOTHING;
        END LOOP;
END;
$$;


SELECT 'Update tx_gas_cache' AS comment;

ALTER TABLE tx_gas_cache
    ADD COLUMN IF NOT EXISTS height INTEGER,
    DROP CONSTRAINT IF EXISTS tx_gas_cache_hash_key;

UPDATE tx_gas_cache tgc
SET height = q.height
FROM (SELECT height, hash FROM tx_cache) q
WHERE tgc.hash = q.hash;

DROP INDEX IF EXISTS tx_gas_cache_hash_key;
DROP INDEX IF EXISTS tx_gas_cache_unique_key;
CREATE UNIQUE INDEX IF NOT EXISTS tx_gas_cache_unique_key ON tx_gas_cache (height, hash);

create or replace procedure insert_tx_gas_cache(txgasfee tx_gas_cache, timez timestamp without time zone, tx_height integer)
    language plpgsql as
$$
BEGIN
    INSERT INTO tx_gas_cache(height, hash, gas_wanted, gas_used, tx_timestamp, fee_amount)
    VALUES (tx_height, txGasFee.hash, txGasFee.gas_wanted, txGasFee.gas_used, timez, txGasFee.fee_amount)
    ON CONFLICT (height, hash) DO NOTHING;
END;
$$;

drop procedure if exists insert_tx_gas_cache(tx_gas_cache, timestamp);


SELECT 'Update tx_marker_join' AS comment;

DROP INDEX IF EXISTS tx_marker_join_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tx_marker_join_unique_idx ON tx_marker_join (tx_hash_id, marker_id);

create or replace procedure insert_tx_marker_join(markerjoin tx_marker_join[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    mj tx_marker_join;
BEGIN
    FOREACH mj IN ARRAY markerJoin
        LOOP
            INSERT INTO tx_marker_join(block_height, tx_hash, denom, tx_hash_id, marker_id)
            VALUES (tx_height, mj.tx_hash, mj.denom, tx_id, mj.marker_id)
            ON CONFLICT (tx_hash_id, marker_id) DO NOTHING;
        END LOOP;
END;
$$;


SELECT 'Update tx_nft_join' AS comment;

DROP INDEX IF EXISTS tx_nft_join_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tx_nft_join_unique_idx ON tx_nft_join (tx_hash_id, metadata_type, metadata_id);

create or replace procedure insert_tx_nft_join(nftjoin tx_nft_join[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    nj tx_nft_join;
BEGIN
    FOREACH nj IN ARRAY nftJoin
        LOOP
            INSERT INTO tx_nft_join(block_height, tx_hash, tx_hash_id, metadata_id, metadata_type, metadata_uuid)
            VALUES (tx_height, nj.tx_hash, tx_id, nj.metadata_id, nj.metadata_type, nj.metadata_uuid)
            ON CONFLICT (tx_hash_id, metadata_type, metadata_id) DO NOTHING;
        END LOOP;
END;
$$;


SELECT 'Update tx_single_message_cache' AS comment;

ALTER TABLE tx_single_message_cache
    ADD COLUMN IF NOT EXISTS height INTEGER,
    DROP CONSTRAINT IF EXISTS tx_single_message_cache_tx_hash_key;

UPDATE tx_single_message_cache tsmc
SET height = q.height
FROM (SELECT height, hash FROM tx_cache) q
WHERE tsmc.tx_hash = q.hash;


DROP INDEX IF EXISTS tx_single_message_cache_tx_hash_key;
CREATE UNIQUE INDEX IF NOT EXISTS tx_single_message_cache_tx_hash_key ON tx_single_message_cache (height, tx_hash);

create or replace procedure insert_tx_single_message_cache(singlemsg tx_single_message_cache[], timez timestamp without time zone, tx_height integer)
    language plpgsql as
$$
DECLARE
    sm tx_single_message_cache;
BEGIN
    FOREACH sm IN ARRAY singleMsg
        LOOP
            INSERT INTO tx_single_message_cache(height, tx_hash, tx_timestamp, gas_used, tx_message_type)
            VALUES (tx_height, sm.tx_hash, timez, sm.gas_used, sm.tx_message_type)
            ON CONFLICT (height, tx_hash) DO NOTHING;
        END LOOP;
END;
$$;

drop procedure if exists insert_tx_single_message_cache(tx_single_message_cache[], timestamp);


SELECT 'Update tx_sm_code' AS comment;

DROP INDEX IF EXISTS tx_sm_code_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tx_sm_code_unique_idx ON tx_sm_code (tx_hash_id, sm_code);

create or replace procedure insert_tx_sm_code(smcodes tx_sm_code[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    codej tx_sm_code;
BEGIN
    FOREACH codej IN ARRAY smCodes
        LOOP
            INSERT INTO tx_sm_code(block_height, tx_hash, tx_hash_id, sm_code)
            VALUES (tx_height, codej.tx_hash, tx_id, codej.sm_code)
            ON CONFLICT (tx_hash_id, sm_code) DO NOTHING;
        END LOOP;
END;
$$;


SELECT 'Update tx_sm_contract' AS comment;

DROP INDEX IF EXISTS tx_sm_contract_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tx_sm_contract_unique_idx ON tx_sm_contract (tx_hash_id, sm_contract_id);

create or replace procedure insert_tx_sm_contract(smcontracts tx_sm_contract[], tx_height integer, tx_id integer)
    language plpgsql as
$$
DECLARE
    contractj tx_sm_contract;
BEGIN
    FOREACH contractj IN ARRAY smContracts
        LOOP
            INSERT INTO tx_sm_contract(block_height, tx_hash, tx_hash_id, sm_contract_id, sm_contract_address)
            VALUES (tx_height, contractj.tx_hash, tx_id, contractj.sm_contract_id, contractj.sm_contract_address)
            ON CONFLICT (tx_hash_id, sm_contract_id) DO NOTHING;
        END LOOP;
END;
$$;

SELECT 'Update validator_market_rate' AS comment;

DROP INDEX IF EXISTS validator_market_rate_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS validator_market_rate_unique_idx ON validator_market_rate (tx_hash_id);

create or replace procedure insert_validator_market_rate(valmarketrate validator_market_rate, timez timestamp without time zone, tx_height integer, tx_id integer)
    language plpgsql as
$$
BEGIN
    INSERT INTO validator_market_rate(block_height, block_timestamp, proposer_address, tx_hash_id, tx_hash, market_rate, success)
    VALUES (tx_height, timez, valMarketRate.proposer_address, tx_id, valMarketRate.tx_hash, valMarketRate.market_rate, valMarketRate.success)
    ON CONFLICT (tx_hash_id) DO NOTHING;
END;
$$;

SELECT 'Update add_tx()' AS comment;

create or replace procedure add_tx(tu tx_update, tx_height integer, timez timestamp without time zone)
    language plpgsql as
$$
DECLARE
    tx_id INT; -- tx id
BEGIN

    -- Insert tx record, getting id
    SELECT insert_tx_cache_returning_id((tu).tx, tx_height, timez) INTO tx_id;

    -- insert gas fee
    CALL insert_tx_gas_cache((tu).txGasFee, timez, tx_height);

    -- insert market rate
    CALL insert_validator_market_rate((tu).valMarketRate, timez, tx_height, tx_id);

    -- insert fees
    CALL insert_tx_fees((tu).txFees, tx_height, tx_id);

    -- insert msgs
    CALL insert_tx_msgs((tu).txMsgs, tx_height, tx_id);

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
    CALL insert_tx_gas_cache((tu).txGasFee, timez, tx_height);

    -- insert market rate
    CALL insert_validator_market_rate((tu).valMarketRate, timez, tx_height, tx_id);

    -- insert fees
    CALL insert_tx_fees((tu).txFees, tx_height, tx_id);

    -- insert msgs
    CALL insert_tx_msgs((tu).txMsgs, tx_height, tx_id);

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

    -- insert sig join
    CALL insert_signature_join((tu).sigs);

    -- insert feepayer
    CALL insert_tx_feepayer((tu).feepayers, tx_height, tx_id);

    RAISE INFO 'UPDATED tx';
END;
$$;
