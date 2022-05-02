SELECT 'Adding name table' AS comment;

CREATE TABLE IF NOT EXISTS name
(
    id           SERIAL PRIMARY KEY,
    parent       VARCHAR(550),
    child        VARCHAR(40),
    full_name    VARCHAR(600),
    owner        VARCHAR(128),
    restricted   BOOLEAN DEFAULT FALSE,
    height_added INT
);

DROP INDEX IF EXISTS name_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS name_unique_idx ON name (full_name, owner);

SELECT 'Inserting bind_names' AS comment;
WITH base AS (
    SELECT tc.height,
           tc.id,
           CASE WHEN attr.key = 'name' THEN attr.value::text END    AS full_name,
           CASE WHEN attr.key = 'address' THEN attr.value::text END AS owner
    FROM tx_cache tc
             JOIN tx_message tm ON tm.tx_hash_id = tc.id
             JOIN tx_message_type tmt on tm.tx_message_type_id = tmt.id,
         jsonb_array_elements(tc.tx_v2 -> 'tx_response' -> 'logs') with ordinality logs("events", idx),
         jsonb_to_recordset(logs.events -> 'events') event("type" text, "attributes" jsonb),
         jsonb_to_recordset(event.attributes) attr("key" text, "value" text)
    WHERE tc.error_code IS NULL
      AND tmt.proto_type IN ('/provenance.name.v1.MsgBindNameRequest',
                             '/cosmwasm.wasm.v1beta1.MsgInstantiateContract',
                             '/cosmwasm.wasm.v1.MsgInstantiateContract',
                             '/cosmwasm.wasm.v1.MsgExecuteContract',
                             '/cosmwasm.wasm.v1beta1.MsgExecuteContract')
      AND logs.idx - 1 = tm.msg_idx
      AND event.type IN ('provenance.name.v1.EventNameBound')
    ORDER BY tc.height
),
     name_agg AS (
         SELECT base.id,
                array_agg(base.full_name) AS name_agg
         FROM base
         WHERE base.full_name IS NOT NULL
         GROUP BY base.id
     ),
     owner_agg AS (
         SELECT base.id,
                array_agg(base.owner) AS owner_agg
         FROM base
         WHERE base.owner IS NOT NULL
         GROUP BY base.id
     ),
     smooshed AS (
         SELECT base.height,
                base.id                                AS tx_id,
                REPLACE(unnest(na.name_agg), '"', '')  AS full_name,
                REPLACE(unnest(oa.owner_agg), '"', '') AS owner
         FROM base
                  JOIN name_agg na ON base.id = na.id
                  JOIN owner_agg oa ON base.id = oa.id
     ),
     regexed AS (
         SELECT sm.height,
                sm.owner,
                sm.full_name,
                regexp_matches(sm.full_name, '([a-zA-Z0-9-]*)(\.[a-zA-Z0-9.-]*)') AS reg
         FROM smooshed sm
     )
INSERT
INTO name (parent, child, full_name, owner, height_added)
SELECT substr(reg[2], 2) AS parent,
       reg[1]            AS child,
       r.full_name,
       r.owner,
       MAX(r.height)
FROM regexed r
GROUP BY parent, child, full_name, owner
ON CONFLICT (full_name, owner) DO UPDATE
    SET height_added = CASE
                           WHEN excluded.height_added > name.height_added THEN excluded.height_added
                           ELSE name.height_added END;


SELECT 'Removing unbind_names' AS comment;
DELETE
FROM name
WHERE id IN (
    WITH base AS (SELECT tc.height,
                         tc.id,
                         CASE WHEN attr.key = 'name' THEN attr.value::text END    AS full_name,
                         CASE WHEN attr.key = 'address' THEN attr.value::text END AS owner
                  FROM tx_cache tc
                           JOIN tx_message tm ON tm.tx_hash_id = tc.id
                           JOIN tx_message_type tmt on tm.tx_message_type_id = tmt.id,
                       jsonb_array_elements(tc.tx_v2 -> 'tx_response' -> 'logs') with ordinality logs("events", idx),
                       jsonb_to_recordset(logs.events -> 'events') event("type" text, "attributes" jsonb),
                       jsonb_to_recordset(event.attributes) attr("key" text, "value" text)
                  WHERE tc.error_code IS NULL
                    AND tmt.proto_type IN ('/provenance.name.v1.MsgDeleteNameRequest',
                                           '/cosmwasm.wasm.v1beta1.MsgInstantiateContract',
                                           '/cosmwasm.wasm.v1.MsgInstantiateContract',
                                           '/cosmwasm.wasm.v1.MsgExecuteContract',
                                           '/cosmwasm.wasm.v1beta1.MsgExecuteContract')
                    AND logs.idx - 1 = msg_idx
                    AND event.type IN ('provenance.name.v1.EventNameUnbound')
                  ORDER BY tc.height
    ),
         smooshed AS (
             SELECT base.height,
                    base.id                          AS tx_id,
                    REPLACE(MAX(full_name), '"', '') AS full_name,
                    REPLACE(MAX(owner), '"', '')     AS owner
             FROM base
             GROUP BY base.height, base.id
         )
    SELECT name.id
    FROM smooshed sm
             JOIN name ON sm.full_name = name.full_name AND sm.owner = name.owner
    WHERE sm.height > name.height_added
);

DROP INDEX IF EXISTS name_unique_idx;
CREATE UNIQUE INDEX IF NOT EXISTS name_unique_idx ON name (full_name);
