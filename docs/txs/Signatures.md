# Signatures

### Processing

1) ingest accounts
   a) save if found
2) ingest txs
   1) save feepayers
   2) get signatures
   3) for secp256k1, secp256r1, ed25519
      1) save as single signatures
      2) attach to account
      3) return ids
   4) for LegacyAminoPubKey
      1) save as a whole signature
      2) save individual sigs
         1) attach the multi sig id
      3) return whole sig id
   5) attach to tx
   6) save first signer as feepayer record


### DB
Table: signature

```
create table if not exists signature (
    id                 serial primary key,
    pubkey_object_hash varchar(200) not null,
    base_64_sig        varchar(128) null,
    pubkey_type        varchar(128) not null,
    pubkey_object      jsonb        not null,
    address            VARCHAR(128) not null
);

create unique index if not exists signature_unique_idx on signature (pubkey_object_hash);
CREATE INDEX IF NOT EXISTS signature_address_idx ON signature(address);
```

Table: signature_multi_join

```
create table if not exists signature_multi_join (
    id                 serial primary key,
    multi_sig_id       INTEGER NOT NULL,
    multi_sig_address  VARCHAR(128) NOT NULL,
    sig_id             INTEGER NOT NULL,
    sig_address        VARCHAR(128) NOT NULL,
    sig_idx            INTEGER NOT NULL
);

create unique index if not exists signature_multi_join_unique_idx on signature_multi_join (multi_sig_id, sig_id);
create index if not exists signature_multi_join_sig_id_idx on signature_multi_join (sig_id);
create index if not exists signature_multi_join_multi_sig_address_idx on signature_multi_join (multi_sig_address);
create index if not exists signature_multi_join_sig_address_idx on signature_multi_join (sig_address);
```

Table: signature_tx

```
CREATE TABLE IF NOT EXISTS signature_tx (
    id           SERIAL PRIMARY KEY,
    tx_hash_id   INTEGER not null,
    block_height INTEGER not null,
    tx_hash      VARCHAR(64) not null,
    sig_idx      INTEGER not null,
    sig_id       INTEGER not null,
    sequence     INTEGER not null
);

CREATE UNIQUE INDEX IF NOT EXISTS signature_tx_unique_idx ON signature_tx(tx_hash_id, sig_idx);
CREATE INDEX IF NOT EXISTS signature_tx_tx_hash_id_idx ON signature_tx(tx_hash_id);
CREATE INDEX IF NOT EXISTS signature_tx_sig_id_idx ON signature_tx(sig_id);
```

### Queries

Fetching sigs on tx
```
select
       st.sig_idx,
       st.sequence,
       s.pubkey_type,
       s.pubkey_object
from signature_tx st
join signature s on st.sig_id = s.id
where st.tx_hash_id = 'blah'
order by st.sig_idx
```

Fetching sigs on account
```
select
       s.pubkey_type main_type,
       s.pubkey_object main_pubkey,
       s.base_64_sig main_base64,
       smj.sig_idx,
       smj.sig_address child_address
from  signature s
left join signature_multi_join smj on s.id = smj.multi_sig_id
where s.address = 'blah'
order by sig_idx
```
