import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from zap_report_gate import check_report, summarize


class ZapReportGateTest(unittest.TestCase):
    def report(self, folder, *, risk="1", host="127.0.0.1", port="5173"):
        path = Path(folder) / "zap.json"
        path.write_text(json.dumps({
            "site": [{"@host": host, "@port": port,
                      "alerts": [{"riskcode": risk, "pluginid": "10020",
                                  "name": "Test signal", "instances": [{}]}]}]
        }), encoding="utf-8")
        return path

    def test_low_does_not_fail_but_is_reported(self):
        with TemporaryDirectory() as temp:
            path = self.report(temp)
            summary_path = Path(temp) / "summary.md"
            with patch.dict("os.environ", {"GITHUB_STEP_SUMMARY": str(summary_path)}):
                self.assertEqual(0, summarize([path]))
            self.assertIn("Test signal", summary_path.read_text())
            self.assertIn("Low", summary_path.read_text())

    def test_high_blocks(self):
        with TemporaryDirectory() as temp:
            path = self.report(temp, risk="3")
            with patch.dict("os.environ", {"GITHUB_STEP_SUMMARY": str(Path(temp) / "summary.md")}):
                self.assertEqual(1, summarize([path]))

    def test_external_targets_block(self):
        with TemporaryDirectory() as temp:
            path = self.report(temp, host="example.com")
            with self.assertRaisesRegex(ValueError, "unapproved"):
                check_report(path)

    def test_authenticated_port_requires_explicit_scope(self):
        with TemporaryDirectory() as temp:
            path = self.report(temp, port="8080")
            with self.assertRaisesRegex(ValueError, "unapproved"):
                check_report(path)
            self.assertEqual(1, check_report(path, expected_port="8080")["sites"])

    def test_unknown_risk_blocks(self):
        with TemporaryDirectory() as temp:
            path = self.report(temp, risk="critical-unparsed")
            with self.assertRaisesRegex(ValueError, "risk code"):
                check_report(path)

    def test_missing_or_empty_report_blocks(self):
        with TemporaryDirectory() as temp:
            with self.assertRaises(ValueError):
                check_report(Path(temp) / "missing.json")
            path = Path(temp) / "empty.json"
            path.write_text('{"site":[]}', encoding="utf-8")
            with self.assertRaises(ValueError):
                check_report(path)


if __name__ == "__main__":
    unittest.main()
