CREATE TABLE app_user (
    user_id        BIGSERIAL PRIMARY KEY,
    email          VARCHAR(120) NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(30)  NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX app_user_email_key ON app_user (lower(email));

CREATE TABLE refresh_token (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (user_id),
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_token (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (user_id),
    type        VARCHAR(20) NOT NULL CHECK (type IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
