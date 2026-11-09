CREATE TABLE order_imports(
  store_id UUID NOT NULL REFERENCES stores(id),
  external_key VARCHAR(100) NOT NULL,
  order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
  imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(store_id,external_key)
);
