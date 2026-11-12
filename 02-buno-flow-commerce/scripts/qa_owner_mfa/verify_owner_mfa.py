#!/usr/bin/env python3
"""Real HTTP assertions against loopback Java + disposable PostgreSQL.

No external network, no actual merchant identities, no secrets in logs.
"""
import base64
import hashlib
import hmac
import json
import os
import secrets
import struct
import subprocess
import time
import urllib.error
import urllib.request

ROOT = "http://127.0.0.1:8099/api/v1"
TEST_EMAIL = f"owner-mfa-{secrets.token_hex(8)}@example.invalid"
PASSWORD = secrets.token_urlsafe(26)
STORE = "Buno Isolated OWNER QA"


def fail(label, status):
    raise AssertionError(f"QA_FAIL={label} http={status}")


def call(path, payload=None, token=None, store=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if store:
        headers["X-Store-Id"] = store
    req = urllib.request.Request(
        ROOT + path,
        method="POST" if payload is not None else "GET",
        data=json.dumps(payload).encode() if payload is not None else None,
        headers=headers,
    )
    try:
        with urllib.request.urlopen(req, timeout=12) as response:
            body = response.read(50_000)
            return response.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as error:
        error.read(10_000)  # do not print sensitive response bodies
        return error.code, {}


def expect(label, path, payload, status, token=None, store=None):
    actual, data = call(path, payload, token, store)
    if actual != status:
        fail(label, actual)
    return data


def sql(statement):
    result = subprocess.run(
        ["psql", "-XAt", "-v", "ON_ERROR_STOP=1", "-c", statement],
        capture_output=True, text=True, check=True, timeout=12
    )
    return result.stdout.strip()


def totp(base32, offset=0):
    raw = base64.b32decode(base32, casefold=True)
    counter = int(time.time() // 30) + offset
    h = hmac.new(raw, struct.pack(">Q", counter), hashlib.sha1).digest()
    pos = h[-1] & 0x0F
    number = struct.unpack(">I", h[pos:pos + 4])[0] & 0x7FFFFFFF
    return f"{number % 1_000_000:06d}"


def require(condition, label):
    if not condition:
        raise AssertionError("QA_FAIL=" + label)


def main():
    require(os.getenv("COMMERCE_OWNER_MFA_REQUIRED") == "true", "mfa_not_enabled")
    require(os.getenv("SPRING_DATASOURCE_URL", "").startswith(
        "jdbc:postgresql://127.0.0.1:55434/flow_mfa_qa"),
        "database_not_local"
    )

    # Password-only registration must NOT issue any usable session.
    account = expect("register", "/auth/register", {
        "email": TEST_EMAIL, "password": PASSWORD, "storeName": STORE
    }, 200)
    require(account.get("mfaRequired") is True and account.get("enrollmentRequired") is True,
            "enrollment_challenge_not_required")
    require("accessToken" not in account, "register_bypassed_mfa")
    challenge = account["challengeToken"]
    store = account["storeId"]
    require(len(challenge) == 64, "challenge_size")
    expect("challenge_token_cannot_access_me", "/me", None, 401, challenge, store)

    enrolled = expect("enrollment_begin", "/auth/mfa/enroll/start", {
        "challengeToken": challenge
    }, 200)
    secret = enrolled["secret"]
    require(len(secret) == 32 and enrolled["otpauthUri"].startswith("otpauth://totp/"),
            "invalid_totp_enrollment")

    invalid_code = "000000"
    otp = totp(secret)
    if otp == invalid_code:
        invalid_code = "999999"
    expect("reject_wrong_otp", "/auth/mfa/enroll/complete", {
        "challengeToken": challenge, "code": invalid_code
    }, 401)

    accepted = expect("finish_enrollment", "/auth/mfa/enroll/complete", {
        "challengeToken": challenge, "code": otp
    }, 200)
    require(accepted.get("role") == "OWNER" and accepted.get("storeId") == store,
            "mfa_identity_wrong")
    token = accepted["accessToken"]
    codes = accepted["recoveryCodes"]
    require(len(codes) == 8 and len(set(codes)) == 8, "recovery_code_count_or_uniqueness")
    expect("verified_token_access", "/me", None, 200, token, store)
    expect("enroll_challenge_used_twice", "/auth/mfa/enroll/complete", {
        "challengeToken": challenge, "code": otp
    }, 401)

    # Confirm the actual database contains a ciphertext, never the Base32 plaintext.
    ciphertext = sql("SELECT secret_ciphertext FROM flow_commerce.owner_mfa LIMIT 1;")
    require(len(ciphertext) >= 40 and secret not in ciphertext, "secret_not_encrypted")
    require(sql("SELECT count(*) FROM flow_commerce.user_sessions WHERE mfa_verified=false;") == "0",
            "non_mfa_session_inserted")
    print("QA_PASS=owner_enroll_totp_encrypted_and_verified", flush=True)

    login = expect("login_password_only", "/auth/login", {
        "email": TEST_EMAIL, "password": PASSWORD
    }, 200)
    require(login.get("mfaRequired") is True and "accessToken" not in login and
            login.get("enrollmentRequired") is False, "owner_password_bypassed_mfa")
    login_token = login["challengeToken"]
    expect("totp_replay_rejected", "/auth/mfa/login/verify", {
        "challengeToken": login_token, "code": otp
    }, 401)

    recovery = codes[0]
    verified = expect("recovery_code_login", "/auth/mfa/login/verify", {
        "challengeToken": login_token, "recoveryCode": recovery
    }, 200)
    require("accessToken" in verified, "recovery_login_not_authenticated")
    expect("recovery_session_access", "/me", None, 200, verified["accessToken"], store)
    expect("challenge_reuse_rejected", "/auth/mfa/login/verify", {
        "challengeToken": login_token, "recoveryCode": recovery
    }, 401)

    login2 = expect("login_again", "/auth/login", {
        "email": TEST_EMAIL, "password": PASSWORD
    }, 200)
    expect("recovery_code_reuse_rejected", "/auth/mfa/login/verify", {
        "challengeToken": login2["challengeToken"], "recoveryCode": recovery
    }, 401)
    require(sql("SELECT count(*) FROM flow_commerce.owner_mfa_recovery_codes WHERE used_at IS NOT NULL;") == "1",
            "recovery_used_once")
    print("QA_PASS=owner_login_replay_and_recovery_once", flush=True)

    # Challenge expiry enforced against the actual PostgreSQL state.
    login3 = expect("login_for_expiry", "/auth/login", {
        "email": TEST_EMAIL, "password": PASSWORD
    }, 200)
    token_hash = hashlib.sha256(login3["challengeToken"].encode()).hexdigest()
    sql(f"UPDATE flow_commerce.owner_mfa_challenges SET expires_at=now()-interval '1 second' "
        f"WHERE token_hash='{token_hash}';")
    expect("expired_challenge_rejected", "/auth/mfa/login/verify", {
        "challengeToken": login3["challengeToken"], "recoveryCode": codes[1]
    }, 401)
    print("QA_PASS=owner_challenge_expiry", flush=True)

    # Fail closed without ever contacting accounts in the actual staging.
    require(sql("SELECT count(*) FROM flow_commerce.users;") == "1", "unexpected_qa_users")
    require(sql("SELECT count(*) FROM flow_commerce.orders;") == "0", "unexpected_qa_orders")
    require(sql("SELECT count(*) FROM flow_commerce.owner_mfa WHERE enabled_at IS NOT NULL;") == "1",
            "totp_enrollment_missing")
    print("QA_PASS=isolated_db_counts_and_no_real_accounts", flush=True)


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, KeyError, ValueError, RuntimeError, OSError) as exc:
        message = str(exc)
        if message.startswith("QA_FAIL="):
            print(message, flush=True)
        else:
            print("QA_FAIL=unexpected_controlled_test_exception", flush=True)
        raise SystemExit(1)
