#!/usr/bin/env python3
"""Encrypted, durable Flow Commerce backup to an S3 bucket with Object Lock.

Opt-in only. No credentials in source, args, URLs, logs or GitHub Actions.
The dump is produced read-only and encrypted by age before touching disk.
It is NOT an automated backup service until a real private bucket, credentials,
retention policy, scheduled job and restore drill are configured and verified.
"""
from __future__ import annotations

import argparse
import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import uuid

AGE_KEY_RE = re.compile(r"^age1[023456789acdefghjklmnpqrstuvwxyz]{58}$")
S3_RE = re.compile(r"^s3://([a-z0-9][a-z0-9.-]{2,62})/([a-zA-Z0-9/_-]{1,180})/?$")
PG_HOST_RE = re.compile(r"^[a-z0-9.-]+$")
APPROVAL = "approved-encrypted-s3-backup"

def requirements(config: dict[str, str]) -> tuple[str, str, str]:
    if config.get("FLOW_BACKUP_APPROVAL") != APPROVAL:
        raise ValueError("Aprovação de backup não fornecida")
    host = config.get("FLOW_BACKUP_SOURCE_PGHOST", "").lower()
    if not PG_HOST_RE.fullmatch(host) or not (
        host.endswith(".supabase.co") or host.endswith(".supabase.com")
    ):
        raise ValueError("Origem precisa ser o PostgreSQL Supabase aprovado")
    dbname = config.get("FLOW_BACKUP_SOURCE_PGDATABASE", "")
    if not re.fullmatch(r"[a-zA-Z0-9_]{1,63}", dbname):
        raise ValueError("Nome de banco inválido")
    if not config.get("FLOW_BACKUP_SOURCE_PGUSER") or not config.get("FLOW_BACKUP_SOURCE_PGPASSWORD"):
        raise ValueError("Credenciais de origem ausentes")
    recipient = config.get("FLOW_BACKUP_AGE_RECIPIENT", "")
    if not AGE_KEY_RE.fullmatch(recipient):
        raise ValueError("Chave pública age inválida")
    uri = config.get("FLOW_BACKUP_S3_PREFIX", "")
    match = S3_RE.fullmatch(uri)
    if not match or ".." in match.group(2) or "//" in match.group(2):
        raise ValueError("Prefixo privado S3 inválido")
    if not config.get("AWS_ACCESS_KEY_ID") or not config.get("AWS_SECRET_ACCESS_KEY"):
        raise ValueError("Credenciais S3 ausentes")
    return match.group(1), match.group(2).rstrip("/"), recipient

def command(args: list[str], *, env: dict[str, str] | None = None) -> str:
    completed = subprocess.run(args, env=env, capture_output=True, text=True, timeout=90, check=False)
    if completed.returncode:
        raise RuntimeError("Falha na verificação/transferência do storage (detalhes protegidos)")
    return completed.stdout

def validate_s3(bucket: str) -> None:
    versioning = json.loads(command(["aws", "s3api", "get-bucket-versioning", "--bucket", bucket]))
    if versioning.get("Status") != "Enabled":
        raise ValueError("Bucket sem versionamento habilitado")
    lock = json.loads(command(["aws", "s3api", "get-object-lock-configuration", "--bucket", bucket]))
    config = lock.get("ObjectLockConfiguration") or {}
    if config.get("ObjectLockEnabled") != "Enabled" or not (config.get("Rule") or {}).get("DefaultRetention"):
        raise ValueError("Bucket sem retenção imutável Object Lock configurada")

def source_env(config: dict[str, str]) -> dict[str, str]:
    env = dict(os.environ)
    for key in ("PGHOSTADDR", "PGSERVICE", "PGSERVICEFILE", "PGOPTIONS", "PGPASSFILE"):
        env.pop(key, None)
    for key in ("PGHOST", "PGPORT", "PGDATABASE", "PGUSER", "PGPASSWORD"):
        val = config.get("FLOW_BACKUP_SOURCE_" + key, "")
        if val:
            env[key] = val
    env["PGSSLMODE"] = config.get("FLOW_BACKUP_SOURCE_PGSSLMODE", "require")
    if env["PGSSLMODE"] not in {"require", "verify-ca", "verify-full"}:
        raise ValueError("TLS obrigatório para origem")
    env["PGOPTIONS"] = "-c default_transaction_read_only=on"
    env["PGCONNECT_TIMEOUT"] = "10"
    return env

def execute() -> None:
    config = dict(os.environ)
    bucket, prefix, recipient = requirements(config)
    validate_s3(bucket)
    with tempfile.TemporaryDirectory(prefix="buno-encrypted-backup-") as tmp:
        directory = Path(tmp)
        directory.chmod(0o700)
        cipher = directory / "flow_commerce.dump.age"
        with cipher.open("xb") as output:
            pg = subprocess.Popen(
                ["pg_dump", "--format=custom", "--schema=flow_commerce",
                 "--no-owner", "--no-acl", "--no-password", "--file", "-"],
                env=source_env(config), stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            )
            try:
                encrypted = subprocess.Popen(
                    ["age", "-r", recipient],
                    stdin=pg.stdout, stdout=output, stderr=subprocess.DEVNULL,
                )
                assert pg.stdout is not None
                pg.stdout.close()
                encrypted_result = encrypted.wait(timeout=900)
                pg_result = pg.wait(timeout=900)
                if encrypted_result != 0 or pg_result != 0:
                    raise RuntimeError("Falha no dump ou na criptografia")
            finally:
                if pg.poll() is None:
                    pg.kill()
                    pg.wait()
        if cipher.stat().st_size < 100:
            raise RuntimeError("Artefato criptografado inválido")
        key = f"{prefix}/{datetime.datetime.now(datetime.timezone.utc):%Y/%m/%d}/flow-{uuid.uuid4().hex}.dump.age"
        command(["aws", "s3", "cp", "--only-show-errors", str(cipher), f"s3://{bucket}/{key}"])
        meta = json.loads(command(["aws", "s3api", "head-object", "--bucket", bucket, "--key", key]))
        if int(meta.get("ContentLength", -1)) != cipher.stat().st_size:
            raise RuntimeError("Cópia remota divergente; NÃO homologado")
        print("BACKUP_ENCRYPTED_UPLOAD_CONFIRMED: artefato age cifrado, destino versionado, retenção Object Lock ativa")
        print("ATENÇÃO: é preciso restaurar um artefato real em PostgreSQL isolado para homologar o ciclo completo")

def main() -> int:
    parser = argparse.ArgumentParser(description="Backup criptografado e imutável de flow_commerce")
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    if not args.execute:
        print("DRY RUN: nenhum dump ou upload iniciado. Configurar storage e aprovação antes do --execute.")
        return 0
    try:
        execute()
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as exc:
        print("BACKUP_NOT_HOMOLOGATED: " + type(exc).__name__, file=sys.stderr)
        return 1
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
