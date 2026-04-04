CREATE TABLE transactions (
    id            UUID          PRIMARY KEY,
    account_id    UUID          NOT NULL REFERENCES accounts (id),
    type          VARCHAR(10)   NOT NULL,
    amount        NUMERIC(14,2) NOT NULL,
    description   VARCHAR(255),
    balance_after NUMERIC(14,2) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_account_id ON transactions (account_id);
CREATE INDEX idx_transactions_created_at ON transactions (created_at DESC);
