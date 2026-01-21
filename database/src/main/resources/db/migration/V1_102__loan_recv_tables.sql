CREATE TABLE ledger_entry (
    asset_uuid UUID,
    ledger_uuid UNIQUE UUID,
    bucket TEXT,
    ledger_entry_type TEXT,
    post_date TIMESTAMP,
    effective_date TIMESTAMP,
    entry_amount DOUBLE PRECISION,
    prin_applied DOUBLE PRECISION,
    prin_balance DOUBLE PRECISION,
    int_applied DOUBLE PRECISION,
    int_balance DOUBLE PRECISION,
    PRIMARY KEY (asset_uuid, ledger_uuid, bucket) -- Composite primary key
);

CREATE TABLE loan_detail (
    asset_uuid UUID PRIMARY KEY,
    asset_owner_uuid UUID,
    loan_status TEXT,
    funded_date TIMESTAMP,
    reg_pmt_amt DOUBLE PRECISION,
    cur_pmt_amt DOUBLE PRECISION,
    loan_type TEXT,
    orig_loan_amt DOUBLE PRECISION,
    prin_bal_amt DOUBLE PRECISION,
    int_bal_amt DOUBLE PRECISION,
    credit_bal_amt DOUBLE PRECISION,
    credit_limit_amt DOUBLE PRECISION,
    loan_bal_amt DOUBLE PRECISION,
    int_rate DOUBLE PRECISION,
    expected_payoff_date TIMESTAMP,
    maturity_date TIMESTAMP,
    days_delq BIGINT,
    loan_orig_term BIGINT,
    loan_term BIGINT,
    last_updated TIMESTAMP
);