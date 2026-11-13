#!/usr/bin/env python3
"""Isolated authenticated DAST through the OWASP ZAP proxy.

Runs only in GitHub Actions against 127.0.0.1:8080. All users, passwords, TOTP
secrets, stores and orders are synthetic. Nothing sensitive is printed or saved.
ZAP stores HTTP exchanges only in its ephemeral process; artifacts contain
sanitized rule names/severity and pass/fail labels, NEVER request/response data.
"""
from __future__ import annotations

import base64
from collections import defaultdict
import hashlib
import hmac
import http.client
import json
import os
from pathlib import Path
import secrets
import struct
import sys
import time
import urllib.parse
import urllib.request

API = "http://127.0.0.1:8080"
ZAP_HOST = "127.0.0.1"
ZAP_PORT = 8090
PORT = 8080
OUT = Path("zap-artifacts")
RESULTS = []


def guarantee(value, label):
    if not value:
        raise AssertionError("FAIL: " + label)


def note(label, status):
    RESULTS.append({"check": label, "status": "PASS", "http": status})
    print("PASS:", label, "HTTP", status, flush=True)


def via_zap(method, path, payload=None, *, bearer=None, store=None):
    guarantee(path.startswith("/api/v1/") and "://" not in path and
              "?" not in path and "#" not in path, "strict local API allowlist")
    guarantee(method in ("GET", "POST", "PATCH"), "unexpected HTTP method")
    headers = {
        "Host": "127.0.0.1:8080",
        "Accept": "application/json",
        "User-Agent": "Buno-DAST-Isolated/0.5.8",
        "Cache-Control": "no-store",
        "Connection": "close",
    }
    if payload is not None:
        headers["Content-Type"] = "application/json"
    if bearer:
        headers["Authorization"] = "Bearer " + bearer
    if store:
        headers["X-Store-Id"] = store
    data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode()
    conn = http.client.HTTPConnection(ZAP_HOST, ZAP_PORT, timeout=20)
    try:
        # Absolute URL form forces this request through the local ZAP HTTP proxy.
        conn.request(method, API + path, body=data, headers=headers)
        response = conn.getresponse()
        code = response.status
        body = response.read(150_000)
        content_type = response.getheader("Content-Type", "")
        if code >= 400:
            # Error bodies from filters/proxies may be blank or non-JSON.
            # Negative tests assert HTTP status, never body text or error shape.
            parsed = {}
        elif "application/json" in content_type:
            if not body:
                raise AssertionError("FAIL: successful JSON response was empty")
            try:
                parsed = json.loads(body)
            except (ValueError, UnicodeDecodeError):
                raise AssertionError("FAIL: invalid successful JSON response")
        else:
            parsed = body.decode("utf-8", errors="replace")
        return code, parsed
    finally:
        conn.close()


def exact(label, expected, method, path, data=None, *, bearer=None, store=None):
    status, parsed = via_zap(method, path, data, bearer=bearer, store=store)
    guarantee(status == expected, label + f" expected={expected} actual={status}")
    note(label, status)
    return parsed


def denied(label, method, path, data=None, *, bearer=None, store=None):
    status, parsed = via_zap(method, path, data, bearer=bearer, store=store)
    guarantee(status in (401, 403, 404), label + f" expected denial got={status}")
    note(label, status)
    return parsed


def totp(base32):
    raw = base64.b32decode(base32, casefold=True)
    step = int(time.time()) // 30
    digest = hmac.new(raw, struct.pack(">Q", step), hashlib.sha1).digest()
    p = digest[-1] & 0xf
    number = (struct.unpack(">I", digest[p:p + 4])[0] & 0x7fffffff) % 1000000
    return f"{number:06d}"


def zap_json(path):
    url = f"http://{ZAP_HOST}:{ZAP_PORT}/JSON/" + path
    with urllib.request.urlopen(url, timeout=10) as r:
        return json.load(r)


def wait_passive_queue():
    for _ in range(90):
        current = zap_json("pscan/view/recordsToScan/")
        pending = int(current.get("recordsToScan", -1))
        if pending == 0:
            return
        time.sleep(2)
    raise AssertionError("FAIL: ZAP passive scan queue did not finish")


def sanitized_alerts():
    grouped = defaultdict(int)
    start = 0
    while True:
        params = urllib.parse.urlencode({
            "baseurl": API,
            "start": start,
            "count": 100,
        })
        values = zap_json("core/view/alerts/?" + params)
        alerts = values.get("alerts")
        guarantee(isinstance(alerts, list), "ZAP alerts missing")
        for alert in alerts:
            plugin = str(alert.get("pluginId") or alert.get("pluginid") or "unknown")
            risk = str(alert.get("riskcode", ""))
            guarantee(risk in ("0", "1", "2", "3"), "unknown ZAP severity")
            name = str(alert.get("name", "Unnamed alert"))[:120]
            # Discard URLs, query, headers, evidence, messages, user/store/tokens.
            grouped[(plugin, risk, name)] += 1
        if len(alerts) < 100:
            break
        start += 100
        guarantee(start <= 5000, "unexpectedly large ZAP report")

    sanitized = [{
        "riskcode": risk,
        "pluginid": plugin,
        "name": name,
        "instances": [{} for _ in range(count)],
    } for (plugin, risk, name), count in sorted(grouped.items())]
    return {"site": [{"@host": ZAP_HOST, "@port": str(PORT),
                      "alerts": sanitized}]}


