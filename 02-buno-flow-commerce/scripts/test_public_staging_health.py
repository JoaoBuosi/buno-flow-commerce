"""Pure offline tests; no calls to Vercel, Railway or a real account."""
from unittest import TestCase
from unittest.mock import patch
from check_public_staging_health import origin, check


class HealthCheckTests(TestCase):
    def test_accepts_only_specific_https_staging_hosts(self):
        self.assertEqual(origin("https://flow-commerce-api-staging.up.railway.app", "api"),
                         "https://flow-commerce-api-staging.up.railway.app")
        self.assertEqual(origin("https://buno-commerce-staging-abc.vercel.app", "frontend"),
                         "https://buno-commerce-staging-abc.vercel.app")
        for url, kind in [
            ("http://flow-commerce-api-staging.up.railway.app", "api"),
            ("https://localhost", "api"),
            ("https://some.example.com", "frontend"),
            ("https://flow-commerce-api-staging.up.railway.app:8443", "api"),
            ("https://user:password@x.up.railway.app", "api"),
            ("https://x.vercel.app/?token=secret", "frontend"),
        ]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                origin(url, kind)

    def test_verifies_railway_health_and_direct_api_denial(self):
        with patch("check_public_staging_health.probe", side_effect=[
            (200,b'{"status":"UP"}'), (403,b""), (401,b"")
        ]) as probe:
            result = check("https://a.up.railway.app", "https://b.vercel.app")
        self.assertEqual(len(result),3)
        self.assertTrue(all(status for _,status in result))
        self.assertEqual(probe.call_count,3)

    def test_detects_broken_health_without_masking(self):
        with patch("check_public_staging_health.probe", side_effect=[
            (200,b'{"status":"DOWN"}'), (200,b"{}"), (200,b"")
        ]):
            result = check("https://a.up.railway.app", "https://b.vercel.app")
        self.assertEqual([ok for _,ok in result],[False,False,True])
