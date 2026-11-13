"""CSP security regression test: no permissive inline CSS needed by React."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / "web"


class FrontendCspTest(unittest.TestCase):
    def test_no_inline_styles_allowed_in_policy(self):
        config = json.loads((WEB / "vercel.json").read_text(encoding="utf-8"))
        policy = [
            header["value"] for entry in config["headers"]
            for header in entry["headers"]
            if header["key"].lower() == "content-security-policy"
        ]
        self.assertEqual(1, len(policy))
        directives = dict((item.strip().split(maxsplit=1) + [""])[:2]
                          for item in policy[0].split(";") if item.strip())
        self.assertEqual("'self'", directives.get("style-src"))
        self.assertEqual("'self'", directives.get("script-src"))
        self.assertNotIn("unsafe-inline", policy[0])
        self.assertNotIn("unsafe-eval", policy[0])

    def test_react_source_uses_css_class_not_inline_style(self):
        src = (WEB / "src" / "main.tsx").read_text(encoding="utf-8")
        self.assertNotRegex(src, r"\bstyle=\s*\{\{")
        self.assertNotRegex(src, r"<style\b")
        css = (WEB / "src" / "style.css").read_text(encoding="utf-8")
        self.assertIn(".forecastHeading{margin-top:12px}", css)
        self.assertIn('className="forecastHeading"', src)


if __name__ == "__main__":
    unittest.main()
