import os
import unittest
from unittest.mock import patch
from durable_encrypted_backup import APPROVAL, requirements, source_env

class DurableEncryptedBackupPolicyTest(unittest.TestCase):
    def setup_config(self):
        return {
            "FLOW_BACKUP_APPROVAL": APPROVAL,
            "FLOW_BACKUP_SOURCE_PGHOST": "db.example.supabase.co",
            "FLOW_BACKUP_SOURCE_PGDATABASE": "postgres",
            "FLOW_BACKUP_SOURCE_PGUSER": "backup_readonly",
            "FLOW_BACKUP_SOURCE_PGPASSWORD": "fake-ci-only",
            "FLOW_BACKUP_AGE_RECIPIENT": "age1" + "q" * 58,
            "FLOW_BACKUP_S3_PREFIX": "s3://example-private-backups/flow-commerce",
            "AWS_ACCESS_KEY_ID": "fake-ci-only",
            "AWS_SECRET_ACCESS_KEY": "fake-ci-only",
        }

    def test_explicit_approval_and_encrypted_destination_are_required(self):
        self.assertEqual(
            ("example-private-backups", "flow-commerce", "age1" + "q" * 58),
            requirements(self.setup_config())
        )
        for absent in ("FLOW_BACKUP_APPROVAL", "FLOW_BACKUP_AGE_RECIPIENT",
                       "FLOW_BACKUP_S3_PREFIX", "AWS_SECRET_ACCESS_KEY"):
            data = self.setup_config()
            data.pop(absent)
            with self.subTest(absent=absent), self.assertRaises(ValueError):
                requirements(data)

    def test_refuses_non_supabase_source_and_path_traversal(self):
        data=self.setup_config()
        data["FLOW_BACKUP_SOURCE_PGHOST"]="attacker.invalid"
        with self.assertRaises(ValueError):
            requirements(data)
        data=self.setup_config()
        data["FLOW_BACKUP_S3_PREFIX"]="s3://example-private-backups/../../unsafe"
        with self.assertRaises(ValueError):
            requirements(data)

    def test_readonly_postgres_and_tls_even_with_ambient_overrides(self):
        data=self.setup_config()
        with patch.dict(os.environ, {"PGHOSTADDR":"192.0.2.7","PGOPTIONS":"-c default_transaction_read_only=off"}, clear=False):
            source=source_env(data)
        self.assertNotIn("PGHOSTADDR",source)
        self.assertEqual(source["PGOPTIONS"],"-c default_transaction_read_only=on")
        self.assertEqual(source["PGSSLMODE"],"require")
        self.assertEqual(source["PGHOST"],"db.example.supabase.co")
        data["FLOW_BACKUP_SOURCE_PGSSLMODE"]="disable"
        with self.assertRaises(ValueError):
            source_env(data)

if __name__=="__main__":
    unittest.main()
