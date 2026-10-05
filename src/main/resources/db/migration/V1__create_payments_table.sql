CREATE TABLE payments (
                          id              UUID            PRIMARY KEY,
                          amount          NUMERIC(19, 2)  NOT NULL,
                          currency        VARCHAR(3)      NOT NULL,
                          status          VARCHAR(20)     NOT NULL,
                          description     VARCHAR(255),
                          idempotency_key VARCHAR(100)    NOT NULL,
                          created_at      TIMESTAMPTZ     NOT NULL,
                          updated_at      TIMESTAMPTZ     NOT NULL,

                          CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key)
);