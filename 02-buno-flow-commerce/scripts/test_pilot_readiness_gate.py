#!/usr/bin/env python3
"""Negative and positive tests of the no-secrets, fail-closed release gate."""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "gate", Path(__file__).with_name("pilot_readiness_gate.py"))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

def valid():
    return {
        "version": "0.5.9", "environment": "staging",
        "checks": {key: {"passed": True, "evidence_url": "https://github.com/example/evidence"}
                   for key in gate.REQUIRED},
        "approval": {"reviewed_by": "security-reviewer", "reviewed_at": "2026-11-12",
                     "ticket": "https://github.com/example/review"},
    }

class PilotReadinessGateTests(unittest.TestCase):
    def test_complete_structural_evidence(self):
        self.assertEqual(gate.validate(valid()), [])

    def test_missing_real_backup_restore_blocks(self):
        data = valid()
        del data["checks"]["backup_real_restore_isolated"]
        self.assertTrue(gate.validate(data))

    def test_failed_mfa_key_escrow_blocks(self):
        data = valid()
        data["checks"]["mfa_key_rotated_and_escrowed"]["passed"] = False
        self.assertTrue(gate.validate(data))

    def test_failed_recovery_email_blocks(self):
        data = valid()
        data["checks"]["password_reset_email_verified"]["passed"] = False
        self.assertTrue(gate.validate(data))

    def test_missing_independent_review_blocks(self):
        data = valid()
        data["checks"]["independent_pentest_reviewed"] = {"passed": True}
        self.assertTrue(gate.validate(data))

    def test_missing_human_approval_blocks(self):
        data = valid()
        data["approval"] = {}
        self.assertTrue(gate.validate(data))

    def test_bad_environment_blocks(self):
        data = valid()
        data["environment"] = "production"
        self.assertTrue(gate.validate(data))

    def test_secret_material_blocks(self):
        data = valid()
        data["smtp_password"] = "should-not-be-recorded"
        self.assertTrue(gate.validate(data))

    def test_non_https_evidence_blocks(self):
        data = valid()
        data["checks"]["ci_zap_authenticated"]["evidence_url"] = "http://localhost/report"
        self.assertTrue(gate.validate(data))

    def test_incomplete_mfa_recovery_blocks(self):
        data = valid()
        data["checks"]["mfa_totp_and_recovery"]["passed"] = False
        self.assertTrue(gate.validate(data))

    def test_unexpected_data_blocks(self):
        data = valid()
        data["checks"]["ci_backend_postgres"]["note"] = "extra"
        self.assertTrue(gate.validate(data))

if __name__ == "__main__":
    unittest.main()
