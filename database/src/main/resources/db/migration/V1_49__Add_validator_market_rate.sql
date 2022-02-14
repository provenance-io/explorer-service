SELECT 'Adding validator_market_rate table' AS comment;
CREATE TABLE IF NOT EXISTS validator_market_rate
(
    id               SERIAL PRIMARY KEY,
    block_height     INT          NOT NULL,
    block_timestamp  TIMESTAMP    NOT NULL,
    proposer_address VARCHAR(128) NOT NULL,
    tx_hash_id       INT          NOT NULL,
    tx_hash          VARCHAR(64)  NOT NULL,
    market_rate      NUMERIC      NOT NULL,
    success          BOOLEAN      NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS validator_market_rate_unique_idx ON validator_market_rate (tx_hash);
CREATE INDEX IF NOT EXISTS validator_market_rate_proposer_idx ON validator_market_rate (proposer_address);
CREATE INDEX IF NOT EXISTS validator_market_rate_timestamp_idx ON validator_market_rate (block_timestamp);

SELECT 'Create procedure to insert into validator_market_rate table' AS comment;
CREATE OR REPLACE PROCEDURE update_market_rate(updateFromHeight int, fromHeight int, toHeight int)
    LANGUAGE plpgsql
AS
$$
BEGIN

    WITH baseVars (baseDenom, height) AS (VALUES ('nhash', updateFromHeight)),
         msgFees (protoType, additionalFee) AS (
             values ('/provenance.attribute.v1.MsgAddAttributeRequest', 10000000000),
                    ('/provenance.metadata.v1.MsgWriteScopeRequest', 10000000000),
                    ('/provenance.metadata.v1.MsgP8eMemorializeContractRequest', 10000000000),
                    ('/provenance.name.v1.MsgBindNameRequest', 10000000000),
                    ('/cosmos.gov.v1beta1.MsgSubmitProposal', 100000000000),
                    ('/provenance.marker.v1.MsgAddMarkerRequest', 100000000000)
         ),
         msgEvents (protoType, event, field) AS (
             values ('/provenance.attribute.v1.MsgAddAttributeRequest', 'provenance.attribute.v1.EventAttributeAdd',
                     'account'),
                    ('/provenance.metadata.v1.MsgWriteScopeRequest', 'provenance.metadata.v1.EventScopeCreated',
                     'scope_addr'),
                    ('/provenance.metadata.v1.MsgWriteScopeRequest', 'provenance.metadata.v1.EventScopeUpdated',
                     'scope_addr'),
                    ('/provenance.name.v1.MsgBindNameRequest', 'provenance.name.v1.EventNameBound', 'address'),
                    ('/cosmos.gov.v1beta1.MsgSubmitProposal', 'submit_proposal', 'proposal_id'),
                    ('/provenance.marker.v1.MsgAddMarkerRequest', 'provenance.marker.v1.EventMarkerAdd', 'denom')
         ),
         msgFeeMsg AS (
             SELECT tm.tx_hash_id,
                    tmt.proto_type,
                    tmt.type,
                    mf.additionalFee,
                    count(*)                    count,
                    count(*) * mf.additionalFee typedFees
             FROM baseVars,
                  tx_message tm
                      JOIN tx_message_type tmt ON tm.tx_message_type_id = tmt.id
                      JOIN msgFees mf ON mf.protoType = tmt.proto_type
             WHERE tm.block_height between fromHeight and toHeight
               AND tmt.proto_type = ANY (SELECT protoType FROM msgFees)
             GROUP BY tm.tx_hash_id, tmt.proto_type, tmt.type, mf.additionalFee
         ),
         msgFeeEvent AS (
             SELECT tm.tx_hash_id,
                    mf.protoType,
                    tmt.type,
                    mf.additionalFee,
                    count(*)                    count,
                    count(*) * mf.additionalFee typedFees
             FROM baseVars,
                  tx_message tm
                      JOIN tx_message_type tmt ON tm.tx_message_type_id = tmt.id
                      JOIN tx_msg_event tme ON tm.id = tme.tx_message_id
                      JOIN tx_msg_event_attr tmea ON tme.id = tmea.tx_msg_event_id
                      JOIN msgEvents me ON me.event = tme.event_type
                      JOIN msgFees mf ON me.protoType = mf.protoType
             WHERE tm.block_height between fromHeight and toHeight
               AND tmt.proto_type IN ('/cosmwasm.wasm.v1.MsgExecuteContract')
               AND tme.event_type = ANY (SELECT event FROM msgEvents)
               AND tmea.attr_key = ANY (SELECT field FROM msgEvents)
             GROUP BY tm.tx_hash_id, mf.protoType, tmt.type, tme.event_type, tmea.attr_key, mf.additionalFee
         ),
         msgFeesByType AS (
             SELECT tx_hash_id,
                    proto_type,
                    type,
                    baseVars.baseDenom denom,
                    additionalFee,
                    sum(count)         count,
                    sum(typedFees)     typedFees
             FROM (
                      SELECT *
                      FROM msgFeeMsg mfm
                      UNION ALL
                      SELECT *
                      FROM msgFeeEvent mfe
                  ) AS combined,
                  baseVars
             GROUP BY tx_hash_id, proto_type, type, baseVars.baseDenom, additionalFee
         ),
         msgFeesByTx AS (
             SELECT tx_hash_id,
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
             FROM baseVars,
                  tx_cache tc,
                  jsonb_to_recordset(tc.tx_v2 -> 'tx' -> 'auth_info' -> 'fee' -> 'amount') amounts("denom" text, "amount" text)
             WHERE tc.height between fromHeight and toHeight
         ),
         marketRate AS (
             SELECT base.id tx_hash_id,
                    CASE
                        WHEN base.height < baseVars.height
                            THEN base.amount / base.wanted
                        WHEN base.height >= baseVars.height
                            THEN (base.amount - COALESCE(mfbt.txMsgFees, 0)) / base.wanted
                        END marketRate
             FROM baseVars,
                  base
                      LEFT JOIN msgFeesByTx mfbt ON mfbt.tx_hash_id = base.id
         )
    INSERT INTO validator_market_rate (block_height, block_timestamp, proposer_address, tx_hash_id, tx_hash, market_rate,
                                       success)
    SELECT tc.height                    block_height,
           bp.block_timestamp           block_timestamp,
           bp.proposer_operator_address proposer_address,
           tc.id                        tx_hash_id,
           tc.hash                      tx_hash,
           mr.marketRate                market_rate,
           tc.success
    FROM base tc
             JOIN marketRate mr ON tc.id = mr.tx_hash_id
             JOIN block_proposer bp ON tc.height = bp.block_height
    ON CONFLICT (tx_hash) DO NOTHING ;
END;
$$;

ALTER TABLE block_proposer
    DROP COLUMN IF EXISTS min_gas_fee;

DO $$
    BEGIN
        IF EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_name='validator_gas_fee_cache' and column_name='min_gas_fee')
        THEN
            ALTER TABLE validator_gas_fee_cache RENAME COLUMN min_gas_fee TO min_market_rate;
        END IF;
        IF EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_name='validator_gas_fee_cache' and column_name='max_gas_fee')
        THEN
            ALTER TABLE validator_gas_fee_cache RENAME COLUMN max_gas_fee TO max_market_rate;
        END IF;
        IF EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_name='validator_gas_fee_cache' and column_name='avg_gas_fee')
        THEN
            ALTER TABLE validator_gas_fee_cache RENAME COLUMN avg_gas_fee TO avg_market_rate;
        END IF;
    END $$;
