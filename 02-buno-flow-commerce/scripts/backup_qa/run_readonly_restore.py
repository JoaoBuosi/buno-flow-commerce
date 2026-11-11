#!/usr/bin/env python3
"""One-shot real restore QA: source SELECT-only; ephemeral PostgreSQL 17 on RAM-backed tmpfs.

Secrets stay in Railway environment variables; neither dump nor restored rows are logged,
uploaded as artifacts, or stored on a persistent disk. Do not enable on public services.
"""
from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit

import verify_isolated_restore as restore

PORT = "55432"
DB_NAME = "flow_restore_qa"


def local_run(argv: list[str], timeout: int = 90) -> None:
    try:
        completed = subprocess.run(argv, check=False, text=True, capture_output=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        raise RuntimeError("Could not initialize the isolated PostgreSQL server") from None
    if completed.returncode != 0:
        # Avoid showing raw database commands or environment details.
        raise RuntimeError("Isolated PostgreSQL operation failed") from None


def source_from_railway() -> None:
    url = os.environ.get("SPRING_DATASOURCE_URL", "").strip()
    if not url.startswith("jdbc:postgresql://"):
        raise RuntimeError("Expected PostgreSQL JDBC source URL")
    parsed = urlsplit(url[5:])
    host = (parsed.hostname or "").lower()
    if not (host.endswith(".supabase.co") or host.endswith(".supabase.com")):
        raise RuntimeError("Origin must be the approved hosted Supabase PostgreSQL")
    if not parsed.path or parsed.path == "/":
        raise RuntimeError("Database name missing from source")
    username = os.environ.get("SPRING_DATASOURCE_USERNAME", "").strip()
    password = os.environ.get("SPRING_DATASOURCE_PASSWORD", "")
    if not username or not password:
        raise RuntimeError("Source database credentials not available in Railway runtime")
    os.environ.update({
        "FLOW_BACKUP_SOURCE_PGHOST": host,
        "FLOW_BACKUP_SOURCE_PGPORT": str(parsed.port or 5432),
        "FLOW_BACKUP_SOURCE_PGDATABASE": parsed.path.removeprefix("/"),
        "FLOW_BACKUP_SOURCE_PGUSER": username,
        "FLOW_BACKUP_SOURCE_PGPASSWORD": password,
        "FLOW_BACKUP_SOURCE_PGSSLMODE": "require",
    })


def restore_config() -> None:
    os.environ.update({
        "FLOW_RESTORE_PGHOST": "127.0.0.1",
        "FLOW_RESTORE_PGPORT": PORT,
        "FLOW_RESTORE_PGDATABASE": DB_NAME,
        "FLOW_RESTORE_PGUSER": "postgres",
        "FLOW_RESTORE_PGSSLMODE": "disable",
    })
    os.environ.pop("FLOW_RESTORE_PGPASSWORD", None)


def main() -> int:
    if not os.path.isdir("/dev/shm"):
        print("BLOQUEADO: RAM-backed scratch space unavailable", file=sys.stderr)
        return 1
    root = Path(tempfile.mkdtemp(prefix="flow-restore-qa-", dir="/dev/shm"))
    root.chmod(0o700)
    data_dir = root / "pgdata"
    socket_dir = root / "socket"
    socket_dir.mkdir(mode=0o700)
    started = False
    try:
        print("QA: preparing loopback PostgreSQL 17 with RAM-backed data", flush=True)
        source_from_railway()
        restore_config()
        local_run(["initdb", "-D", str(data_dir), "--no-sync",
                   "--auth-local=trust", "--auth-host=trust"], timeout=100)
        local_run(["pg_ctl", "-D", str(data_dir), "-l", str(root / "postgres.log"), "-o",
                   f"-c listen_addresses=127.0.0.1 -c unix_socket_directories={socket_dir} -p {PORT} -c fsync=off",
                   "-w", "start"], timeout=100)
        started = True
        local_run(["createdb", "-h", "127.0.0.1", "-p", PORT,
                   "-U", "postgres", DB_NAME], timeout=45)

        print("QA: PostgreSQL local started; checking read-only source and empty destination", flush=True)
        source = restore.pg_env("FLOW_BACKUP_SOURCE_", "require")
        # Prevent writes even if the source credential is more privileged than necessary.
        source["PGOPTIONS"] = "-c default_transaction_read_only=on"
        target = restore.pg_env("FLOW_RESTORE_", "disable")
        dump_path = root / "flow-commerce-real.dump"
        restore.validate_output(dump_path, Path.cwd())
        restore.verify(dump_path, source, target)
        print("QA: transient PostgreSQL 17 restore completed; dump and restored rows remain in RAM only.")
        print("QA: this proves restore procedure, NOT encrypted durable backup retention.")
        return 0
    except Exception:
        print("NÃO HOMOLOGADO: revisão dos logs agregados e permissões necessária.", file=sys.stderr)
        return 1
    finally:
        if started:
            try:
                local_run(["pg_ctl", "-D", str(data_dir), "-m", "immediate",
                           "-w", "stop"], timeout=30)
            except RuntimeError:
                print("Aviso: banco transitório encerrará com o container.", file=sys.stderr)
        shutil.rmtree(root, ignore_errors=True)
        for key in list(os.environ):
            if key.startswith("FLOW_BACKUP_SOURCE_") or key.startswith("FLOW_RESTORE_"):
                os.environ.pop(key, None)


if __name__ == "__main__":
    raise SystemExit(main())
