CREATE TABLE supplier (
    supplier_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    purchase_day VARCHAR(40),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE branch (
    branch_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    address VARCHAR(200) NOT NULL DEFAULT '',
    capacity INT NOT NULL CHECK (capacity >= 0),
    stock_threshold INT NOT NULL DEFAULT 0 CHECK (stock_threshold >= 0),
    free_slots_threshold INT NOT NULL DEFAULT 999 CHECK (free_slots_threshold >= 0),
    shipping_type VARCHAR(20) NOT NULL DEFAULT 'amount' CHECK (shipping_type IN ('amount','percentage')),
    shipping_value NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (shipping_value >= 0),
    affects_price BOOLEAN NOT NULL DEFAULT FALSE,
    is_kitchen BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX branch_one_kitchen ON branch (is_kitchen) WHERE is_kitchen AND deleted_at IS NULL;

CREATE TABLE reserve_fund (
    reserve_fund_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    percentage NUMERIC(5,2) NOT NULL CHECK (percentage >= 0),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE supply (
    supply_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    unit VARCHAR(2) NOT NULL CHECK (unit IN ('g','ml','u')),
    supplier_id BIGINT NOT NULL REFERENCES supplier (supplier_id),
    presentation VARCHAR(60),
    content NUMERIC(12,3) NOT NULL CHECK (content > 0),
    grams_per_unit NUMERIC(12,3) CHECK (grams_per_unit > 0),
    packaging_type VARCHAR(10) CHECK (packaging_type IN ('tray','sticker')),
    composite_rule VARCHAR(10) CHECK (composite_rule IN ('average','max')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX supply_name_key ON supply (lower(name)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX supply_packaging_key ON supply (packaging_type) WHERE packaging_type IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE supply_part (
    supply_part_id BIGSERIAL PRIMARY KEY,
    supply_id BIGINT NOT NULL REFERENCES supply (supply_id),
    part_id BIGINT NOT NULL REFERENCES supply (supply_id),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX supply_part_key ON supply_part (supply_id, part_id) WHERE deleted_at IS NULL;

CREATE TABLE supply_price (
    supply_price_id BIGSERIAL PRIMARY KEY,
    supply_id BIGINT NOT NULL REFERENCES supply (supply_id),
    price_date DATE NOT NULL,
    package_price NUMERIC(12,2) NOT NULL CHECK (package_price >= 0),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX supply_price_latest ON supply_price (supply_id, price_date DESC, supply_price_id DESC);

CREATE TABLE settings (
    settings_id BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (settings_id),
    pack_price NUMERIC(12,2) NOT NULL CHECK (pack_price > 0),
    oil_ml_per_pack NUMERIC(12,3) NOT NULL,
    oil_supply_id BIGINT REFERENCES supply (supply_id),
    trays_per_pack INT NOT NULL,
    stickers_per_pack INT NOT NULL,
    reference_overhead NUMERIC(12,2) NOT NULL,
    regular_months INT NOT NULL,
    recontact_days INT NOT NULL,
    price_rise_alert NUMERIC(5,4) NOT NULL,
    sale_reasons TEXT[] NOT NULL,
    expense_types TEXT[] NOT NULL CHECK ('fijo' = ANY (expense_types)),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE dish (
    dish_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE recipe_line (
    recipe_line_id BIGSERIAL PRIMARY KEY,
    dish_id BIGINT NOT NULL REFERENCES dish (dish_id),
    supply_id BIGINT NOT NULL REFERENCES supply (supply_id),
    quantity NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    unit VARCHAR(2) NOT NULL CHECK (unit IN ('g','ml','u')),
    portions NUMERIC(8,3) NOT NULL DEFAULT 1 CHECK (portions > 0),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE pack (
    pack_id BIGSERIAL PRIMARY KEY,
    number INT NOT NULL,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    price NUMERIC(12,2) CHECK (price > 0),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX pack_number_key ON pack (number) WHERE deleted_at IS NULL;

CREATE TABLE pack_version (
    pack_version_id BIGSERIAL PRIMARY KEY,
    pack_id BIGINT NOT NULL REFERENCES pack (pack_id),
    valid_from DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE pack_version_menu (
    pack_version_menu_id BIGSERIAL PRIMARY KEY,
    pack_version_id BIGINT NOT NULL REFERENCES pack_version (pack_version_id),
    position SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 5),
    menu_number INT NOT NULL CHECK (menu_number >= 1),
    dish_id BIGINT NOT NULL REFERENCES dish (dish_id),
    created_at TIMESTAMPTZ NOT NULL, created_by BIGINT NOT NULL REFERENCES app_user (user_id),
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL REFERENCES app_user (user_id),
    deleted_at TIMESTAMPTZ, deleted_by BIGINT REFERENCES app_user (user_id),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX pack_version_menu_key ON pack_version_menu (pack_version_id, position) WHERE deleted_at IS NULL;
