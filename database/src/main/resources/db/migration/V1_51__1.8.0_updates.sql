SELECT 'Adding tx_fee.msg_type' AS comment;
ALTER TABLE tx_fee
    ADD COLUMN IF NOT EXISTS msg_type VARCHAR(256);

DROP INDEX IF EXISTS tx_fee_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS tx_fee_unique_idx ON tx_fee (tx_hash_id, fee_type, COALESCE(msg_type,''), marker_id);

SELECT 'Updating tx insert procedure' AS comment;
CREATE OR REPLACE PROCEDURE add_tx(tu tx_update, tx_height INT, timez TIMESTAMP)
    LANGUAGE plpgsql
AS
$$
DECLARE
    tx_id     INT; -- tx id
    tf        tx_fee;
    msg       tx_msg;
    msgId     integer;
    event     tx_event;
    eventId   integer;
    attr      tx_msg_event_attr;
    sm        tx_single_message_cache;
    aj        tx_address_join;
    mj        tx_marker_join;
    nj        tx_nft_join;
    gp        gov_proposal;
    pm        proposal_monitor;
    gd        gov_deposit;
    gv        gov_vote;
    ibcl      ibc_ledger;
    codej     tx_sm_code;
    contractj tx_sm_contract;
    sig       signature_join;
    tfp       tx_feepayer;
