-- Loan servicing header from x/ledger.
ALTER TABLE scope_nav_snapshot
    ADD COLUMN IF NOT EXISTS next_pmt_amt BIGINT,
    ADD COLUMN IF NOT EXISTS next_pmt_denom VARCHAR(128),
    ADD COLUMN IF NOT EXISTS next_pmt_date DATE,
    ADD COLUMN IF NOT EXISTS maturity_date DATE,
    ADD COLUMN IF NOT EXISTS loan_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS interest_rate INTEGER;

COMMENT ON COLUMN scope_nav_snapshot.interest_rate IS
    'x/ledger interest_rate: 0-100000000, where 100000000 = 100% (e.g. 4321987 = 4.321987%)';
