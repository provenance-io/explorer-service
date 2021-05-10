select 'Altering account' as comment;

ALTER TABLE account
    ALTER COLUMN account_number DROP NOT NULL;
ALTER TABLE account
    ALTER COLUMN base_account DROP NOT NULL;
ALTER TABLE account
    ALTER COLUMN data DROP NOT NULL;
