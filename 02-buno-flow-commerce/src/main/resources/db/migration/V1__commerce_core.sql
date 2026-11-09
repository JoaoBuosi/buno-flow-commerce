CREATE TABLE stores (
  id UUID PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE users (
  id UUID PRIMARY KEY,
  email VARCHAR(254) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE memberships (
  store_id UUID NOT NULL REFERENCES stores(id),
  user_id UUID NOT NULL REFERENCES users(id),
  role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER','STAFF')),
  PRIMARY KEY (store_id,user_id)
);
CREATE TABLE user_sessions (
  token_hash VARCHAR(64) PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE orders (
  id UUID PRIMARY KEY,
  store_id UUID NOT NULL REFERENCES stores(id),
  customer_name VARCHAR(120) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED','PAID','PROCESSING','SHIPPED','DELIVERED','CANCELED')),
  total NUMERIC(19,2) NOT NULL CHECK (total > 0),
  cancellation_reason VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_orders_tenant_time ON orders(store_id,created_at DESC);
CREATE TABLE order_items (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  sku VARCHAR(80) NOT NULL,
  description VARCHAR(200) NOT NULL,
  quantity INTEGER NOT NULL CHECK(quantity>0),
  unit_price NUMERIC(19,2) NOT NULL CHECK(unit_price>0),
  subtotal NUMERIC(19,2) NOT NULL CHECK(subtotal>0)
);
CREATE TABLE order_events (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id),
  store_id UUID NOT NULL REFERENCES stores(id),
  actor_id UUID NOT NULL REFERENCES users(id),
  action VARCHAR(50) NOT NULL,
  old_status VARCHAR(20),
  new_status VARCHAR(20),
  detail VARCHAR(500),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_events_tenant_order ON order_events(store_id,order_id,occurred_at);
