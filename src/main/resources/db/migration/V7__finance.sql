CREATE TABLE month_close (
    month_close_id BIGSERIAL PRIMARY KEY,
    month CHAR(7) NOT NULL,
    overhead_per_pack NUMERIC(14,4),
    closed_on DATE NOT NULL,
    report JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX month_close_month_key ON month_close (month) WHERE deleted_at IS NULL;