BEGIN

    -- Insert tx record, getting id
    WITH t AS (
        INSERT INTO tx_cache (hash, height, gas_wanted, gas_used, tx_timestamp, error_code, codespace, tx_v2)
            VALUES ((tu).tx.hash,
                    tx_height,
                    (tu).tx.gas_wanted,
                    (tu).tx.gas_used,
                    timez,
                    (tu).tx.error_code,
                    (tu).tx.codespace,
                    (tu).tx.tx_v2)
            ON CONFLICT (hash) DO NOTHING
            RETURNING id
    )
    SELECT * FROM t
    UNION SELECT id FROM tx_cache WHERE hash = (tu).tx.hash
    INTO tx_id;

    -- insert gas fee
    INSERT INTO tx_gas_cache(hash, gas_wanted, gas_used, tx_timestamp, fee_amount)
    VALUES ((tu).txGasFee.hash, (tu).txGasFee.gas_wanted, (tu).txGasFee.gas_used, timez, (tu).txGasFee.fee_amount)
    ON CONFLICT (hash) DO NOTHING;
    -- insert market rate
    INSERT INTO validator_market_rate(block_height, block_timestamp, proposer_address, tx_hash_id, tx_hash, market_rate,
                                      success)
    VALUES (tx_height, timez, (tu).valMarketRate.proposer_address, tx_id, (tu).valMarketRate.tx_hash,
            (tu).valMarketRate.market_rate, (tu).valMarketRate.success)
    ON CONFLICT (tx_hash) DO NOTHING;
    -- insert fee
    FOREACH tf IN ARRAY (tu).txFees
        LOOP
            INSERT INTO tx_fee(tx_hash, fee_type, marker_id, marker, amount, block_height, tx_hash_id, msg_type)
            VALUES (tf.tx_hash, tf.fee_type, tf.marker_id, tf.marker, tf.amount, tx_height, tx_id, tf.msg_type)
            ON CONFLICT (tx_hash_id, fee_type, COALESCE(msg_type,''), marker_id) DO NOTHING;
        END LOOP;

    -- insert msgs
    FOREACH msg IN ARRAY (tu).txMsgs
        LOOP
            WITH m AS (
                INSERT INTO tx_message (tx_hash, block_height, tx_hash_id, tx_message_type_id, tx_message_hash,
                                        msg_idx, "tx_message")
                    VALUES ((msg).txMsg.tx_hash,
                            tx_height,
                            tx_id,
                            (msg).txMsg.tx_message_type_id,
                            (msg).txMsg.tx_message_hash,
                            (msg).txMsg.msg_idx,
                            (msg).txMsg.tx_message)
                    ON CONFLICT (tx_hash_id, tx_message_hash, msg_idx) DO NOTHING
                    RETURNING id
            )
            SELECT * FROM m
            UNION SELECT id FROM tx_message
            WHERE tx_hash_id = tx_id
              AND tx_message_hash = (msg).txMsg.tx_message_hash
              AND msg_idx = (msg).txMsg.msg_idx
            INTO msgId;
            -- insert events
            FOREACH event IN ARRAY (msg).txEvents
                LOOP
                    WITH e AS (
                        INSERT INTO tx_msg_event (tx_hash, block_height, tx_hash_id, tx_message_id, event_type,
                                                  tx_msg_type_id)
                            VALUES ((event).txEvent.tx_hash,
                                    tx_height,
                                    tx_id,
                                    msgId,
                                    (event).txEvent.event_type,
                                    (event).txEvent.tx_msg_type_id)
                            ON CONFLICT (tx_hash_id, tx_message_id, event_type) DO NOTHING
                            RETURNING id
                    )
                    SELECT * FROM e
                    UNION SELECT id FROM tx_msg_event
                    WHERE tx_hash_id = tx_id
                      AND tx_message_id = msgId
                      AND event_type = (event).txEvent.event_type
                    INTO eventId;
                    -- insert attributes
                    FOREACH attr IN ARRAY (event).txAttrs
                        LOOP
                            INSERT INTO tx_msg_event_attr(tx_msg_event_id, attr_key, attr_value, attr_idx, attr_hash)
                            VALUES (eventId, attr.attr_key, attr.attr_value, attr.attr_idx, attr.attr_hash)
                            ON CONFLICT (tx_msg_event_id, attr_hash) DO NOTHING;
                        END LOOP;
                END LOOP;
        END LOOP;

    -- insert single msg record
    FOREACH sm IN ARRAY (tu).singleMsg
        LOOP
            INSERT INTO tx_single_message_cache(tx_hash, tx_timestamp, gas_used, tx_message_type)
            VALUES (sm.tx_hash, timez, sm.gas_used, sm.tx_message_type)
            ON CONFLICT (tx_hash) DO NOTHING;
        END LOOP;

    -- insert address join
    FOREACH aj IN ARRAY (tu).addressJoin
        LOOP
            INSERT INTO tx_address_join(block_height, tx_hash, address, tx_hash_id, address_id, address_type)
            VALUES (tx_height, aj.tx_hash, aj.address, tx_id, aj.address_id, aj.address_type)
            ON CONFLICT (tx_hash, address) DO NOTHING;
        END LOOP;

    -- insert marker join
    FOREACH mj IN ARRAY (tu).markerJoin
        LOOP
            INSERT INTO tx_marker_join(block_height, tx_hash, denom, tx_hash_id, marker_id)
            VALUES (tx_height, mj.tx_hash, mj.denom, tx_id, mj.marker_id)
            ON CONFLICT (tx_hash, marker_id) DO NOTHING;
        END LOOP;

    -- insert scope join
    FOREACH nj IN ARRAY (tu).nftJoin
        LOOP
            INSERT INTO tx_nft_join(block_height, tx_hash, tx_hash_id, metadata_id, metadata_type,
                                    metadata_uuid)
            VALUES (tx_height, nj.tx_hash, tx_id, nj.metadata_id, nj.metadata_type, nj.metadata_uuid)
            ON CONFLICT (tx_hash, metadata_type, metadata_id) DO NOTHING;
        END LOOP;

    -- insert proposal
    FOREACH gp IN ARRAY (tu).proposals
        LOOP
            INSERT INTO gov_proposal(proposal_id, proposal_type, address_id, address, is_validator, title,
                                     description,
                                     status, data, content, block_height, tx_hash, tx_timestamp)
            VALUES (gp.proposal_id,
                    gp.proposal_type,
                    gp.address_id,
                    gp.address,
                    gp.is_validator,
                    gp.title,
                    gp.description,
                    gp.status,
                    gp.data,
                    gp.content,
                    tx_height,
                    gp.tx_hash,
                    timez)
            ON CONFLICT (proposal_id) DO UPDATE
                SET status       = gp.status,
                    data         = gp.data,
                    tx_hash      = gp.tx_hash,
                    tx_timestamp = timez,
                    block_height = tx_height;
        END LOOP;
    -- insert monitor
    FOREACH pm IN ARRAY (tu).proposalMonitors
        LOOP
            INSERT INTO proposal_monitor(proposal_id, submitted_height, proposed_completion_height,
                                         voting_end_time, proposal_type, matching_data_hash)
            VALUES (pm.proposal_id,
                    pm.submitted_height,
                    pm.proposed_completion_height,
                    pm.voting_end_time,
                    pm.proposal_type,
                    pm.matching_data_hash)
            ON CONFLICT (proposal_id) DO NOTHING;
        END LOOP;
    -- insert deposit
    FOREACH gd IN ARRAY (tu).deposits
        LOOP
            INSERT INTO gov_deposit(proposal_id, address_id, address, block_height, tx_hash, tx_timestamp,
                                    is_validator, deposit_type, amount, denom)
            VALUES (gd.proposal_id,
                    gd.address_id,
                    gd.address,
                    tx_height,
                    gd.tx_hash,
                    timez,
                    gd.is_validator,
                    gd.deposit_type,
                    gd.amount,
                    gd.denom)
            ON CONFLICT (proposal_id, tx_hash, deposit_type, address_id, denom) DO NOTHING;
        END LOOP;
    -- insert vote
    FOREACH gv IN ARRAY (tu).votes
        LOOP
            INSERT INTO gov_vote(proposal_id, address_id, address, block_height, tx_hash, tx_timestamp,
                                 is_validator, vote)
            VALUES (gv.proposal_id,
                    gv.address_id,
                    gv.address,
                    tx_height,
                    gv.tx_hash,
                    timez,
                    gv.is_validator,
                    gv.vote)
            ON CONFLICT (proposal_id, address_id) DO UPDATE
                SET vote         = gv.vote,
                    block_height = tx_height,
                    tx_hash      = gv.tx_hash,
                    tx_timestamp = timez
            WHERE tx_height > gov_vote.block_height;
        END LOOP;

    -- insert ledger
    FOREACH ibcl IN ARRAY (tu).ibcLedgers
        LOOP
            INSERT INTO ibc_ledger(dst_chain_name, channel_id, denom, denom_trace, balance_in, balance_out,
                                   from_address, to_address, pass_through_address_id, pass_through_address,
                                   logs, block_height, tx_hash_id, tx_hash, tx_timestamp, acknowledged,
                                   ack_success)
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
                    ibcl.ack_success)
            ON CONFLICT (channel_id, from_address, to_address, denom_trace, balance_in, balance_out, tx_hash_id)
                DO UPDATE
                SET acknowledged     = true,
                    ack_success      = ibcl.ack_success,
                    ack_logs         = ibcl.ack_logs,
                    ack_block_height = tx_height,
                    ack_tx_hash_id   = tx_id,
                    ack_tx_hash      = ibcl.ack_tx_hash,
                    ack_tx_timestamp = timez;
        END LOOP;

    -- insert sm join
    FOREACH codej IN ARRAY (tu).smCodes
        LOOP
            INSERT INTO tx_sm_code(block_height, tx_hash, tx_hash_id, sm_code)
            VALUES (tx_height, codej.tx_hash, tx_id, codej.sm_code)
            ON CONFLICT (tx_hash, sm_code) DO NOTHING;
        END LOOP;

    FOREACH contractj IN ARRAY (tu).smContracts
        LOOP
            INSERT INTO tx_sm_contract(block_height, tx_hash, tx_hash_id, sm_contract_id, sm_contract_address)
            VALUES (tx_height, contractj.tx_hash, tx_id, contractj.sm_contract_id, contractj.sm_contract_address)
            ON CONFLICT (tx_hash, sm_contract_id) DO NOTHING;
        END LOOP;

    -- insert sig join
    FOREACH sig IN ARRAY (tu).sigs
        LOOP
            INSERT INTO signature_join(join_type, join_key, signature_id)
            VALUES (sig.join_type, sig.join_key, sig.signature_id)
            ON CONFLICT (join_type, join_key, signature_id) DO NOTHING;
        END LOOP;

    -- insert feepayer
    FOREACH tfp IN ARRAY (tu).feepayers
        LOOP
            INSERT INTO tx_feepayer(block_height, tx_hash_id, tx_hash, payer_type, address_id, address)
            VALUES (tx_height, tx_id, tfp.tx_hash, tfp.payer_type, tfp.address_id, tfp.address)
            ON CONFLICT (tx_hash, payer_type, address_id) DO NOTHING;
        END LOOP;

    RAISE INFO 'UPDATED tx';
