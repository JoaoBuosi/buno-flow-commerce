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

-- Scoped service tokens are provisioned out-of-band, and only their hashes are stored.
CREATE TABLE verify_service_credentials (
  token_hash CHAR(64) PRIMARY KEY,
  store_id UUID NOT NULL REFERENCES stores(id),
  user_id UUID NOT NULL REFERENCES users(id),
  scope VARCHAR(40) NOT NULL CHECK (scope='ORDER_PROCESSING_ONLY'),
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ
);
CREATE INDEX ix_verify_service_credentials_store ON verify_service_credentials(store_id,expires_at);
