-- Auth abuse protection: anonymous IP hashes only, no raw IP addresses and no email addresses.
-- Buckets are shared by every Spring replica in this database.
CREATE TABLE IF NOT EXISTS flow_commerce.auth_ip_rate_limits (
  ip_hash VARCHAR(64) NOT NULL,
  route_family VARCHAR(24) NOT NULL,
  window_started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  requests INTEGER NOT NULL CHECK (requests > 0),
  PRIMARY KEY (ip_hash, route_family)
);
CREATE INDEX IF NOT EXISTS ix_auth_ip_rate_limits_window
  ON flow_commerce.auth_ip_rate_limits(window_started_at);
