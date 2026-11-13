"""Safety regression tests: no live DB credential or network needed."""
import os
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch, call

from verify_isolated_restore import validate_target, validate_source, validate_output, pg_env, verify, REQUIRED_TABLES, parse_table_names


class IsolatedRestoreGuards(unittest.TestCase):
    def test_refuse_any_remote_destination(self):
        for host in ("flow-commerce.db.supabase.co", "10.10.0.2", "", "0.0.0.0", "db.internal"):
            with self.subTest(host=host), self.assertRaises(ValueError):
                validate_target(host, "flow_restore_ci")

    def test_requires_prefixed_disposable_local_database(self):
        for db in ("postgres", "orders", "production", "flow_commerce", "flow_restore_../bad", "flow_restore_"):
            with self.subTest(db=db), self.assertRaises(ValueError):
                validate_target("localhost", db)
        validate_target("127.0.0.1", "flow_restore_ci_01")

    def test_backup_never_overwrites_or_lives_inside_repo(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            repo = base / "checkout"
            repo.mkdir()
            private = base / "encrypted"
            private.mkdir()
            validate_output(private / "dump.backup", repo)
            with self.assertRaises(ValueError):
                validate_output(repo / "dump.backup", repo)
            existing = private / "previous.backup"
            existing.write_bytes(b"existing backup")
            with self.assertRaises(FileExistsError):
                validate_output(existing, repo)

    def test_missing_config_fails_closed(self):
        with patch.dict(os.environ, {
                "FLOW_BACKUP_SOURCE_PGHOST": "",
                "FLOW_BACKUP_SOURCE_PGPORT": "",
                "FLOW_BACKUP_SOURCE_PGDATABASE": "",
                "FLOW_BACKUP_SOURCE_PGUSER": ""}, clear=True):
            with self.assertRaises(ValueError):
                pg_env("FLOW_BACKUP_SOURCE_", "require")

    def test_ambient_pg_hostaddr_cannot_redirect_local_restore(self):
        with patch.dict(os.environ, {
                "PGHOSTADDR": "203.0.113.50", "PGSERVICE": "production",
                "PGSERVICEFILE": "/tmp/evil-services",
                "PGOPTIONS": "-c search_path=production",
                "PGTARGETSESSIONATTRS": "read-write",
                "FLOW_RESTORE_PGHOST": "localhost",
                "FLOW_RESTORE_PGPORT": "5432",
                "FLOW_RESTORE_PGDATABASE": "flow_restore_qa",
                "FLOW_RESTORE_PGUSER": "local",
                "FLOW_RESTORE_PGSSLMODE": "disable"
                }, clear=True):
            env = pg_env("FLOW_RESTORE_", "disable")
            self.assertEqual(env["PGHOST"], "localhost")
            self.assertEqual(env["PGDATABASE"], "flow_restore_qa")
            for key in ("PGHOSTADDR","PGSERVICE","PGSERVICEFILE","PGOPTIONS",
                        "PGTARGETSESSIONATTRS"):
                self.assertNotIn(key, env)

    def test_backup_source_cannot_be_local_or_unencrypted(self):
        sources = [
            {"PGHOST":"localhost","PGSSLMODE":"require","PGUSER":"reader"},
            {"PGHOST":"db.example.test","PGSSLMODE":"disable","PGUSER":"reader"},
            {"PGHOST":"db.example.test","PGSSLMODE":"require","PGUSER":"postgres"},
            {"PGHOST":"db.example.test","PGSSLMODE":"require","PGUSER":"postgres.project"},
            {"PGHOST":"db.example.test","PGSSLMODE":"require","PGUSER":"supabase_admin"},
        ]
        for source in sources:
            with self.subTest(source=source), self.assertRaises(ValueError):
                validate_source(source)
        validate_source({"PGHOST":"db.example.test","PGSSLMODE":"verify-full","PGUSER":"export_reader"})

    def test_source_sessions_are_forced_read_only(self):
        with patch.dict(os.environ, {
                "FLOW_BACKUP_SOURCE_PGHOST":"db.example.test",
                "FLOW_BACKUP_SOURCE_PGPORT":"5432",
                "FLOW_BACKUP_SOURCE_PGDATABASE":"commerce",
                "FLOW_BACKUP_SOURCE_PGUSER":"export_reader",
                "FLOW_BACKUP_SOURCE_PGSSLMODE":"require"
                }, clear=True):
            env = pg_env("FLOW_BACKUP_SOURCE_","require")
            self.assertEqual(env["PGOPTIONS"],"-c default_transaction_read_only=on")

    def test_privileged_source_is_denied_before_any_dump_or_restore(self):
        src={"PGHOST":"db.example.test","PGPORT":"5432","PGDATABASE":"commerce",
             "PGUSER":"export_reader","PGSSLMODE":"require"}
        dst={"PGHOST":"127.0.0.1","PGPORT":"5432","PGDATABASE":"flow_restore_ci"}
        with (patch("verify_isolated_restore.query",side_effect=["0","privileged"]) as q,
              patch("verify_isolated_restore.run") as run):
            with self.assertRaisesRegex(ValueError,"privilégios"):
                verify(Path("/tmp/nonexistent-backup.dump"),src,dst)
            self.assertEqual(q.call_count,2)
            run.assert_not_called()

    def test_real_schema_all_thirteen_required_tables(self):
        self.assertEqual(len(REQUIRED_TABLES), 13)
        self.assertIn("owner_mfa_recovery_codes", REQUIRED_TABLES)
        self.assertIn("owner_mfa_challenges", REQUIRED_TABLES)
        self.assertIn("owner_mfa", REQUIRED_TABLES)
        self.assertIn("auth_ip_rate_limits", REQUIRED_TABLES)

    def test_missing_mfa_table_is_rejected(self):
        listing = "\\n".join(sorted(REQUIRED_TABLES - {"owner_mfa"}))
        with self.assertRaisesRegex(RuntimeError, "obrigatórias"):
            parse_table_names(listing)

    def test_new_tables_are_also_verified_and_not_ignored(self):
        listing = "\\n".join(sorted(REQUIRED_TABLES | {"new_future_table"}))
        self.assertEqual(len(parse_table_names(listing)), 14)

    def test_catalog_rejects_sql_injection_like_names(self):
        listing = "\\n".join(sorted(REQUIRED_TABLES)) + "\\norders; DROP SCHEMA flow_commerce"
        with self.assertRaisesRegex(RuntimeError, "malformado"):
            parse_table_names(listing)

    def test_incomplete_source_schema_blocks_before_dump(self):
        src={"PGHOST":"db.example.test","PGPORT":"5432","PGDATABASE":"commerce",
             "PGUSER":"export_reader","PGSSLMODE":"require"}
        dst={"PGHOST":"127.0.0.1","PGPORT":"5432","PGDATABASE":"flow_restore_ci"}
        incomplete="\\n".join(sorted(REQUIRED_TABLES - {"auth_ip_rate_limits"}))
        with (patch("verify_isolated_restore.query",
                    side_effect=["0","readonly",incomplete]) as q,
              patch("verify_isolated_restore.run") as command):
            with self.assertRaisesRegex(RuntimeError,"obrigatórias"):
                verify(Path("/tmp/should-not-exist.dump"),src,dst)
            self.assertEqual(q.call_count,3)
            command.assert_not_called()

    def test_libhq_env_keeps_password_out_of_parameters(self):
        with patch.dict(os.environ, {
                "FLOW_RESTORE_PGHOST": "127.0.0.1",
                "FLOW_RESTORE_PGPORT": "5432",
                "FLOW_RESTORE_PGDATABASE": "flow_restore_ci",
                "FLOW_RESTORE_PGUSER": "app",
                "FLOW_RESTORE_PGPASSWORD": "not-for-logs",
                "FLOW_RESTORE_PGSSLMODE": "disable"
                }, clear=True):
            variables = pg_env("FLOW_RESTORE_", "disable")
            self.assertEqual(variables["PGHOST"], "127.0.0.1")
            self.assertEqual(variables["PGPASSWORD"], "not-for-logs")
            self.assertEqual(variables["PGSSLMODE"], "disable")


if __name__ == "__main__":
    unittest.main()
