-- Invalidate cached Total Loan Balance metrics after aggregation logic change.
-- Cached entries will be rebuilt on next request with the updated go-pulse-service query.
DELETE FROM pulse_cache
WHERE type IN (
    'LOAN_LEDGER_TOTAL_BALANCE_METRIC',
    'LOAN_LEDGER_TOTAL_COUNT_METRIC'
);
