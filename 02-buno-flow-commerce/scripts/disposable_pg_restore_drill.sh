#!/usr/bin/env bash
# Synthetic PostgreSQL backup-and-restore drill; never connects to remote databases.
set -Eeuo pipefail
if [[ "${CI:-}" != "true" || "${FLOW_DISPOSABLE_RESTORE:-}" != "explicit-local-only" ]]; then
  echo "BLOCKED: CI and explicit disposable-only consent required" >&2
  exit 2
fi
if [[ -n "${DATABASE_URL:-}" || -n "${PGHOST:-}" || -n "${PGSERVICE:-}" ]]; then
  echo "BLOCKED: external database connection settings present" >&2
  exit 2
fi
name="flow-059-restore-${GITHUB_RUN_ID:-local}"
name="${name//[^a-zA-Z0-9_-]/-}"
cleanup() { docker rm -f "$name" >/dev/null 2>&1 || :; }
trap cleanup EXIT
docker pull postgres:17-alpine >/dev/null
docker run -d --name "$name" --network none -e POSTGRES_PASSWORD=synthetic-ci-only \
  -e POSTGRES_DB=synthetic_source postgres:17-alpine
if ! docker exec "$name" sh -c 'test -x /usr/local/bin/docker-entrypoint.sh'; then
  echo "FAIL: disposable container did not initialize" >&2
  docker logs --tail 35 "$name" >&2 || :
  exit 1
fi
ready=0
for i in $(seq 1 45); do
  if docker exec "$name" psql -X -q -A -t -v ON_ERROR_STOP=1 -U postgres -d synthetic_source -c 'SELECT 1' 2>/dev/null | grep -qx 1; then
    ready=1
    break
  fi
  sleep 1
done
if [[ "$ready" != 1 ]]; then
  echo "FAIL: disposable synthetic_source database did not become queryable" >&2
  docker logs --tail 25 "$name" >&2 || :
  exit 1
fi
docker exec -i "$name" psql -v ON_ERROR_STOP=1 -U postgres -d synthetic_source <<'SQL' >/dev/null
CREATE SCHEMA flow_commerce;
CREATE TABLE flow_commerce.synthetic_orders (id integer PRIMARY KEY, status text NOT NULL);
INSERT INTO flow_commerce.synthetic_orders VALUES (1,'CREATED'),(2,'PAID'),(3,'SHIPPED');
SQL
# Archive remains inside container; no exported dumps or persistent volumes.
docker exec "$name" pg_dump -U postgres -d synthetic_source -Fc -f /tmp/restore-drill.dump
docker exec "$name" createdb -U postgres synthetic_restore
docker exec "$name" pg_restore -U postgres -d synthetic_restore --no-owner --no-acl \
  --exit-on-error /tmp/restore-drill.dump
source_count="$(docker exec "$name" psql -At -U postgres -d synthetic_source \
  -c 'SELECT count(*) FROM flow_commerce.synthetic_orders')"
restore_count="$(docker exec "$name" psql -At -U postgres -d synthetic_restore \
  -c 'SELECT count(*) FROM flow_commerce.synthetic_orders')"
source_digest="$(docker exec "$name" psql -At -U postgres -d synthetic_source \
  -c "SELECT md5(string_agg(id::text || ':' || status, ',' ORDER BY id)) FROM flow_commerce.synthetic_orders")"
restore_digest="$(docker exec "$name" psql -At -U postgres -d synthetic_restore \
  -c "SELECT md5(string_agg(id::text || ':' || status, ',' ORDER BY id)) FROM flow_commerce.synthetic_orders")"
if [[ "$source_count" != "3" || "$restore_count" != "3" || "$source_digest" != "$restore_digest" ]]; then
  echo "FAIL: synthetic restore verification mismatch" >&2
  exit 1
fi
echo "PASS: disposable PostgreSQL 17 pg_dump/pg_restore, schema, row count and digest match"
echo "LIMIT: synthetic data only; real Supabase backup remains untested"
