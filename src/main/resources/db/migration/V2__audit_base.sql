ALTER TABLE app_user
    ADD COLUMN created_by BIGINT REFERENCES app_user (user_id),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_by BIGINT REFERENCES app_user (user_id),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by BIGINT REFERENCES app_user (user_id);
