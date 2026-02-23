SELECT 'Update add_tx to show detailed error messages' AS comment;

CREATE OR REPLACE PROCEDURE add_tx(tu tx_update, tx_height integer, timez timestamp without time zone)
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
        RAISE EXCEPTION 'Error saving tx %. Error Code: %, Message: %.', (tu).tx.hash, SQLSTATE, SQLERRM;
END;
$$;
