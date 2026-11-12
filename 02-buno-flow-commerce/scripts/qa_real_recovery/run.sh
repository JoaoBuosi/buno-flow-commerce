#!/usr/bin/env bash
# Controlled one-shot E2E. NO production DB credentials. NO persistent volumes.
set -euo pipefail
umask 077

if [[ "${FLOW_QA_REAL_EMAIL_APPROVED:-}" != "yes" ]] || \
   [[ "${FLOW_QA_APPROVED_EMAIL:-}" != "joaovbuosi@gmail.com" ]] || \
   [[ -z "${RESEND_API_KEY:-}" ]]; then
  echo "QA BLOCKED: approval, mailbox, or Resend key not configured" >&2
  exit 2
fi

ROOT="$(mktemp -d -p /dev/shm buno-flow-e2e-XXXXXX)"
PGDIR="$ROOT/data"
cleanup() {
  if [[ -d "$PGDIR" ]]; then
    pg_ctl -D "$PGDIR" -m immediate -w stop >/dev/null 2>&1 || true
  fi
  rm -rf "$ROOT"
}
trap cleanup EXIT

echo "QA: initializing disposable PostgreSQL 17 in tmpfs (loopback only)"
initdb -D "$PGDIR" --no-sync --auth-local=trust --auth-host=trust >/dev/null
pg_ctl -D "$PGDIR" -l "$ROOT/postgres.log" \
  -o "-c listen_addresses=127.0.0.1 -c unix_socket_directories=$ROOT -c fsync=off -p 55432" \
  -w start >/dev/null
createdb -h 127.0.0.1 -p 55432 -U postgres flow_qa

# Always override any ambient production database variables, even if accidentally set.
export SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55432/flow_qa?currentSchema=flow_commerce'
export SPRING_DATASOURCE_USERNAME='postgres'
export SPRING_DATASOURCE_PASSWORD=''
export SPRING_PROFILES_ACTIVE=''
export FLOW_QA_DATABASE_HOST='127.0.0.1'

echo "QA: executing isolated Spring API + actual Resend delivery assertions"
mvn -q -B -Dtest=LiveResendRecoveryQaIT -DfailIfNoTests=true test
echo "QA: completed; all account records and reset tokens will be deleted with the tmpfs database"
