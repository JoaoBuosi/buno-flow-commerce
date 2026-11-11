-- Flow Commerce 0.5.3 — SELECT-only PostgreSQL integrity probe.
-- Run exclusively with an authorized read-only database role.
-- No row-level personal data is returned; outputs are aggregate counts.
-- A zero result in each column is necessary, but not sufficient, for go-live.
SELECT
  (SELECT count(*) FROM flow_commerce.orders o
    LEFT JOIN flow_commerce.stores s ON s.id=o.store_id
    WHERE s.id IS NULL) AS orders_without_store,
  (SELECT count(*) FROM flow_commerce.order_items i
    LEFT JOIN flow_commerce.orders o ON o.id=i.order_id
    WHERE o.id IS NULL) AS items_without_order,
  (SELECT count(*) FROM flow_commerce.order_events e
    LEFT JOIN flow_commerce.orders o ON o.id=e.order_id
    WHERE o.id IS NULL) AS events_without_order,
  (SELECT count(*) FROM flow_commerce.order_events e
    JOIN flow_commerce.orders o ON o.id=e.order_id
    WHERE e.store_id <> o.store_id) AS event_cross_tenant_mismatches,
  (SELECT count(*) FROM flow_commerce.order_imports i
    JOIN flow_commerce.orders o ON o.id=i.order_id
    WHERE i.store_id <> o.store_id) AS import_cross_tenant_mismatches,
  (SELECT count(*) FROM flow_commerce.memberships m
    LEFT JOIN flow_commerce.stores s ON s.id=m.store_id
    WHERE s.id IS NULL) AS memberships_without_store,
  (SELECT count(*) FROM flow_commerce.memberships m
    LEFT JOIN flow_commerce.users u ON u.id=m.user_id
    WHERE u.id IS NULL) AS memberships_without_user,
  (SELECT count(*) FROM flow_commerce.user_sessions s
    LEFT JOIN flow_commerce.users u ON u.id=s.user_id
    WHERE u.id IS NULL) AS sessions_without_user,
  (SELECT count(*) FROM pg_constraint c
    JOIN pg_namespace n ON n.oid=c.connamespace
    WHERE n.nspname='flow_commerce' AND c.contype='f'
      AND NOT c.convalidated) AS unvalidated_foreign_keys;
