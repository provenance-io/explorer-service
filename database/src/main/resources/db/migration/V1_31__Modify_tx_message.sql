
SELECT 'Updating tx_message' as comment;
ALTER TABLE tx_message
    ADD COLUMN IF NOT EXISTS msg_idx INT DEFAULT 0;



