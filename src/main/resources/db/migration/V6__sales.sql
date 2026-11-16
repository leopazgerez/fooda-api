CREATE TABLE customer (
    customer_id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    phone VARCHAR(40),
    address VARCHAR(200),
    birthday CHAR(5),
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE sale (
    sale_id BIGSERIAL PRIMARY KEY,
    sale_date DATE NOT NULL,
    branch_id BIGINT NOT NULL REFERENCES branch (branch_id),
    customer_id BIGINT REFERENCES customer (customer_id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('retail','wholesale','consignment','internal_use','paid_with_packs')),
    reason VARCHAR(60),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('transfer','cash','mercado_pago','other','packs')),
    discount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount >= 0),
    shipping NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (shipping >= 0),
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX sale_date_idx ON sale (sale_date);
CREATE INDEX sale_customer_idx ON sale (customer_id);

CREATE TABLE sale_line (
    sale_line_id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL REFERENCES sale (sale_id),
    production_id BIGINT NOT NULL REFERENCES production (production_id),
    pack_id BIGINT NOT NULL REFERENCES pack (pack_id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX sale_line_sale_idx ON sale_line (sale_id);

CREATE TABLE payment (
    payment_id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL REFERENCES sale (sale_id),
    payment_date DATE NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    method VARCHAR(20) NOT NULL CHECK (method IN ('transfer','cash','mercado_pago','other','packs')),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX payment_sale_key ON payment (sale_id) WHERE deleted_at IS NULL;

ALTER TABLE expense ADD CONSTRAINT expense_sale_fk FOREIGN KEY (sale_id) REFERENCES sale (sale_id);
CREATE UNIQUE INDEX expense_sale_key ON expense (sale_id) WHERE sale_id IS NOT NULL AND deleted_at IS NULL;
