"""No-network, no-credential preflight tests."""
import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

spec = importlib.util.spec_from_file_location(
    "real_restore_preflight", Path(__file__).with_name("real_restore_preflight.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

def settings():
    return {
        "FLOW_BACKUP_SOURCE_PGHOST": "db.approved.example",
        "FLOW_BACKUP_SOURCE_PGPORT": "5432",
        "FLOW_BACKUP_SOURCE_PGUSER": "approved_reader",
        "FLOW_BACKUP_SOURCE_PGSSLMODE": "require",
        "FLOW_RESTORE_PGHOST": "127.0.0.1",
        "FLOW_RESTORE_PGPORT": "55432",
        "FLOW_RESTORE_PGDATABASE": "flow_restore_qa",
        "FLOW_RESTORE_PGSSLMODE": "disable",
    }

class RealRestorePreflightTests(unittest.TestCase):
    def setUp(self):
        self.tmp = TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name) / "repo"
        self.repo.mkdir()
        self.safe = Path(self.tmp.name) / "private"
        self.safe.mkdir()
        self.output = self.safe / "backup.dump"

    def evaluate(self, **changes):
        data = settings()
        data.update(changes)
        return module.inspect(data, self.output, self.repo)

    def test_good_structure_only(self):
        self.assertEqual(self.evaluate(), [])

    def test_remote_destination_rejected(self):
        self.assertTrue(self.evaluate(FLOW_RESTORE_PGHOST="db.remote.example"))

    def test_wrong_database_rejected(self):
        self.assertTrue(self.evaluate(FLOW_RESTORE_PGDATABASE="postgres"))

    def test_unsafe_port_rejected(self):
        for port in ("0", "65536", "abc", ""):
            with self.subTest(port=port):
                self.assertTrue(self.evaluate(FLOW_RESTORE_PGPORT=port))

    def test_source_must_use_tls(self):
        self.assertTrue(self.evaluate(FLOW_BACKUP_SOURCE_PGSSLMODE="disable"))

    def test_source_cannot_be_local(self):
        self.assertTrue(self.evaluate(FLOW_BACKUP_SOURCE_PGHOST="localhost"))

    def test_ambient_redirects_rejected(self):
        for key in module.RISKY_ENV:
            with self.subTest(key=key):
                self.assertTrue(self.evaluate(**{key: "unexpected"}))

    def test_existing_file_is_not_overwritten(self):
        self.output.write_bytes(b"test")
        self.assertTrue(self.evaluate())

    def test_repo_output_rejected(self):
        self.output = self.repo / "backup.dump"
        self.assertTrue(self.evaluate())

    def test_no_source_role_rejected(self):
        self.assertTrue(self.evaluate(FLOW_BACKUP_SOURCE_PGUSER=""))

if __name__ == "__main__":
    unittest.main()
