#!/usr/bin/env python3
"""Copy flow_commerce from an approved PostgreSQL source to a *local empty* restore database.

SAFETY:
- NEVER performs SQL writes on the source; uses only pg_dump.
- Destination MUST be loopback and named flow_restore_*, with NO user tables.
- Does not create databases, delete schemas, log secrets or overwrite backup files.
- Requires explicit --execute and uses libpq environment variables, not DSNs in args.
- Backup holds personal data. Use an encrypted private filesystem and delete it securely.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

# These critical structures must exist, but ALL source tables are discovered and
# compared so new migrations cannot silently escape restore verification.
REQUIRED_TABLES = frozenset((
    "stores", "users", "memberships", "orders", "order_items", "order_events",
    "order_imports", "user_sessions", "password_resets",
    "auth_ip_rate_limits", "owner_mfa", "owner_mfa_challenges",
    "owner_mfa_recovery_codes",
))
IDENTIFIER = re.compile(r"[a-z_][a-z0-9_]*\\Z")
LIST_TABLES_SQL = """
SELECT tablename FROM pg_catalog.pg_tables
WHERE schemaname = 'flow_commerce' ORDER BY tablename
"""

def parse_table_names(raw: str) -> tuple[str, ...]:
    """Validate source/destination catalog identifiers, never interpolate untrusted SQL."""
    names = tuple(name.strip() for name in raw.splitlines())
    if (not names or len(names) != len(set(names)) or
            any(not IDENTIFIER.fullmatch(name) for name in names)):
        raise RuntimeError("Catálogo de tabelas vazio, malformado ou com nomes não seguros")
    if not REQUIRED_TABLES.issubset(names):
        raise RuntimeError("Tabelas obrigatórias de pedidos, acesso ou MFA ausentes do backup")
    return names

TARGET_RE = re.compile(r"^flow_restore_[a-z0-9_]{1,48}$")
LOOPBACK = {"127.0.0.1", "localhost", "::1"}


def validate_target(host: str, database: str) -> None:
    if host not in LOOPBACK:
        raise ValueError("A restauração só pode usar PostgreSQL LOCAL em 127.0.0.1/localhost/::1.")
    if not TARGET_RE.fullmatch(database):
        raise ValueError("Banco de destino deve começar com flow_restore_ e conter somente letras minúsculas/números/_.")


def validate_source(source: dict[str, str]) -> None:
    host = source.get("PGHOST", "")
    if not host or host in LOOPBACK or host in ("0.0.0.0", "::"):
        raise ValueError("Origem de exportação deve ser remota e explicitamente aprovada.")
    if source.get("PGSSLMODE") not in ("require", "verify-ca", "verify-full"):
        raise ValueError("Origem exige TLS com PGSSLMODE=require/verify-ca/verify-full.")
    user = source.get("PGUSER", "").lower()
    if not user or user == "postgres" or user.startswith("postgres.") or user in ("supabase_admin", "service_role"):
        raise ValueError("Origem deve usar uma role de exportação dedicada, nunca uma conta administrativa.")

def validate_output(path: Path, workdir: Path) -> None:
    target = path.expanduser().resolve()
    repo = workdir.resolve()
    if repo == target or repo in target.parents:
        raise ValueError("O dump contém dados sensíveis: use pasta PRIVADA fora do repositório.")
    if target.exists():
        raise FileExistsError("O arquivo de backup já existe. Não sobrescrever.")
    if not target.parent.is_dir():
        raise ValueError("A pasta de backup precisa existir e ser privada.")


def pg_env(prefix: str, default_ssl: str) -> dict[str, str]:
    env = os.environ.copy()
    # libpq can route to an entirely different remote server via PGHOSTADDR
    # even when PGHOST is localhost. Drop ambient overrides before building
    # the explicitly approved source/destination connection.
    for unsafe in ("PGHOSTADDR", "PGSERVICE", "PGSERVICEFILE", "PGOPTIONS",
                   "PGTARGETSESSIONATTRS", "PGREQUIREAUTH", "PGCHANNELBINDING"):
        env.pop(unsafe, None)
    for name in ("PGHOST", "PGPORT", "PGDATABASE", "PGUSER", "PGPASSWORD"):
        value = os.getenv(prefix + name, "").strip()
        if not value and name != "PGPASSWORD":
            raise ValueError("Configuração incompleta: " + prefix + name)
        if value:
            env[name] = value
        elif name == "PGPASSWORD":
            # PGPASSFILE or libpq service can supply password without shell history.
            env.pop(name, None)
    env["PGSSLMODE"] = os.getenv(prefix + "PGSSLMODE", default_ssl)
    env["PGCONNECT_TIMEOUT"] = "12"
    if prefix == "FLOW_BACKUP_SOURCE_":
        # Force source sessions read-only independently of their login defaults.
        # The authorization query below also rejects roles with write privileges.
        env["PGOPTIONS"] = "-c default_transaction_read_only=on"
    return env


def run(args: list[str], env: dict[str, str], timeout: int = 120) -> str:
    try:
        result = subprocess.run(args, env=env, text=True, capture_output=True, timeout=timeout, check=False)
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        raise RuntimeError("Ferramenta PostgreSQL ausente ou operação excedeu o limite") from None
    if result.returncode:
        # Never show upstream error text; pg utilities may reveal hostnames or sensitive context.
        raise RuntimeError("Comando PostgreSQL falhou; revise permissões e conectividade em ambiente seguro")
    return result.stdout.strip()


def query(sql: str, env: dict[str, str]) -> str:
    return run(["psql", "-X", "-q", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-c", sql], env, timeout=45)


def verify(output: Path, source: dict[str, str], destination: dict[str, str]) -> None:
    validate_target(destination["PGHOST"], destination["PGDATABASE"])
    validate_source(source)
    if (source["PGHOST"] == destination["PGHOST"] and
        source["PGPORT"] == destination["PGPORT"] and
        source["PGDATABASE"] == destination["PGDATABASE"]):
        raise ValueError("O destino não pode ser a origem.")
    ready = query("""
      SELECT count(*) FROM pg_catalog.pg_tables
       WHERE schemaname NOT IN ('pg_catalog','information_schema')
    """, destination)
    if ready != "0":
        raise ValueError("O banco de destino contém tabelas; ABORTADO antes da restauração.")

    # Refuse privileged source roles, including table owners and inherited write
    # privileges. The operator must create an approved export-only role.
    readonly = query("""
      SELECT CASE WHEN
        NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles
                    WHERE rolname = current_user
                      AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication))
        AND NOT has_schema_privilege(current_user, 'flow_commerce', 'CREATE')
        AND NOT EXISTS (
          SELECT 1 FROM pg_catalog.pg_tables
           WHERE schemaname = 'flow_commerce'
             AND has_table_privilege(current_user,
                  quote_ident(schemaname)||'.'||quote_ident(tablename),
                  'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
        )
      THEN 'readonly' ELSE 'privileged' END
    """, source)
    if readonly != "readonly":
        raise ValueError("Role de exportação tem privilégios de escrita/administração; ABORTADO antes do dump.")

    source_tables = parse_table_names(query(LIST_TABLES_SQL, source))

    # File is created exclusively with private permissions and is never replaced.
    fd = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    os.close(fd)
    try:
        run(["pg_dump", "--format=custom", "--schema=flow_commerce",
             "--no-owner", "--no-acl", "--file", str(output)], source, timeout=900)
        if output.stat().st_size == 0:
            raise RuntimeError("Backup vazio; restauração bloqueada.")
        run(["pg_restore", "--exit-on-error", "--single-transaction",
             "--no-owner", "--no-acl", "--dbname", destination["PGDATABASE"],
             str(output)], destination, timeout=900)
        target_tables = parse_table_names(query(LIST_TABLES_SQL, destination))
        if source_tables != target_tables:
            raise RuntimeError("Conjunto de tabelas da restauração difere da origem")
        for table in source_tables:
            # Identifiers are verified against IDENTIFIER by parse_table_names,
            # preventing SQL injection via unexpected catalog identifiers.
            sql = "SELECT count(*) FROM flow_commerce." + table
            origin_count = query(sql, source)
            target_count = query(sql, destination)
            if not origin_count.isdigit() or origin_count != target_count:
                raise RuntimeError("Contagem divergente na tabela " + table)
        # Table names and business record counts need not appear in operational logs.
        print("Conferido o conjunto completo de tabelas e suas contagens; sem dados de clientes nos logs.")
        bad_foreign_keys = query("""
          SELECT
            (SELECT count(*) FROM flow_commerce.orders o LEFT JOIN flow_commerce.stores s
               ON s.id=o.store_id WHERE s.id IS NULL) +
            (SELECT count(*) FROM flow_commerce.order_items i LEFT JOIN flow_commerce.orders o
               ON o.id=i.order_id WHERE o.id IS NULL)
        """, destination)
        if bad_foreign_keys != "0":
            raise RuntimeError("Restauração com chaves estrangeiras inconsistentes.")
        print("APROVADO: backup lógico verificado em PostgreSQL local isolado. NÃO é teste de failover automático.")
        print("AVISO: o dump contém dados de contas e pedidos; mantenha criptografado e exclua conforme política aprovada.")
    except Exception:
        # Failed backups are not evidence of recovery. Do not silently delete a
        # completed backup with personal data; surface its location to the operator.
        print("NÃO HOMOLOGADO: operação interrompida. Verifique e proteja o dump sensível.", file=sys.stderr)
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description="Valida backup/restauração segura de flow_commerce num banco PostgreSQL LOCAL e VAZIO.")
    parser.add_argument("--output", type=Path, required=True, help="Caminho absoluto em armazenamento privado e cifrado, FORA do repo")
    parser.add_argument("--execute", action="store_true", help="Confirma execução somente com destino local/isolado")
    args = parser.parse_args()
    if not args.execute:
        print("MODO DE INSPEÇÃO: nenhum backup/restauração iniciado. Acrescente --execute depois de configurar origem/destino.")
        return 0
    try:
        validate_output(args.output, Path.cwd())
        source = pg_env("FLOW_BACKUP_SOURCE_", "require")
        dest = pg_env("FLOW_RESTORE_", "disable")
        verify(args.output.expanduser().resolve(), source, dest)
    except (ValueError, RuntimeError, FileExistsError, PermissionError) as exc:
        print("BLOQUEADO: " + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
