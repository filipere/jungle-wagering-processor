CREATE TABLE wallets (
    id               VARCHAR(100)  PRIMARY KEY,
    player_id        VARCHAR(255)  NOT NULL,
    balance_amount   NUMERIC(19,2) NOT NULL,
    balance_currency VARCHAR(3)    NOT NULL,
    version          INTEGER       NOT NULL DEFAULT 1,
    CONSTRAINT uk_wallet_player_currency UNIQUE (player_id, balance_currency),
    CONSTRAINT ck_wallet_balance CHECK (balance_amount >= 0)
);

CREATE TABLE transactions (
    id                   VARCHAR(100)  PRIMARY KEY,
    wallet_id            VARCHAR(100)  NOT NULL REFERENCES wallets (id),
    type                 VARCHAR(20)   NOT NULL,
    amount               NUMERIC(19,2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    reference_id         VARCHAR(255)  NOT NULL,
    related_reference_id VARCHAR(255),
    status               VARCHAR(30)   NOT NULL,
    failure_code         VARCHAR(50),
    balance_after        NUMERIC(19,2),
    attempts             INTEGER       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_transactions_reference_id UNIQUE (reference_id),
    CONSTRAINT ck_transactions_amount CHECK (amount > 0)
);

CREATE TABLE wallet_ledger_entries (
    id                      VARCHAR(100)  PRIMARY KEY,
    wallet_id               VARCHAR(100)  NOT NULL REFERENCES wallets (id),
    transaction_id          VARCHAR(100)  NOT NULL REFERENCES transactions (id),
    direction               VARCHAR(10)   NOT NULL,
    amount                  NUMERIC(19,2) NOT NULL,
    currency                VARCHAR(3)    NOT NULL,
    balance_before          NUMERIC(19,2) NOT NULL,
    balance_currency_before VARCHAR(3)    NOT NULL,
    balance_after           NUMERIC(19,2) NOT NULL,
    balance_currency_after  VARCHAR(3)    NOT NULL,
    created_at              TIMESTAMP     NOT NULL,
    CONSTRAINT uk_ledger_wallet_transaction UNIQUE (wallet_id, transaction_id),
    CONSTRAINT ck_ledger_amount CHECK (amount > 0)
);

CREATE TABLE outbox_messages (
    id           VARCHAR(100) PRIMARY KEY,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);