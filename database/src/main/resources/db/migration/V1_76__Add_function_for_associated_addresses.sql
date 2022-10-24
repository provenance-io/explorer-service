SELECT 'Add function get_tx_associated_values()' AS comment;

drop function if exists get_tx_associated_values(text, integer);

create or replace function get_tx_associated_values(tx_hash text, tx_height integer)
    returns TABLE(value varchar, type varchar)
    language plpgsql as
$$
BEGIN
    RETURN QUERY
-- BASE tx info
        with tx AS (
            select id from tx_cache where hash = tx_hash and height = tx_height
        )
-- denom match
        select
            tmj.denom as value,
            'DENOM' as type
        from tx_marker_join tmj , tx
        where tmj.tx_hash_id = tx.id
        union all
-- nft uuids
        select
            tnj.metadata_uuid as value,
            tnj.metadata_type as type
        from tx_nft_join tnj , tx
        where tnj.tx_hash_id = tx.id
        union all
-- nft scope addr
        select
            ns.address as value,
            tnj.metadata_type as type
        from tx_nft_join tnj
                 join nft_scope ns ON tnj.metadata_type = 'SCOPE' AND tnj.metadata_id = ns.id, tx
        where tnj.tx_hash_id = tx.id
        union all
-- nft scope spec addr
        select
            nss.address as value,
            tnj.metadata_type as type
        from tx_nft_join tnj
                 join nft_scope_spec nss ON tnj.metadata_type = 'SCOPE_SPEC' AND tnj.metadata_id = nss.id, tx
        where tnj.tx_hash_id = tx.id
        union all
-- nft contract spec addr
        select
            ncs.address as value,
            tnj.metadata_type as type
        from tx_nft_join tnj
                 join nft_contract_spec ncs ON tnj.metadata_type = 'CONTRACT_SPEC' AND tnj.metadata_id = ncs.id, tx
        where tnj.tx_hash_id = tx.id
        union all
-- SC CODE
        select
            tmc.sm_code::text as value,
            'CODE' as type
        from tx_sm_code tmc , tx
        where tmc.tx_hash_id = tx.id
        union all
-- SC Contract
        select
            tmct.sm_contract_address as value,
            'CONTRACT' as type
        from tx_sm_contract tmct , tx
        where tmct.tx_hash_id = tx.id
        union all
-- address match, both account and operator (validator)
        select
            taj.address  as value,
            taj.address_type  as type
        from tx_address_join taj
                 left join account on taj.address = account.account_address, tx
        where taj.tx_hash_id = tx.id
          and (account.is_contract is null or account.is_contract = false)
        union all
-- proposal match from proposals
        select
            gp.proposal_id::text as value,
            'PROPOSAL' as type
        from gov_proposal gp, tx
        where gp.tx_hash_id = tx.id
        union all
-- proposal match from deposits
        select
            gd.proposal_id::text as value,
            'PROPOSAL' as type
        from gov_deposit gd, tx
        where gd.tx_hash_id = tx.id
        union all
-- proposal match from votes
        select
            gv.proposal_id::text as value,
            'PROPOSAL' as type
        from gov_vote gv, tx
        where gv.tx_hash_id = tx.id
        union all
-- ibc client
        select
            ti.client as value,
            'IBC_CLIENT' as type
        from tx_ibc ti, tx
        where ti.tx_hash_id = tx.id
        union all
-- ibc channel
        select
            (ic.src_port || ', ' || ic.src_channel) as value,
            'IBC_CHANNEL' as type
        from tx_ibc ti
                 join ibc_channel ic on ti.channel_id = ic.id, tx
        where ti.tx_hash_id = tx.id;

END $$;