def run():
    guarantee(os.getenv("CI") == "true" and
              os.getenv("DAST_AUTH_LOCAL_ONLY") == "explicit-disposable-only",
              "CI/authorization gate")
    guarantee(os.getenv("SPRING_DATASOURCE_URL", "").startswith(
        "jdbc:postgresql://127.0.0.1:5432/commerce_auth_dast?"),
        "disposable database guard")
    OUT.mkdir(parents=True, exist_ok=True)
    # Never use fixed/real identities or credentials in the evidence.
    serial = secrets.token_hex(10)
    password_a = secrets.token_urlsafe(24)
    password_b = secrets.token_urlsafe(24)
    email_a = f"owner-a-{serial}@example.invalid"
    email_b = f"owner-b-{serial}@example.invalid"

    a = exact("owner_A_register_synthetic", 200, "POST", "/api/v1/auth/register",
              {"email": email_a, "password": password_a, "storeName": "DAST Store A"})
    b = exact("owner_B_register_synthetic", 200, "POST", "/api/v1/auth/register",
              {"email": email_b, "password": password_b, "storeName": "DAST Store B"})
    guarantee("accessToken" in a and "accessToken" in b, "initial normal owners sessions")
    token_a, store_a = a["accessToken"], a["storeId"]
    token_b, store_b = b["accessToken"], b["storeId"]
    guarantee(store_a != store_b, "different stores")

    exact("owner_A_self", 200, "GET", "/api/v1/me", bearer=token_a, store=store_a)
    exact("owner_B_self", 200, "GET", "/api/v1/me", bearer=token_b, store=store_b)
    denied("anonymous_orders_denied", "GET", "/api/v1/orders")
    denied("anonymous_MFA_status_denied", "GET", "/api/v1/auth/mfa/status")
    denied("cross_tenant_A_token_B_store_denied", "GET", "/api/v1/me",
           bearer=token_a, store=store_b)
    denied("cross_tenant_B_token_A_store_denied", "GET", "/api/v1/orders",
           bearer=token_b, store=store_a)

    item = [{"sku": "DAST-SKU", "description": "Synthetic CI product",
             "quantity": 1, "price": 13.50}]
    order_a = exact("owner_A_creates_synthetic_order", 201, "POST", "/api/v1/orders",
                    {"customer": "DAST-SYNTHETIC-A", "items": item},
                    bearer=token_a, store=store_a)
    order_b = exact("owner_B_creates_synthetic_order", 201, "POST", "/api/v1/orders",
                    {"customer": "DAST-SYNTHETIC-B", "items": item},
                    bearer=token_b, store=store_b)
    id_a, id_b = order_a["id"], order_b["id"]
    guarantee(id_a != id_b, "different orders")
    own_a = exact("owner_A_own_order_read", 200, "GET", "/api/v1/orders/" + id_a,
                  bearer=token_a, store=store_a)
    guarantee(own_a.get("customer_name") == "DAST-SYNTHETIC-A", "A own order identity")
    denied("B_cannot_read_A_order_IDOR", "GET", "/api/v1/orders/" + id_a,
           bearer=token_b, store=store_b)
    denied("A_cannot_read_B_order_IDOR", "GET", "/api/v1/orders/" + id_b,
           bearer=token_a, store=store_a)
    denied("B_cannot_read_A_order_history", "GET", "/api/v1/orders/" + id_a + "/history",
           bearer=token_b, store=store_b)
    denied("B_cannot_edit_A_order", "PATCH", "/api/v1/orders/" + id_a + "/status",
           {"status": "PAID"}, bearer=token_b, store=store_b)
    own_list_a = exact("A_list_only_A_orders", 200, "GET", "/api/v1/orders",
                       bearer=token_a, store=store_a)
    own_list_b = exact("B_list_only_B_orders", 200, "GET", "/api/v1/orders",
                       bearer=token_b, store=store_b)
    guarantee(len(own_list_a) == 1 and own_list_a[0]["id"] == id_a,
              "tenant A listing leaks no B orders")
    guarantee(len(own_list_b) == 1 and own_list_b[0]["id"] == id_b,
              "tenant B listing leaks no A orders")
    exact("A_order_unmodified", 200, "GET", "/api/v1/orders/" + id_a,
          bearer=token_a, store=store_a)
    note("tenant_isolation_and_denial_assertions", 200)

    state = exact("owner_A_pre_MFA_status", 200, "GET", "/api/v1/auth/mfa/status",
                  bearer=token_a, store=store_a)
    guarantee(state["enrollmentAvailable"] is True and state["enrolled"] is False,
              "self-enrollment only for disposable CI")
    denied("owner_A_wrong_password_cannot_enable_MFA", "POST",
           "/api/v1/auth/mfa/self/enroll", {"currentPassword": "incorrect-synthetic"},
           bearer=token_a, store=store_a)
    pending = exact("owner_A_reauth_starts_MFA", 200, "POST",
                    "/api/v1/auth/mfa/self/enroll", {"currentPassword": password_a},
                    bearer=token_a, store=store_a)
    guarantee(pending["mfaRequired"] is True and "accessToken" not in pending,
              "MFA setup challenge is not a bearer token")
    challenge = pending["challengeToken"]
    denied("MFA_challenge_cannot_access_orders", "GET", "/api/v1/orders",
           bearer=challenge, store=store_a)
    setup = exact("owner_A_TOTP_setup", 200, "POST", "/api/v1/auth/mfa/enroll/start",
                  {"challengeToken": challenge})
    code = totp(setup["secret"])
    enrolled = exact("owner_A_TOTP_activation", 200, "POST",
                     "/api/v1/auth/mfa/enroll/complete",
                     {"challengeToken": challenge, "code": code})
    guarantee(len(enrolled["recoveryCodes"]) == 8, "eight recovery codes")
    verified_a = enrolled["accessToken"]
    denied("old_A_session_revoked_after_MFA_activation", "GET", "/api/v1/me",
           bearer=token_a, store=store_a)
    exact("A_verified_session_can_access_A_order", 200, "GET", "/api/v1/orders/" + id_a,
          bearer=verified_a, store=store_a)
    denied("B_cannot_access_verified_A_store", "GET", "/api/v1/orders",
           bearer=token_b, store=store_a)

    login_a = exact("owner_A_password_requires_second_factor", 200, "POST",
                    "/api/v1/auth/login", {"email": email_a, "password": password_a})
    guarantee(login_a["mfaRequired"] is True and
              "accessToken" not in login_a and
              login_a["enrollmentRequired"] is False,
              "enrolled owner must not receive password-only token")
    login_challenge = login_a["challengeToken"]
    denied("A_password_challenge_not_a_session", "GET", "/api/v1/me",
           bearer=login_challenge, store=store_a)
    denied("A_consumed_TOTP_cannot_be_replayed", "POST",
           "/api/v1/auth/mfa/login/verify",
           {"challengeToken": login_challenge, "code": code})
    recovery = enrolled["recoveryCodes"][0]
    recovered = exact("owner_A_one_time_recovery_code", 200, "POST",
                      "/api/v1/auth/mfa/login/verify",
                      {"challengeToken": login_challenge, "recoveryCode": recovery})
    exact("owner_A_recovered_session", 200, "GET", "/api/v1/me",
          bearer=recovered["accessToken"], store=store_a)
    denied("recovery_challenge_reuse_denied", "POST", "/api/v1/auth/mfa/login/verify",
           {"challengeToken": login_challenge, "recoveryCode": recovery})
    next_login = exact("owner_A_new_MFA_challenge", 200, "POST",
                       "/api/v1/auth/login", {"email": email_a, "password": password_a})
    denied("recovery_code_reused_on_new_login_denied", "POST",
           "/api/v1/auth/mfa/login/verify",
           {"challengeToken": next_login["challengeToken"], "recoveryCode": recovery})
    second_login = exact("owner_B_unenrolled_remains_accessible", 200, "POST",
                         "/api/v1/auth/login", {"email": email_b, "password": password_b})
    guarantee("accessToken" in second_login and "mfaRequired" not in second_login,
              "non-enrolled owner should keep access during migration")

    # Before leaving, confirm all captured passive requests are processed.
    wait_passive_queue()
    raw = sanitized_alerts()
    OUT.joinpath("authenticated-zap.json").write_text(
        json.dumps(raw, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    OUT.joinpath("authenticated-assertions.json").write_text(
        json.dumps({"mode": "localhost-disposable-ZAP-proxy",
                    "assertions": RESULTS, "total_passed": len(RESULTS)},
                   indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"PASS: authenticated ZAP proxy requests={len(RESULTS)} "
          f"sanitized_rules={len(raw['site'][0]['alerts'])}", flush=True)


if __name__ == "__main__":
    try:
        run()
    except (AssertionError, ValueError, KeyError, OSError, TimeoutError, TypeError) as exc:
        # Only error labels and type, never HTTP body, OTP, bearer or password.
        label = str(exc) if isinstance(exc, AssertionError) else type(exc).__name__
        print(f"::error::Authenticated DAST failed: {label}", file=sys.stderr)
        sys.exit(1)
