"""Synthetic monitoring and alert-gate regression tests. No network."""
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("monitor_alert_gate.py")
spec = importlib.util.spec_from_file_location("monitor_alert_gate", SCRIPT)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

def sample():
    return {"environment": "disposable", "checks": {
        "api_health": {"http_status": 200, "latency_ms": 120},
        "anonymous_api_denied": {"http_status": 403, "latency_ms": 60},
        "frontend_reachable": {"http_status": 401, "latency_ms": 90},
    }}

class MonitorGateTests(unittest.TestCase):
    def test_success(self):
        self.assertEqual(gate.evaluate(sample()), [])

    def test_health_down_alerts(self):
        x = sample()
        x["checks"]["api_health"]["http_status"] = 503
        self.assertTrue(gate.evaluate(x))

    def test_auth_bypass_alerts(self):
        x = sample()
        x["checks"]["anonymous_api_denied"]["http_status"] = 200
        self.assertTrue(gate.evaluate(x))

    def test_frontend_outage_alerts(self):
        x = sample()
        x["checks"]["frontend_reachable"]["http_status"] = 502
        self.assertTrue(gate.evaluate(x))

    def test_latency_slo_alerts(self):
        x = sample()
        x["checks"]["api_health"]["latency_ms"] = 6000
        self.assertTrue(gate.evaluate(x))

    def test_missing_probe_fails_closed(self):
        x = sample()
        del x["checks"]["anonymous_api_denied"]
        self.assertTrue(gate.evaluate(x))

    def test_unexpected_field_fails_closed(self):
        x = sample()
        x["checks"]["api_health"]["response_body"] = "not allowed"
        self.assertTrue(gate.evaluate(x))

    def test_wrong_environment_fails_closed(self):
        x = sample()
        x["environment"] = "production"
        self.assertTrue(gate.evaluate(x))

    def test_bool_is_not_http_status(self):
        x = sample()
        x["checks"]["api_health"]["http_status"] = True
        self.assertTrue(gate.evaluate(x))

    def test_cli_blocked_on_sensitive_field(self):
        x = sample()
        x["token"] = "should-never-store"
        with tempfile.TemporaryDirectory() as td:
            evidence = Path(td) / "report.json"
            evidence.write_text(json.dumps(x), encoding="utf-8")
            result = subprocess.run([sys.executable, str(SCRIPT), str(evidence)],
                                    text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 2)
        self.assertNotIn("should-never-store", result.stdout)

    def test_cli_passes_clean_sample(self):
        with tempfile.TemporaryDirectory() as td:
            evidence = Path(td) / "report.json"
            evidence.write_text(json.dumps(sample()), encoding="utf-8")
            result = subprocess.run([sys.executable, str(SCRIPT), str(evidence)],
                                    text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 0)

if __name__ == "__main__":
    unittest.main()
