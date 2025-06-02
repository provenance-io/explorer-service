ALTER TABLE token_distribution_paginated_results
    ADD COLUMN IF NOT EXISTS spendable jsonb NOT NULL DEFAULT '{"amount": "0", "denom": "nhash"}';
