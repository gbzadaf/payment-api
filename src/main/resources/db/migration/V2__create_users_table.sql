CREATE TABLE users (
                       id            UUID          PRIMARY KEY,
                       email         VARCHAR(150)  NOT NULL,
                       password_hash VARCHAR(100)  NOT NULL,
                       created_at    TIMESTAMPTZ   NOT NULL,

                       CONSTRAINT uk_users_email UNIQUE (email)
);