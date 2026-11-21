-- Operation IDs are consumed atomically with the protected status transition.
ALTER TABLE order_events ADD COLUMN IF NOT EXISTS operation_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS ix_order_events_operation ON order_events(operation_id)
  WHERE operation_id IS NOT NULL;
CREATE TABLE verify_operations (
  operation_id UUID PRIMARY KEY,
  store_id UUID NOT NULL REFERENCES stores(id),
  order_id UUID NOT NULL REFERENCES orders(id),
  expected_version BIGINT NOT NULL CHECK (expected_version>=0),
  consumed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_verify_operations_tenant ON verify_operations(store_id,order_id);
