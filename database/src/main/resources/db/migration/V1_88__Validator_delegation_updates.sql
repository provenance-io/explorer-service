SELECT 'Validator Delegation Program updates' AS comment;

CREATE INDEX IF NOT EXISTS block_tx_count_cache_block_timestamp_idx on block_tx_count_cache (block_timestamp);

drop materialized view if exists block_time_spread;
Create MATERIALIZED VIEW if not exists block_time_spread as
select extract(year from block_timestamp)    as year,
       extract(quarter from block_timestamp) as quarter,
       extract(month from block_timestamp)   as month,
       min(block_height)                     as min_height,
       max(block_height)                     as max_height,
       min(block_timestamp)                  as min_time,
       max(block_timestamp)                  as max_time,
       count(*)                              as total_blocks
from block_tx_count_cache
group by extract(year from block_timestamp),
         extract(quarter from block_timestamp),
         extract(month from block_timestamp)
with data;

create table if not exists validator_metrics
(
    id               serial primary key,
    oper_addr_id     integer,
    operator_address varchar(128),
    year             integer,
    quarter          integer,
    data             jsonb
);

create unique index if not exists validator_metrics_unique_idx on validator_metrics (oper_addr_id, year, quarter);

create index if not exists validator_metrics_oper_id_idx on validator_metrics (oper_addr_id);
create index if not exists validator_metrics_oper_addr_idx on validator_metrics (operator_address);
create index if not exists validator_metrics_period_idx on validator_metrics (year, quarter);


-- Fix for
create or replace procedure insert_proposal_monitor(proposalmonitors proposal_monitor[])
    language plpgsql as
$$
DECLARE
    pm proposal_monitor;
BEGIN
    FOREACH pm IN ARRAY proposalMonitors
        LOOP
            INSERT INTO proposal_monitor(proposal_id, submitted_height, proposed_completion_height, voting_end_time,
                                         proposal_type, matching_data_hash)
            VALUES (pm.proposal_id,
                    pm.submitted_height,
                    pm.proposed_completion_height,
                    pm.voting_end_time,
                    pm.proposal_type,
                    pm.matching_data_hash)
            ON CONFLICT (proposal_id, matching_data_hash) DO NOTHING;
        END LOOP;
END;
$$;
