#!/usr/bin/env python3
"""Isolated authenticated DAST harness for the real Flow Commerce Java API.

Registers two fake OWNER accounts on local PostgreSQL, verifies their RFC6238
TOTP setup, creates one synthetic order per store and asserts tenant-bound
authorization failures. Writes ONLY short-lived session tokens to a chmod 0600
fixture consumed by the loopback-only ZAP GET proxy. No real identities,
Supabase credentials or external host input are accepted.
"""
from __future__ import annotations
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import struct
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:8080/api/v1"
PASSWORD = secrets.token_urlsafe(32)
PREFIX = secrets.token_hex(6)
MAX_BYTES = 60_000


class DastFailure(Exception):
    pass


def check(label: str, ok: bool) -> None:
    if not ok:
        raise DastFailure(label)
    print("AUTH_DAST_PASS=" + label, flush=True)


def api(path: str, method: str = "GET", body: dict | None = None,
        token: str = "", store: str = "") -> tuple[int, object]:
    if not path.startswith("/") or "://" in path:
        raise DastFailure("external_api_target_rejected")
    headers = {"Content-Type": "application/json", "Cache-Control": "no-store"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if store:
        headers["X-Store-Id"] = store
    req = Request(BASE + path,
                  data=json.dumps(body).encode() if body is not None else None,
                  method=method, headers=headers)
    try:
        with urlopen(req, timeout=12) as response:
            raw = response.read(MAX_BYTES)
            return response.status, parse(raw)
    except HTTPError as response:
        # Intentionally do NOT echo request/response or token-bearing headers.
        response.read(MAX_BYTES)
        return response.code, {}
    except (URLError, TimeoutError) as error:
        raise DastFailure("local_api_unavailable") from error


def parse(raw: bytes) -> object:
    if not raw:
        return {}
    try:
        return json.loads(raw)
    except (ValueError, UnicodeDecodeError):
        return raw.decode("utf-8", errors="replace")


def expect(label: str, expected: int, path: str, method: str = "GET",
           body: dict | None = None, token: str = "", store: str = "") -> object:
    code, result = api(path, method, body, token, store)
    if code != expected:
        raise DastFailure(label + "_unexpected_http_" + str(code))
    print("AUTH_DAST_PASS=" + label, flush=True)
    return result


def totp(secret: str) -> str:
    try:
        key = base64.b32decode(secret, casefold=True)
        step = int(time.time() // 30)
        digest = hmac.digest(key, struct.pack(">Q", step), "sha1")
        offset = digest[-1] & 0xf
        value = struct.unpack(">I", digest[offset:offset+4])[0] & 0x7fffffff
        return f"{value % 1000000:06d}"
    except (ValueError, KeyError) as error:
        raise DastFailure("invalid_enrollment_totp") from error


def enroll(alias: str) -> dict:
    email = "dast-" + alias + "-" + PREFIX + "@example.invalid"
    initial = expect(alias + "_register_requires_second_factor", 200, "/auth/register", "POST",
                     {"email": email, "password": PASSWORD, "storeName": "QA " + alias})
    check(alias + "_register_no_session",
          isinstance(initial, dict) and initial.get("mfaRequired") is True
          and initial.get("enrollmentRequired") is True and "accessToken" not in initial)
    challenge = initial["challengeToken"]
    store = initial["storeId"]
    expect(alias + "_challenge_not_bearer", 401, "/me", token=challenge, store=store)
    setup = expect(alias + "_enroll_start", 200, "/auth/mfa/enroll/start", "POST",
                   {"challengeToken": challenge})
    code = totp(setup["secret"])
    confirmed = expect(alias + "_enroll_complete", 200, "/auth/mfa/enroll/complete", "POST",
                       {"challengeToken": challenge, "code": code})
    check(alias + "_eight_recovery_codes", len(confirmed.get("recoveryCodes", [])) == 8)
    token = confirmed["accessToken"]
    profile = expect(alias + "_authenticated_me", 200, "/me", token=token, store=store)
    check(alias + "_proper_identity", profile.get("storeId") == store
          and profile.get("role") == "OWNER")
    mfa = expect(alias + "_mfa_status", 200, "/auth/mfa/status", token=token, store=store)
    check(alias + "_second_factor_enforced", mfa.get("enrolled") and mfa.get("required"))
    return {"email": email, "token": token, "store": store,
            "recovery": confirmed["recoveryCodes"], "password": PASSWORD, "otp": code}


def order(owner: dict, label: str) -> dict:
    marker = "DAST_" + label + "_" + PREFIX
    result = expect(label + "_create_synthetic_order", 201, "/orders", "POST", {
        "customer": marker,
        "items": [{"sku": "QA-" + label, "description": "Synthetic CI item",
                   "quantity": 1, "price": 13.75}]
    }, token=owner["token"], store=owner["store"])
    check(label + "_order_has_id", isinstance(result, dict) and bool(result.get("id")))
    return {"id": result["id"], "marker": marker}


def main() -> None:
    fixture = Path(os.environ.get("ZAP_AUTH_FIXTURE_PATH", ""))
    if (os.environ.get("CI") != "true" or
        not fixture.is_absolute() or fixture.name != "auth-fixtures.json" or
        not fixture.parent.is_dir() or fixture.parent.name != "zap-auth"):
        raise DastFailure("ci_fixture_guard_failed")
    if os.environ.get("COMMERCE_OWNER_MFA_REQUIRED") != "true":
        raise DastFailure("mfa_enforcement_not_enabled")
    if os.environ.get("SPRING_DATASOURCE_URL", "").split("?", 1)[0] != (
        "jdbc:postgresql://127.0.0.1:5432/commerce_zap_ci"):
        raise DastFailure("non_disposable_database_blocked")

    a = enroll("owner-a")
    b = enroll("owner-b")
    oa = order(a, "OWNER_A")
    ob = order(b, "OWNER_B")

    # Same-store access works; cross-store tenant-header substitution must fail.
    expect("owner_a_own_detail", 200, "/orders/" + oa["id"],
           token=a["token"], store=a["store"])
    expect("owner_b_own_detail", 200, "/orders/" + ob["id"],
           token=b["token"], store=b["store"])
    for actor, other in ((a,b), (b,a)):
        expect("cross_store_header_is_rejected", 401, "/me",
               token=actor["token"], store=other["store"])
        expect("cross_store_header_orders_rejected", 401, "/orders",
               token=actor["token"], store=other["store"])
        expect("cross_store_header_export_rejected", 401, "/orders/export",
               token=actor["token"], store=other["store"])
    for actor, own, foreign in ((a,oa,ob),(b,ob,oa)):
        expect("idor_foreign_order_detail_404",404,
               "/orders/"+foreign["id"],token=actor["token"],store=actor["store"])
        expect("idor_foreign_order_history_404",404,
               "/orders/"+foreign["id"]+"/history",
               token=actor["token"],store=actor["store"])
        # Targeted write-denial checks are safe because both orders are synthetic.
        expect("idor_foreign_order_status_patch_404",404,
               "/orders/"+foreign["id"]+"/status","PATCH",
               {"status":"PAID"},token=actor["token"],store=actor["store"])
        expect("idor_foreign_order_cancel_404",404,
               "/orders/"+foreign["id"]+"/cancel","POST",
               {"reason":"Synthetic cross-tenant security test"},
               token=actor["token"],store=actor["store"])
        foreign_own_detail=expect("foreign_owner_order_unchanged",200,
               "/orders/"+foreign["id"],token=(
                   b["token"] if actor is a else a["token"]
               ),store=(b["store"] if actor is a else a["store"]))
        check("cross_tenant_write_did_not_mutate_order",
              foreign_own_detail["status"]=="CREATED")
        own_list=expect("tenant_orders_list",200,"/orders",
                        token=actor["token"],store=actor["store"])
        check("tenant_list_contains_only_own_order",
              isinstance(own_list,list) and len(own_list)==1
              and own_list[0]["id"]==own["id"]
              and foreign["marker"] not in json.dumps(own_list))
        dashboard=expect("tenant_dashboard",200,"/dashboard",
                         token=actor["token"],store=actor["store"])
        check("tenant_dashboard_count_one",int(dashboard["order_count"])==1)
        count=expect("tenant_count",200,"/orders/count",
                     token=actor["token"],store=actor["store"])
        check("tenant_count_one",int(count["total"])==1)
        csv=expect("tenant_export",200,"/orders/export",
                   token=actor["token"],store=actor["store"])
        check("tenant_export_does_not_leak_foreign_data",
              isinstance(csv,str) and own["marker"] in csv
              and foreign["marker"] not in csv)
        expect("tenant_own_history",200,"/orders/"+own["id"]+"/history",
               token=actor["token"],store=actor["store"])
    expect("anonymous_cannot_read_orders",401,"/orders")
    expect("anonymous_cannot_read_mfa_status",401,"/auth/mfa/status")
    expect("malformed_token_is_rejected",401,"/me",
           token="f"*64,store=a["store"])

    # Password-only login must not grant bearer; invalid and repeated factor
    # proof must be rejected. Every factor and password is fake, discarded.
    next_login=expect("mfa_password_only_challenge",200,"/auth/login","POST",
                      {"email":a["email"],"password":a["password"]})
    check("mfa_password_does_not_grant_session",
          next_login.get("mfaRequired") is True and "accessToken" not in next_login)
    valid=a["otp"]
    wrong="000000" if valid!="000000" else "999999"
    expect("mfa_invalid_code_rejected",401,"/auth/mfa/login/verify","POST",
           {"challengeToken":next_login["challengeToken"],"code":wrong})
    expect("mfa_totp_replay_rejected",401,"/auth/mfa/login/verify","POST",
           {"challengeToken":next_login["challengeToken"],"code":valid})
    recovery=a["recovery"][0]
    success=expect("mfa_recovery_first_use",200,"/auth/mfa/login/verify","POST",
                   {"challengeToken":next_login["challengeToken"],"recoveryCode":recovery})
    check("mfa_recovery_yields_real_session",bool(success.get("accessToken")))
    expect("mfa_recovery_session_authorized",200,"/me",
           token=success["accessToken"],store=a["store"])
    expect("mfa_replay_challenge_rejected",401,"/auth/mfa/login/verify","POST",
           {"challengeToken":next_login["challengeToken"],"recoveryCode":recovery})
    new_login=expect("mfa_second_password_challenge",200,"/auth/login","POST",
                     {"email":a["email"],"password":a["password"]})
    expect("mfa_recovery_code_single_use",401,"/auth/mfa/login/verify","POST",
           {"challengeToken":new_login["challengeToken"],"recoveryCode":recovery})

    # Never write email, raw OTP, password, recovery code or challenge to disk.
    safe={
      "owner-a":{"token":a["token"],"storeId":a["store"],"orderId":oa["id"]},
      "owner-b":{"token":b["token"],"storeId":b["store"],"orderId":ob["id"]},
    }
    flags=os.O_WRONLY|os.O_CREAT|os.O_EXCL
    fd=os.open(fixture,flags,0o600)
    with os.fdopen(fd,"w",encoding="utf-8") as out:
        json.dump(safe,out)
    check("short_lived_scan_sessions_ready",fixture.stat().st_mode & 0o077 == 0)
    print("AUTH_DAST_RESULT=PASS_local_two_tenants_mfa_and_idor",flush=True)


if __name__=="__main__":
    try:
        main()
    except (DastFailure,KeyError,TypeError,ValueError,OSError) as error:
        if isinstance(error,DastFailure):
            print("::error::AUTH_DAST_FAIL="+str(error),file=sys.stderr)
        else:
            print("::error::AUTH_DAST_FAIL=unexpected_validation_error",file=sys.stderr)
        sys.exit(1)
