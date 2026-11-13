#!/usr/bin/env python3
"""Fail-closed pilot release gate. Offline only; never reads credentials or contacts services."""
from __future__ import annotations
import argparse
import json
from pathlib import Path

REQUIRED = (
    "ci_backend_postgres", "ci_browser_e2e", "ci_security_sast",
    "ci_zap_baseline", "ci_zap_authenticated", "cross_tenant_access_denied",
    "mfa_totp_and_recovery", "password_reset_email_verified",
    "backup_real_restore_isolated", "backup_restore_integrity_verified",
    "mfa_key_rotated_and_escrowed", "mfa_key_recovery_tested",
    "remaining_zap_warnings_triaged", "human_security_review_approved",
    "independent_pentest_reviewed", "pilot_support_and_rollback_ready",
)
SAFE_FIELDS = {"version", "environment", "checks", "approval"}
SAFE_APPROVAL = {"reviewed_by", "reviewed_at", "ticket"}
FORBIDDEN = ("password", "secret", "token", "private_key", "otp", "authorization",
             "cookie", "smtp_", "database_url", "api_key")

def validate(evidence: object) -> list[str]:
    problems = []
    if not isinstance(evidence, dict):
        return ["Evidence must be a JSON object"]
    if set(evidence) - SAFE_FIELDS:
        problems.append("Unexpected fields in release evidence")
    serialized = json.dumps(evidence).lower()
    if any(term in serialized for term in FORBIDDEN):
        problems.append("Potential credential-related material in evidence")
    if evidence.get("version") != "0.5.9":
        problems.append("Expected version 0.5.9")
    if evidence.get("environment") != "staging":
        problems.append("Only staging evidence is accepted")
    checks = evidence.get("checks")
    if not isinstance(checks, dict):
        return problems + ["Missing checks object"]
    for name in REQUIRED:
        item = checks.get(name)
        if not isinstance(item, dict) or item.get("passed") is not True:
            problems.append(f"Missing or unapproved check: {name}")
            continue
        link = item.get("evidence_url")
        if not isinstance(link, str) or not link.startswith("https://") or len(link) < 12:
            problems.append(f"Missing HTTPS evidence reference: {name}")
        if set(item) - {"passed", "evidence_url"}:
            problems.append(f"Unexpected check metadata: {name}")
    if set(checks) - set(REQUIRED):
        problems.append("Unknown checks in evidence")
    approval = evidence.get("approval")
    if not isinstance(approval, dict) or set(approval) != SAFE_APPROVAL or not all(
        isinstance(approval.get(k), str) and approval[k].strip() for k in SAFE_APPROVAL
    ):
        problems.append("Human approval metadata absent or malformed")
    return problems

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("evidence", type=Path)
    args = parser.parse_args()
    try:
        result = validate(json.loads(args.evidence.read_text(encoding="utf-8")))
    except (OSError, ValueError) as exc:
        print(f"BLOCKED: invalid evidence ({type(exc).__name__})")
        return 2
    for reason in result:
        print(f"BLOCKED: {reason}")
    if result:
        return 1
    print("PASS: documentary gate only; does NOT deploy or authorize production release")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
