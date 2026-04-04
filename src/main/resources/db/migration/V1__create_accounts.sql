CREATE TABLE accounts (
    id             UUID         PRIMARY KEY,
    account_number VARCHAR(10)  NOT NULL UNIQUE,
    owner_name     VARCHAR(100) NOT NULL,
    currency       VARCHAR(3)   NOT NULL DEFAULT 'PHP',
    status         VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_account_number ON accounts (account_number);
CREATE INDEX idx_accounts_status ON accounts (status);
