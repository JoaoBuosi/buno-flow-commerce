#!/usr/bin/env bash
# Isolated one-time QA of OWNER MFA on a disposable loopback PostgreSQL 17.
# Does not accept database credentials and does not contact any Buno service.
set -euo pipefail
umask 077
if [[ "${FLOW_MFA_QA_APPROVED:-}" != "isolated-only" ]]; then
  echo "QA_BLOCKED: isolated-run approval missing" >&2
  exit 2
fi
if [[ -n "${SPRING_DATASOURCE_URL:-}" || -n "${SPRING_DATASOURCE_PASSWORD:-}" ]]; then
  echo "QA_BLOCKED: inherited DB credentials not allowed" >&2
  exit 2
fi
ROOT="$(mktemp -d -p /tmp buno-owner-mfa-XXXXXX)"
PGDIR="$ROOT/pgdata"
app_pid=""
cleanup(){
  if [[ -n "$app_pid" ]]; then kill "$app_pid" >/dev/null 2>&1 || true; wait "$app_pid" >/dev/null 2>&1 || true; fi
  if [[ -d "$PGDIR" ]]; then pg_ctl -D "$PGDIR" -m immediate -w stop >/dev/null 2>&1 || true; fi
  rm -rf "$ROOT"
}
trap cleanup EXIT
echo "QA_STAGE=postgres_isolation"
initdb -D "$PGDIR" --no-sync --auth-local=trust --auth-host=trust >/dev/null
pg_ctl -D "$PGDIR" -l "$ROOT/pg.log" \
  -o "-c listen_addresses=127.0.0.1 -c unix_socket_directories=$ROOT -c fsync=off -p 55434" \
  -w start >/dev/null
createdb -h 127.0.0.1 -p 55434 -U postgres flow_mfa_qa
export PGHOST=127.0.0.1 PGPORT=55434 PGUSER=postgres PGDATABASE=flow_mfa_qa
export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:55434/flow_mfa_qa?currentSchema=flow_commerce"
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=''
export SPRING_PROFILES_ACTIVE=''
export COMMERCE_OWNER_MFA_REQUIRED=true
export COMMERCE_IP_RATE_LIMIT_ENABLED=false
export COMMERCE_MAIL_ENABLED=false
export COMMERCE_PILOT_ONLY=false
export COMMERCE_PILOT_INVITE_CODE=''
export COMMERCE_EDGE_REQUIRED=false
export PORT=8099
export SERVER_ADDRESS=127.0.0.1
# Ephemeral key resides solely in this isolated process and its child Java process.
# No secret is printed, persisted in the repo, or sent to another service.
export COMMERCE_MFA_ENCRYPTION_KEY="$(python3 -c 'import base64,secrets;print(base64.b64encode(secrets.token_bytes(32)).decode())')"
echo "QA_STAGE=java_boot_migrations"
java -jar /opt/qa/app.jar > "$ROOT/app.log" 2>&1 &
app_pid=$!
healthy=0
for attempt in $(seq 1 90); do
  if ! kill -0 "$app_pid" 2>/dev/null; then break; fi
  if python3 -c 'import urllib.request;u=urllib.request.urlopen("http://127.0.0.1:8099/actuator/health",timeout=2);import sys;sys.exit(0 if u.status==200 else 1)' >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 1
done
if [[ "$healthy" != 1 ]]; then
  echo "QA_FAIL=backend_not_healthy_or_migration_failed" >&2
  exit 1
fi
# Confirm Flyway has applied V6 in the actual database before testing API.
migrated="$(psql -XAt -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM flow_commerce.flyway_schema_history WHERE version='6' AND success=true;")"
[[ "$migrated" == "1" ]] || { echo "QA_FAIL=flyway_V6_not_applied" >&2; exit 1; }
tables="$(psql -XAt -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='flow_commerce' AND table_name IN ('owner_mfa','owner_mfa_challenges','owner_mfa_recovery_codes');")"
[[ "$tables" == "3" ]] || { echo "QA_FAIL=mfa_tables_missing" >&2; exit 1; }
echo "QA_PASS=flyway_V6_three_tables"
python3 /opt/qa/verify_owner_mfa.py
echo "QA_RESULT=PASS_disposable_mfa_api_and_v6"
