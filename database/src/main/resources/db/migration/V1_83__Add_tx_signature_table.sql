SELECT 'Update signature tables' AS comment;

-- delete bad multisig objects
delete
from signature
where multi_sig_object is not null;

-- delete associated sig joins
delete
from signature_join as sj
    using signature_join as sj_join
        left outer join signature as s on sj_join.signature_id = s.id
where sj.id = sj_join.id
  and s.id is null;

-- add object hash, remove multisig object from signature
alter table signature
    drop column if exists multi_sig_object,
    alter column base_64_sig DROP NOT NULL,
    add column if not exists address            varchar(128);

drop index if exists signature_idx;

-- update the object hash
update signature
set address            = q.account_address
from (select a.account_address,
             sj.signature_id
      from signature_join sj
               left join account a on sj.join_key = a.account_address
      where join_type = 'ACCOUNT') q
where signature.id = q.signature_id;

delete
from signature
where address is null;

DELETE FROM signature
WHERE id IN
      (SELECT id
       FROM (SELECT id,
                    ROW_NUMBER() OVER ( PARTITION BY address ORDER BY id DESC) AS row_num
             FROM signature) t
       WHERE t.row_num > 1);

alter table signature
    alter column address SEt not null;

CREATE UNIQUE INDEX IF NOT EXISTS signature_address_idx ON signature (address);
CREATE INDEX IF NOT EXISTS signature_pubkey_object_idx ON signature (pubkey_object);

-- create multi sig join -> joins a known multi sig to a list of regular sigs
create table if not exists signature_multi_join
(
    id                serial primary key,
    multi_sig_id      INTEGER      NOT NULL,
    multi_sig_address VARCHAR(128) NOT NULL,
    sig_id            INTEGER      NOT NULL,
    sig_address       VARCHAR(128) NOT NULL,
    sig_idx           INTEGER      NOT NULL
);

create unique index if not exists signature_multi_join_unique_idx on signature_multi_join (multi_sig_id, sig_id);
create index if not exists signature_multi_join_sig_id_idx on signature_multi_join (sig_id);
create index if not exists signature_multi_join_multi_sig_address_idx on signature_multi_join (multi_sig_address);
create index if not exists signature_multi_join_sig_address_idx on signature_multi_join (sig_address);

-- creates join table to tx info
CREATE TABLE IF NOT EXISTS signature_tx
(
    id           SERIAL PRIMARY KEY,
    tx_hash_id   INTEGER     not null,
    block_height INTEGER     not null,
    tx_hash      VARCHAR(64) not null,
    sig_idx      INTEGER     not null,
    sig_id       INTEGER     not null,
    sequence     INTEGER     not null
);

CREATE UNIQUE INDEX IF NOT EXISTS signature_tx_unique_idx ON signature_tx (tx_hash_id, sig_idx);
CREATE INDEX IF NOT EXISTS signature_tx_tx_hash_id_idx ON signature_tx (tx_hash_id);
CREATE INDEX IF NOT EXISTS signature_tx_sig_id_idx ON signature_tx (sig_id);

SELECT 'Filling signature_tx' AS comment;

-- fills the tx join table with data
with base as (select tc.id,
                     tc.height,
                     tc.hash,
                     infos.idx - 1 as idx,
                     info.*,
                     sj.signature_id
              from tx_cache tc
                       left join signature_join sj on sj.join_type = 'TRANSACTION' and sj.join_key = tc.hash,
                   jsonb_array_elements(tc.tx_v2 -> 'tx' -> 'auth_info' -> 'signer_infos') with ordinality infos("signer", idx),
                   jsonb_to_record(infos.signer) info("sequence" integer, "mode_info" jsonb, "public_key" jsonb))
select base.id,
       base.height,
       base.hash,
       base.idx,
       CASE
           WHEN base.public_key is null THEN base.signature_id
           ELSE sig.id END,
       base.sequence
from base
         left join signature sig on base.public_key = sig.pubkey_object;
