CREATE TABLE production (
    production_id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    number INT NOT NULL CHECK (number >= 0),
    status VARCHAR(10) NOT NULL CHECK (status IN ('planned','closed')),
    start_date DATE NOT NULL,
    closed_on DATE,
    snapshot JSONB NOT NULL,
    paid_costs JSONB NOT NULL DEFAULT '{}',
    leftovers JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK ((status = 'closed') = (closed_on IS NOT NULL))
);

CREATE UNIQUE INDEX production_code_key ON production (code) WHERE deleted_at IS NULL;

CREATE TABLE production_line (
    production_line_id BIGSERIAL PRIMARY KEY,
    production_id BIGINT NOT NULL REFERENCES production (production_id),
    pack_id BIGINT NOT NULL REFERENCES pack (pack_id),
    planned INT NOT NULL CHECK (planned >= 0),
    actual INT CHECK (actual >= 0),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX production_line_key ON production_line (production_id, pack_id) WHERE deleted_at IS NULL;

CREATE TABLE purchase (
    purchase_id BIGSERIAL PRIMARY KEY,
    production_id BIGINT REFERENCES production (production_id),
    supplier_id BIGINT NOT NULL REFERENCES supplier (supplier_id),
    status VARCHAR(10) NOT NULL CHECK (status IN ('pending','bought')),
    purchase_date DATE,
    total NUMERIC(12,2),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX purchase_production_supplier_key ON purchase (production_id, supplier_id) WHERE production_id IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE purchase_line (
    purchase_line_id BIGSERIAL PRIMARY KEY,
    purchase_id BIGINT NOT NULL REFERENCES purchase (purchase_id),
    supply_id BIGINT NOT NULL REFERENCES supply (supply_id),
    quantity NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    package_price NUMERIC(12,2) NOT NULL CHECK (package_price >= 0),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE transfer (
    transfer_id BIGSERIAL PRIMARY KEY,
    transfer_date DATE NOT NULL,
    origin_branch_id BIGINT NOT NULL REFERENCES branch (branch_id),
    destination_branch_id BIGINT NOT NULL REFERENCES branch (branch_id),
    notes VARCHAR(500),
    source_production_id BIGINT REFERENCES production (production_id),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (origin_branch_id <> destination_branch_id)
);

CREATE TABLE transfer_line (
    transfer_line_id BIGSERIAL PRIMARY KEY,
    transfer_id BIGINT NOT NULL REFERENCES transfer (transfer_id),
    production_id BIGINT NOT NULL REFERENCES production (production_id),
    pack_id BIGINT NOT NULL REFERENCES pack (pack_id),
    quantity INT NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE stock_movement (
    stock_movement_id BIGSERIAL PRIMARY KEY,
    movement_date DATE NOT NULL,
    branch_id BIGINT NOT NULL REFERENCES branch (branch_id),
    production_id BIGINT NOT NULL REFERENCES production (production_id),
    pack_id BIGINT NOT NULL REFERENCES pack (pack_id),
    quantity INT NOT NULL CHECK (quantity <> 0),
    type VARCHAR(20) NOT NULL CHECK (type IN ('production','transfer_in','transfer_out','sale','adjustment')),
    ref_id BIGINT,
    reason VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX stock_movement_lot ON stock_movement (branch_id, production_id, pack_id);

CREATE TABLE expense (
    expense_id BIGSERIAL PRIMARY KEY,
    expense_date DATE NOT NULL,
    type VARCHAR(60) NOT NULL,
    detail VARCHAR(200) NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    amount_packs NUMERIC(12,2),
    pack_price NUMERIC(12,2),
    method VARCHAR(20) NOT NULL CHECK (method IN ('transfer','cash','mercado_pago','other','packs')),
    paid BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(500),
    purchase_id BIGINT REFERENCES purchase (purchase_id),
    sale_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK ((amount_packs IS NULL) = (pack_price IS NULL))
);

-- sale_id gets its FK in block 4
CREATE UNIQUE INDEX expense_purchase_key ON expense (purchase_id) WHERE purchase_id IS NOT NULL AND deleted_at IS NULL;