ALTER TABLE IF EXISTS validator_gas_fee_cache RENAME TO validator_market_rate_stats;
ALTER INDEX IF EXISTS validator_gas_fee_cache_date_operator_address_key RENAME TO validator_market_rate_stats_date_operator_address_key;
ALTER INDEX IF EXISTS validator_gas_fee_cache_date_idx RENAME TO validator_market_rate_stats_date_idx;
ALTER INDEX IF EXISTS validator_gas_fee_cache_operator_address_idx RENAME TO validator_market_rate_stats_operator_address_idx;

DO $$
    BEGIN
        IF EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_name='chain_gas_fee_cache' and column_name='min_gas_fee')
        THEN
            ALTER TABLE chain_gas_fee_cache RENAME COLUMN min_gas_fee TO min_market_rate;
        END IF;
        IF EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_name='chain_gas_fee_cache' and column_name='max_gas_fee')
        THEN
            ALTER TABLE chain_gas_fee_cache RENAME COLUMN max_gas_fee TO max_market_rate;
        END IF;
        IF EXISTS(SELECT *
                  FROM information_schema.columns
                  WHERE table_name='chain_gas_fee_cache' and column_name='avg_gas_fee')
        THEN
            ALTER TABLE chain_gas_fee_cache RENAME COLUMN avg_gas_fee TO avg_market_rate;
        END IF;
    END $$;
ALTER TABLE IF EXISTS chain_gas_fee_cache RENAME TO chain_market_rate_stats;
ALTER INDEX IF EXISTS chain_gas_fee_cache_date_idx RENAME TO chain_market_rate_stats_date_idx;

SELECT 'Create procedure to update market_rate_stats tables' AS comment;
CREATE OR REPLACE PROCEDURE update_market_rate_stats(fromDate date)
    LANGUAGE plpgsql
AS
$$
BEGIN

    INSERT INTO validator_market_rate_stats(date, operator_address, min_market_rate, max_market_rate, avg_market_rate)
    SELECT date_trunc('DAY', block_timestamp) date,
           proposer_address                   operator_address,
           min(market_rate)                   min_market_rate,
           max(market_rate)                   max_market_rate,
           avg(market_rate)                   avg_market_rate
    FROM validator_market_rate vmr
    where date_trunc('DAY', block_timestamp) > fromDate
    group by date_trunc('DAY', block_timestamp), proposer_address
    ON CONFLICT (date, operator_address) DO UPDATE
        SET min_market_rate = excluded.min_market_rate,
            max_market_rate = excluded.max_market_rate,
            avg_market_rate = excluded.avg_market_rate;

    INSERT INTO chain_market_rate_stats(date, min_market_rate, max_market_rate, avg_market_rate)
    SELECT date_trunc('DAY', block_timestamp) date,
           min(market_rate)                   min_market_rate,
           max(market_rate)                   max_market_rate,
           avg(market_rate)                   avg_market_rate
    FROM validator_market_rate vmr
    where date_trunc('DAY', block_timestamp) > fromDate
    group by date_trunc('DAY', block_timestamp)
    ON CONFLICT (date) DO UPDATE
        SET min_market_rate = excluded.min_market_rate,
            max_market_rate = excluded.max_market_rate,
            avg_market_rate = excluded.avg_market_rate;
END;
$$;

DELETE FROM token_distribution_amounts WHERE true;