EXCEPTION
    WHEN others THEN
        RAISE EXCEPTION 'Error saving tx %', (tu).tx.hash;
END;
$$;

SELECT 'Creating procedure update_tx_fees()' AS comment;
CREATE OR REPLACE PROCEDURE update_tx_fees(updateFromHeight int)
    LANGUAGE plpgsql
AS
$$
BEGIN

    DELETE FROM tx_fee WHERE block_height >= updateFromHeight;

    WITH baseVars (baseDenom, height) AS ( VALUES ('nhash', updateFromHeight) ),
         msgFees (protoType, additionalFee) AS (
             values ('/provenance.attribute.v1.MsgAddAttributeRequest', 10000000000),
                    ('/provenance.metadata.v1.MsgWriteScopeRequest', 10000000000),
                    ('/provenance.metadata.v1.MsgP8eMemorializeContractRequest', 10000000000),
                    ('/provenance.name.v1.MsgBindNameRequest', 10000000000),
                    ('/cosmos.gov.v1beta1.MsgSubmitProposal', 100000000000),
                    ('/provenance.marker.v1.MsgAddMarkerRequest', 100000000000)
         ),
         msgEvents (protoType, event, field) AS (
             values ('/provenance.attribute.v1.MsgAddAttributeRequest', 'provenance.attribute.v1.EventAttributeAdd', 'account'),
                    ('/provenance.metadata.v1.MsgWriteScopeRequest', 'provenance.metadata.v1.EventScopeCreated', 'scope_addr'),
                    ('/provenance.metadata.v1.MsgWriteScopeRequest', 'provenance.metadata.v1.EventScopeUpdated', 'scope_addr'),
                    ('/provenance.name.v1.MsgBindNameRequest', 'provenance.name.v1.EventNameBound', 'address'),
                    ('/cosmos.gov.v1beta1.MsgSubmitProposal', 'submit_proposal', 'proposal_id'),
                    ('/provenance.marker.v1.MsgAddMarkerRequest', 'provenance.marker.v1.EventMarkerAdd', 'denom')
         ),
         msgFeeMsg AS (
             SELECT tm.tx_hash_id,
                    tmt.proto_type,
                    tmt.type,
                    mf.additionalFee,
                    count(*) count,
                    count(*) * mf.additionalFee typedFees
             FROM baseVars, tx_message tm
                                JOIN tx_message_type tmt ON tm.tx_message_type_id = tmt.id
                                JOIN msgFees mf ON mf.protoType = tmt.proto_type
             WHERE tm.block_height >= baseVars.height
               AND tmt.proto_type = ANY (SELECT protoType FROM msgFees)
             GROUP BY tm.tx_hash_id, tmt.proto_type, tmt.type, mf.additionalFee
         ),
         msgFeeEvent AS (
             SELECT tm.tx_hash_id,
                    mf.protoType,
                    tmt.type,
                    mf.additionalFee,
                    count(*) count,
                    count(*) * mf.additionalFee typedFees
             FROM baseVars, tx_message tm
                                JOIN tx_message_type tmt ON tm.tx_message_type_id = tmt.id
                                JOIN tx_msg_event tme ON tm.id = tme.tx_message_id
                                JOIN tx_msg_event_attr tmea ON tme.id = tmea.tx_msg_event_id
                                JOIN msgEvents me ON me.event = tme.event_type
                                JOIN msgFees mf ON me.protoType = mf.protoType
             WHERE tm.block_height >= baseVars.height
               AND tmt.proto_type IN ('/cosmwasm.wasm.v1.MsgExecuteContract')
               AND tme.event_type = ANY (SELECT event FROM msgEvents)
               AND tmea.attr_key = ANY (SELECT field FROM msgEvents)
             GROUP BY tm.tx_hash_id, mf.protoType, tmt.type, tme.event_type, tmea.attr_key, mf.additionalFee
         ),
         msgFeesByType AS (
             SELECT
                 tx_hash_id,
                 proto_type,
                 type,
                 baseVars.baseDenom denom,
                 additionalFee,
                 sum(count) count,
                 sum(typedFees) typedFees
             FROM (
                      SELECT *
                      FROM msgFeeMsg mfm
                      UNION ALL
                      SELECT *
                      FROM msgFeeEvent mfe
                  ) AS combined, baseVars
             GROUP BY tx_hash_id, proto_type, type, baseVars.baseDenom, additionalFee
         ),
         msgFeesByTx AS (
             SELECT
                 tx_hash_id,
                 sum(typedFees) txMsgFees
             FROM msgFeesByType
             GROUP BY tx_hash_id
         ),
         base AS (
             SELECT tc.height,
                    tc.id,
                    tc.hash,
                    tc.error_code is null                                 success,
                    amounts.denom                                         denom,
                    amounts.amount::numeric                               amount,
                    (tc.tx_v2 -> 'tx_response' ->> 'gas_wanted')::numeric wanted,
                    (tc.tx_v2 -> 'tx_response' ->> 'gas_used')::numeric   used
             FROM baseVars, tx_cache tc,
                  jsonb_to_recordset(tc.tx_v2 -> 'tx' -> 'auth_info' -> 'fee' -> 'amount') amounts("denom" text, "amount" text)
             WHERE tc.height >= baseVars.height
         ),
         marketRate AS (
             SELECT
                 base.id tx_hash_id,
                 (base.amount - COALESCE(mfbt.txMsgFees, 0)) / base.wanted marketRate
             FROM base
                      LEFT JOIN msgFeesByTx mfbt ON mfbt.tx_hash_id = base.id
         ),
         feeAmount AS (
             SELECT base.id                                                         txId,
                    mc.id                                                           denomId,
                    base.denom,
                    MAX(amount) feeAmount,
                    MAX(CASE
                            WHEN base.denom = baseVars.baseDenom AND used > wanted
                                THEN round(amount) - COALESCE(mfbt.txMsgFees, 0)
                            WHEN base.denom = baseVars.baseDenom AND used <= wanted
                                THEN round(used * marketRate)
                        END)                                                        baseFeeUsed,
                    MAX(CASE
                            WHEN base.denom = baseVars.baseDenom AND used > wanted
                                THEN 0
                            WHEN base.denom = baseVars.baseDenom AND used <= wanted
                                THEN round(amount) - COALESCE(mfbt.txMsgFees, 0) - round(used * marketRate)
                        END)                                                        baseFeeOverage,
                    MAX(CASE WHEN base.denom = baseVars.baseDenom THEN mfbt.txMsgFees END) txMsgFees
             FROM baseVars,
                  base
                      JOIN marker_cache mc ON base.denom = mc.denom
                      JOIN marketRate mr ON mr.tx_hash_id = base.id
                      LEFT JOIN msgFeesByTx mfbt ON mfbt.tx_hash_id = base.id
             GROUP BY base.id, mc.id, base.denom
         ),
         arrays AS (
             SELECT txId,
                    denomId,
                    denom,
                    ARRAY [baseFeeUsed, baseFeeOverage]             feeArray,
                    ARRAY ['BASE_FEE_USED', 'BASE_FEE_OVERAGE'] typeArray
             FROM feeAmount
         ),
         matched AS (
             SELECT arrays.txId              txId,
                    arrays.denomId           denomId,
                    arrays.denom             denom,
                    unnest(arrays.feeArray)  fee,
                    unnest(arrays.typeArray) typed,
                    null                     msgType
             FROM arrays
         ),
         unioned AS (
             SELECT *
             FROM matched
             UNION ALL
             (
                 SELECT tx_hash_id      txId,
                        mc.id           denomId,
                        mfbt.denom      denom,
                        mfbt.typedFees  fee,
                        'MSG_BASED_FEE' typed,
                        mfbt.type       msgType
                 FROM msgFeesByType mfbt
                          JOIN marker_cache mc ON mfbt.denom = mc.denom
                          JOIN base ON base.id = mfbt.tx_hash_id
                 WHERE base.success
             )
         )
    INSERT INTO tx_fee (block_height, tx_hash_id, tx_hash, fee_type, marker_id, marker, amount, msg_type)
    SELECT base.height, base.id, base.hash, unioned.typed, unioned.denomId, unioned.denom, unioned.fee, unioned.msgType
    FROM base
             JOIN unioned ON base.id = unioned.txId
    WHERE fee IS NOT NULL AND fee != 0
    ON CONFLICT (tx_hash_id, fee_type, COALESCE(msg_type,''), marker_id) DO NOTHING;

END;
$$;
