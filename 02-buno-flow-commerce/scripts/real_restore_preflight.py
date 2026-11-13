#!/usr/bin/env python3
"""Read-only preflight for a supervised, local PostgreSQL restore.

Never opens a database connection, performs a backup or prints credentials.
A passing preflight does NOT establish that a local port is not an SSH tunnel,
nor that the backup directory is encrypted. Operator must verify both.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import sys

PORT = re.compile(r"[0-9]{1,5}\Z")
DB = re.compile(r"flow_restore_[a-z0-9_]{1,48}\Z")
LOOPBACK = {"127.0.0.1", "::1", "localhost"}
RISKY_ENV = ("PGHOSTADDR", "PGSERVICE", "PGSERVICEFILE", "PGPASSFILE", "PGOPTIONS")

def inspect(config: dict[str, str], output: Path, repo: Path) -> list[str]:
    errors: list[str] = []
    for variable in RISKY_ENV:
        if config.get(variable):
            errors.append("Ambient libpq override requires removal: " + variable)
    if config.get("FLOW_RESTORE_PGHOST") not in LOOPBACK:
        errors.append("Destination must be a local loopback host")
    if not DB.fullmatch(config.get("FLOW_RESTORE_PGDATABASE", "")):
        errors.append("Destination must be an explicitly named flow_restore_* database")
    port = config.get("FLOW_RESTORE_PGPORT", "")
    if not PORT.fullmatch(port) or not (1 <= int(port) <= 65535):
        errors.append("Destination port must be in 1..65535")
    if config.get("FLOW_RESTORE_PGSSLMODE", "disable") != "disable":
        errors.append("Local destination must use the reviewed non-SSL mode")
    source_host = config.get("FLOW_BACKUP_SOURCE_PGHOST", "")
    if not source_host or source_host in LOOPBACK:
        errors.append("Source must be separately approved and non-local")
    if config.get("FLOW_BACKUP_SOURCE_PGSSLMODE", "require") not in ("require", "verify-ca", "verify-full"):
        errors.append("Source must require TLS")
    if not config.get("FLOW_BACKUP_SOURCE_PGUSER"):
        errors.append("Source must use an approved read-only role")
    if config.get("FLOW_BACKUP_SOURCE_PGHOST") == config.get("FLOW_RESTORE_PGHOST") and config.get("FLOW_BACKUP_SOURCE_PGPORT") == port:
        errors.append("Source and destination share an endpoint")
    try:
        out = output.expanduser().resolve(strict=False)
        root = repo.resolve(strict=True)
        if root == out or root in out.parents:
            errors.append("Backup destination cannot be inside repository")
        if out.exists():
            errors.append("Output file already exists; never overwrite")
        if not out.parent.is_dir():
            errors.append("Backup parent directory is absent")
    except (OSError, RuntimeError):
        errors.append("Invalid backup location")
    return errors

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    reasons = inspect(dict(os.environ), args.output, args.repo)
    for error in reasons:
        print("BLOCKED: " + error)
    if reasons:
        return 1
    print("PRECHECK PASS: parameters structurally eligible; no network or dump operation executed")
    print("MANUAL: verify encryption, no localhost port-forward, source read-only role and empty local DB")
    return 0

if __name__ == "__main__":
    sys.exit(main())
