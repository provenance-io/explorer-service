SELECT 'Add Account token counts' AS comment;

create table if not exists account_token_count (
    id serial primary key,
    address_id integer not null,
    address varchar(128) not null,
    ft_count integer not null default 0,
    nft_count integer not null default 0
);

create unique index if not exists account_token_count_unique_idx on account_token_count(address);
create index if not exists account_token_count_address_id_idx on account_token_count(address_id);

insert into account_token_count (address_id, address)
select id, account_address from account where true
on conflict (address) do nothing ;

create table if not exists process_queue(
    id serial primary key,
    process_type varchar(128) not null,
    process_value text not null,
    processing boolean not null default false
);

create unique index if not exists process_queue_unique_idx on process_queue(process_type, process_value);

insert into process_queue (process_type, process_value)
select 'ACCOUNT', account_address from account where true
on conflict (process_type, process_value) do nothing;
