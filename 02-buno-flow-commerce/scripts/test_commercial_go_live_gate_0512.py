#!/usr/bin/env python3
"""Synthetic policy tests only; all links and timestamps are mock evidence."""
from datetime import datetime, timedelta, timezone
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    "commercial_go_live_gate_0512",Path(__file__).with_name("commercial_go_live_gate_0512.py")
)
gate=importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
NOW=datetime(2026,11,13,12,0,tzinfo=timezone.utc)
SHA="a"*40
URL="https://github.com/JoaoBuosi/buno-flow-commerce/issues/123"
DATE=NOW.isoformat()

def valid():
    return {
       "version":"0.5.12","scope":"paid_pilot","candidate_sha":SHA,
       "deployed_sha":SHA,"observed_at":DATE,
       "checks":{key:{"passed":True,"evidence_url":URL,"checked_at":DATE} for key in gate.REQUIRED},
       "approval":{"reviewed_by":"reviewer","reviewed_at":DATE,
                   "ticket_url":URL,"decision":"approved"}
    }
class GateTests(unittest.TestCase):
    def errors(self,manifest):
        return gate.validate(manifest,SHA,NOW)
    def test_structural_fixture_valid_not_real_authorization(self):
        self.assertEqual(self.errors(valid()),[])
    def test_missing_real_backup_blocks(self):
        data=valid();del data["checks"]["real_backup_restored_in_isolated_postgresql17"]
        self.assertTrue(self.errors(data))
    def test_missing_alert_delivery_blocks(self):
        data=valid();data["checks"]["delivered_outage_alert_acknowledged"]["passed"]=False
        self.assertTrue(self.errors(data))
    def test_missing_mail_delivery_blocks(self):
        data=valid();data["checks"]["real_password_reset_delivery_and_reuse_verified"]["passed"]=False
        self.assertTrue(self.errors(data))
    def test_mfa_vault_required(self):
        data=valid();del data["checks"]["mfa_key_rotated_and_vault_recovery_tested"]
        self.assertTrue(self.errors(data))
    def test_wrong_deployed_commit_blocks(self):
        data=valid();data["deployed_sha"]="b"*40
        self.assertTrue(self.errors(data))
    def test_wrong_expected_commit_blocks(self):
        self.assertTrue(gate.validate(valid(),"c"*40,NOW))
    def test_stale_review_blocks(self):
        data=valid();data["approval"]["reviewed_at"]=(NOW-timedelta(days=8)).isoformat()
        self.assertTrue(self.errors(data))
    def test_stale_check_blocks(self):
        data=valid();data["checks"]["tenant_isolation_in_target_environment"]["checked_at"]=(NOW-timedelta(days=31)).isoformat()
        self.assertTrue(self.errors(data))
    def test_future_manifest_blocks(self):
        data=valid();data["observed_at"]=(NOW+timedelta(days=1)).isoformat()
        self.assertTrue(self.errors(data))
    def test_non_https_blocks(self):
        data=valid();data["checks"]["backend_browser_sast_dast_ci_green"]["evidence_url"]="http://github.com/JoaoBuosi/buno-flow-commerce/issues/123"
        self.assertTrue(self.errors(data))
    def test_redirect_to_external_evidence_blocks(self):
        data=valid();data["approval"]["ticket_url"]="https://github.com.evil.test/JoaoBuosi/buno-flow-commerce/issues/123"
        self.assertTrue(self.errors(data))
    def test_evidence_with_secret_in_url_blocks(self):
        data=valid();data["approval"]["ticket_url"]=URL+"?token=fake"
        self.assertTrue(self.errors(data))
    def test_unexpected_payload_blocks(self):
        data=valid();data["checks"]["real_backup_restored_in_isolated_postgresql17"]["raw_dump"]="not-allowed"
        self.assertTrue(self.errors(data))
    def test_false_approval_blocks(self):
        data=valid();data["approval"]["decision"]="pending"
        self.assertTrue(self.errors(data))
    def test_missing_reviewer_blocks(self):
        data=valid();data["approval"]["reviewed_by"]=""
        self.assertTrue(self.errors(data))
    def test_naive_timestamp_blocks(self):
        data=valid();data["observed_at"]="2026-11-13T12:00:00"
        self.assertTrue(self.errors(data))
    def test_not_a_paid_pilot_blocks(self):
        data=valid();data["scope"]="test"
        self.assertTrue(self.errors(data))

if __name__=="__main__":
    unittest.main()
