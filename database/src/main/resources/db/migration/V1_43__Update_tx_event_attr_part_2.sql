SELECT 'Update tx_msg_event_attr.attr_hash' AS comment;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE tx_msg_event_attr
SET attr_hash = encode(digest(concat(attr_idx,attr_key,attr_value)::text, 'sha512'::TEXT), 'base64'::TEXT)
WHERE attr_hash IS NULL;
