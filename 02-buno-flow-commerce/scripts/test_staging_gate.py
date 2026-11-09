"""Unit tests run offline, without reaching Railway or changing DB rows."""
import unittest
from unittest.mock import patch

import staging_gate


class StagingGateUnitTest(unittest.TestCase):
    def test_origin_rejects_insecure_urls(self):
        for text in ("http://example.com", "https://example.com/api",
                     "https://alice:secret@example.com", "https://example.com?q=1"):
            with self.subTest(text=text):
                with self.assertRaises(ValueError):
                    staging_gate.origin(text)

    def test_origin_accepts_clean_https(self):
        self.assertEqual("https://service.up.railway.app",
                         staging_gate.origin("https://service.up.railway.app/"))

    def test_gate_accepts_mocked_healthy_invite_only_api(self):
        calls = []
        def fake_fetch(url, method="GET", headers=None, body=None):
            calls.append((url, method))
            if url.endswith("/actuator/health"):
                return 200, {}, b'{"status":"UP"}'
            if method == "POST":
                return 403, {}, b'{}'
            if method == "OPTIONS":
                if headers["Origin"] == "https://frontend.vercel.app":
                    return 200, {"Access-Control-Allow-Origin": "https://frontend.vercel.app"}, b""
                return 403, {}, b""
            return 401, {"Cache-Control": "no-store"}, b""

        with patch("sys.argv", ["gate", "--api-origin", "https://api.up.railway.app",
                                  "--frontend-origin", "https://frontend.vercel.app"]), \
             patch.object(staging_gate, "fetch", side_effect=fake_fetch):
            self.assertEqual(0, staging_gate.main())
        self.assertGreaterEqual(len(calls), 9)

    def test_gate_fails_open_registration(self):
        def fake_fetch(url, method="GET", headers=None, body=None):
            if url.endswith("/actuator/health"):
                return 200, {}, b'{"status":"UP"}'
            if method == "POST":
                return 200, {}, b'{"accessToken":"bad"}'
            if method == "OPTIONS":
                if headers["Origin"] == "https://frontend.vercel.app":
                    return 200, {"Access-Control-Allow-Origin": "https://frontend.vercel.app"}, b""
                return 403, {}, b""
            return 401, {"Cache-Control": "no-store"}, b""

        with patch("sys.argv", ["gate", "--api-origin", "https://api.up.railway.app",
                                  "--frontend-origin", "https://frontend.vercel.app"]), \
             patch.object(staging_gate, "fetch", side_effect=fake_fetch):
            self.assertEqual(1, staging_gate.main())


if __name__ == "__main__":
    unittest.main()
