-- V4: accelerate lookups by foreign keys in the dedicated private schema.
-- IF NOT EXISTS supports initial Supabase SQL bootstrap and Flyway hand-off.
CREATE INDEX IF NOT EXISTS ix_memberships_user_id ON flow_commerce.memberships(user_id);
CREATE INDEX IF NOT EXISTS ix_order_events_actor_id ON flow_commerce.order_events(actor_id);
CREATE INDEX IF NOT EXISTS ix_order_events_order_id ON flow_commerce.order_events(order_id);
CREATE INDEX IF NOT EXISTS ix_order_items_order_id ON flow_commerce.order_items(order_id);
CREATE INDEX IF NOT EXISTS ix_user_sessions_user_id ON flow_commerce.user_sessions(user_id);
