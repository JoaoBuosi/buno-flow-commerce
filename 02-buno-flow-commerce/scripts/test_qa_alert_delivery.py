"""QA local-alert delivery tests; never makes network requests."""
import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

spec = importlib.util.spec_from_file_location(
    "qa_alert_delivery", Path(__file__).with_name("qa_alert_delivery.py"))
# normal module lookup is managed by unittest discovery
import qa_alert_delivery as delivery

def report(status=200):
    return {"environment": "disposable", "checks": {
        "api_health": {"http_status": status, "latency_ms": 100},
        "anonymous_api_denied": {"http_status": 403, "latency_ms": 50},
        "frontend_reachable": {"http_status": 401, "latency_ms": 80},
    }}

class QaAlertDeliveryTests(unittest.TestCase):
    def test_success_has_no_alert(self):
        self.assertEqual(delivery.build_alerts(report()), [])

    def test_api_outage_emits_one_sanitized_event(self):
        with TemporaryDirectory() as td:
            spool = Path(td) / "events.jsonl"
            self.assertEqual(delivery.emit(report(503), spool, authorized=True), 1)
            lines = spool.read_text(encoding="utf-8").splitlines()
            self.assertEqual(json.loads(lines[0]), {"type": "api_unavailable", "status": "alert"})
            self.assertEqual(len(lines), 1)

    def test_auth_bypass_emits_incident(self):
        x = report()
        x["checks"]["anonymous_api_denied"]["http_status"] = 200
        self.assertEqual(delivery.build_alerts(x), [{"type": "anonymous_access_violation", "status": "alert"}])

    def test_frontend_outage_emits_incident(self):
        x = report()
        x["checks"]["frontend_reachable"]["http_status"] = 502
        self.assertEqual(delivery.build_alerts(x), [{"type": "frontend_unavailable", "status": "alert"}])

    def test_unauthorized_spool_blocked(self):
        with TemporaryDirectory() as td, self.assertRaises(ValueError):
            delivery.emit(report(503), Path(td) / "events.jsonl", authorized=False)

    def test_real_environment_blocked(self):
        x = report(503)
        x["environment"] = "production"
        with TemporaryDirectory() as td, self.assertRaises(ValueError):
            delivery.emit(x, Path(td) / "events.jsonl", authorized=True)

    def test_existing_spool_never_overwritten(self):
        with TemporaryDirectory() as td:
            spool = Path(td) / "events.jsonl"
            spool.write_text("EXISTING", encoding="utf-8")
            with self.assertRaises(ValueError):
                delivery.emit(report(503), spool, authorized=True)
            self.assertEqual(spool.read_text(encoding="utf-8"), "EXISTING")

    def test_relative_path_blocked(self):
        with self.assertRaises(ValueError):
            delivery.emit(report(), Path("events.jsonl"), authorized=True)

    def test_alerts_include_no_report_body(self):
        x = report(503)
        x["unexpected_sensitive_information"] = "private-qa-string"
        serialized = json.dumps(delivery.build_alerts(x))
        self.assertNotIn("private-qa-string", serialized)

    def test_multiple_failures_redacted_and_deduplicated(self):
        x = report(503)
        x["checks"]["anonymous_api_denied"]["http_status"] = 200
        labels = {e["type"] for e in delivery.build_alerts(x)}
        self.assertEqual(labels, {"api_unavailable", "anonymous_access_violation"})

if __name__ == "__main__":
    unittest.main()
