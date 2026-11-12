#!/usr/bin/env python3
"""One-shot live staging OWNER pilot QA.

Requires a disposable, exact-address allowlist. No DB credentials are available.
Prints only HTTP codes and non-sensitive PASS/FAIL markers.
"""
import base64
import hashlib
import hmac
import json
import os
import secrets
import struct
import time
import urllib.request
import urllib.error

ORIGIN = "https://flow-commerce-api-staging.up.railway.app"
ROOT = ORIGIN + "/api/v1"
PILOT = os.getenv("FLOW_QA_EMAIL", "")
EDGE = os.getenv("COMMERCE_EDGE_SECRET", "")
INVITE = os.getenv("COMMERCE_PILOT_INVITE_CODE", "")
PASSWORD = secrets.token_urlsafe(32)
REGULAR = PILOT.replace("@", "-regular@")
HEADER = {"Content-Type": "application/json", "X-Buno-Edge-Key": EDGE,
          "X-Buno-Client-IP": "192.0.2.193", "User-Agent": "FlowCommerceOwnerMfaQa/0.5.5"}

def expect(condition, label):
    if not condition:
        raise AssertionError("QA_FAIL=" + label)

def call(route, payload=None, token=None, store_id=None, pilot_code=False):
    headers = dict(HEADER)
    if pilot_code: headers["X-Pilot-Code"] = INVITE
    if token: headers["Authorization"] = "Bearer " + token
    if store_id: headers["X-Store-Id"] = store_id
    data = None if payload is None else json.dumps(payload).encode()
    request = urllib.request.Request(ROOT + route, data=data,
        method="GET" if payload is None else "POST", headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=13) as response:
            body = response.read(30000)
            return response.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as exc:
        exc.read(30000)
        return exc.code, {}

def required(route, payload, status, label, token=None, store=None, pilot_code=False):
    code, body = call(route, payload, token, store, pilot_code)
    if code != status:
        print(f"QA_FAIL={label} http={code}", flush=True)
        raise SystemExit(1)
    return body

def totp(secret):
    decoded = base64.b32decode(secret, casefold=True)
    counter = int(time.time() / 30)
    digest = hmac.new(decoded, struct.pack(">Q",counter), hashlib.sha1).digest()
    offset = digest[-1] & 0xf
    number = struct.unpack(">I", digest[offset:offset+4])[0] & 0x7fffffff
    return f"{number % 1_000_000:06d}"

def main():
    if os.getenv("FLOW_QA_CONFIRM") != "isolated-owner-pilot" or not (
        PILOT.startswith("owner-mfa-qa-") and PILOT.endswith("@example.invalid")
        and len(EDGE) >= 32 and len(INVITE) >= 10
    ):
        raise AssertionError("QA_FAIL=environment_guard")
    print("QA_STAGE=disposable_owner_registration",flush=True)
    owner = required("/auth/register", {"email":PILOT,"password":PASSWORD,
        "storeName":"Staging OWNER MFA QA only"}, 200, "pilot_registration",pilot_code=True)
    expect(owner.get("mfaRequired") is True and owner.get("enrollmentRequired") is True
           and "accessToken" not in owner, "pilot_registration_mfa_not_enforced")
    challenge = owner["challengeToken"]
    store_id = owner["storeId"]
    required("/me", None, 401, "challenge_must_not_authorize",challenge,store_id)
    setup = required("/auth/mfa/enroll/start", {"challengeToken":challenge},200,"setup")
    secret = setup["secret"]
    expect(len(secret)==32 and setup["otpauthUri"].startswith("otpauth://totp/"),
           "invalid_totp_setup")
    code = totp(secret)
    complete = required("/auth/mfa/enroll/complete",
        {"challengeToken":challenge,"code":code},200,"enroll")
    expect(complete.get("role")=="OWNER" and complete.get("storeId")==store_id,
        "owner_role_or_store_wrong")
    expect(len(complete.get("recoveryCodes",[]))==8, "recovery_codes_not_8")
    required("/me",None,200,"verified_owner_session",complete["accessToken"],store_id)
    print("QA_PASS=owner_enroll_and_session",flush=True)

    after_password = required("/auth/login",{"email":PILOT,"password":PASSWORD},
        200,"owner_login")
    expect(after_password.get("mfaRequired") is True
           and after_password.get("enrollmentRequired") is False
           and "accessToken" not in after_password,"owner_login_bypass")
    token2=after_password["challengeToken"]
    required("/auth/mfa/login/verify",{"challengeToken":token2,"code":code},
        401,"otp_replay")
    recovery=complete["recoveryCodes"][0]
    verified=required("/auth/mfa/login/verify",{
        "challengeToken":token2,"recoveryCode":recovery},200,"recovery_first")
    required("/me",None,200,"recovery_login_session",verified["accessToken"],store_id)
    required("/auth/mfa/login/verify",{
        "challengeToken":token2,"recoveryCode":recovery},401,"challenge_reuse")
    print("QA_PASS=owner_otp_replay_and_recovery",flush=True)

    print("QA_STAGE=nonpilot_owner_regression",flush=True)
    regular=required("/auth/register",{
        "email":REGULAR,"password":PASSWORD,"storeName":"Staging Normal OWNER QA only"},
        200,"regular_registration",pilot_code=True)
    expect("accessToken" in regular and regular.get("role")=="OWNER"
           and regular.get("mfaRequired") is None,"nonpilot_owner_unexpectedly_blocked")
    required("/me",None,200,"regular_owner_still_authorized",
        regular["accessToken"],regular["storeId"])
    print("QA_PASS=nonpilot_owner_remains_accessible",flush=True)
    print("QA_RESULT=PASS_staging_pilot_owner_mfa",flush=True)

if __name__=="__main__":
    try:
        main()
    except (AssertionError, KeyError, TypeError, ValueError, OSError) as exc:
        if str(exc).startswith("QA_FAIL="):
            print(str(exc),flush=True)
        else:
            print("QA_FAIL=controlled_client_exception",flush=True)
        raise SystemExit(1)
