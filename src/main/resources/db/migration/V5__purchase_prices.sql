-- Price rows a purchase added; annulling the purchase soft-deletes them.
ALTER TABLE supply_price ADD COLUMN purchase_id BIGINT REFERENCES purchase (purchase_id);
CREATE INDEX supply_price_purchase ON supply_price (purchase_id) WHERE purchase_id IS NOT NULL;
