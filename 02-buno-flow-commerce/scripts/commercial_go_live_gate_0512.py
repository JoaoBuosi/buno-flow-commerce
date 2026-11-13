#!/usr/bin/env python3
"""Offline structural gate for a proposed paid Flow Commerce pilot.

Fail-closed: an absent, stale or inconsistent evidence manifest blocks approval.
Never reads environment secrets or contacts GitHub, Railway, Supabase or SMTP.
A PASS means DOCUMENTS ARE STRUCTURALLY COMPLETE, not independent proof.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone, timedelta
import json
from pathlib import Path
import re
from urllib.parse import urlsplit

REQUIRED = frozenset({
    "backend_browser_sast_dast_ci_green",
    "tested_commit_matches_candidate",
    "target_deployment_commit_attested",
    "tenant_isolation_in_target_environment",
    "rate_limit_and_permissions_verified",
    "mfa_key_rotated_and_vault_recovery_tested",
    "mfa_totp_and_single_use_recovery_verified",
    "real_backup_restored_in_isolated_postgresql17",
    "real_backup_integrity_and_retention_verified",
    "real_password_reset_delivery_and_reuse_verified",
    "delivered_outage_alert_acknowledged",
    "physical_mobile_human_acceptance_signed",
    "support_incident_response_and_rollback_drilled",
    "privacy_terms_and_billing_process_approved",
    "independent_security_review_approved",
})
SHA = re.compile(r"[a-f0-9]{40}\Z")
REPO = "/JoaoBuosi/buno-flow-commerce/"
URL_PATH = re.compile(
    r"/JoaoBuosi/buno-flow-commerce/(?:actions/runs/[0-9]+|issues/[0-9]+|pull/[0-9]+|commit/[a-f0-9]{40})/?\Z"
)
TOP_LEVEL = {"version", "scope", "candidate_sha", "deployed_sha", "observed_at", "checks", "approval"}
CHECK_KEYS = {"passed", "evidence_url", "checked_at"}
APPROVAL_KEYS = {"reviewed_by", "reviewed_at", "ticket_url", "decision"}

def parse_date(value: object) -> datetime | None:
    if not isinstance(value, str) or len(value) > 35:
        return None
    try:
        instant = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return instant.astimezone(timezone.utc) if instant.tzinfo else None
    except (TypeError, ValueError, OverflowError):
        return None

def safe_evidence_url(value: object) -> bool:
    if not isinstance(value, str) or len(value) > 300 or any(ch.isspace() for ch in value):
        return False
    try:
        u = urlsplit(value)
        return (u.scheme == "https" and u.hostname == "github.com" and
                u.port is None and u.username is None and u.password is None and
                u.query == "" and u.fragment == "" and
                URL_PATH.fullmatch(u.path) is not None)
    except ValueError:
        return False

def validate(evidence: object, expected_sha: str, now: datetime) -> list[str]:
    if not isinstance(evidence, dict):
        return ["Invalid manifest shape"]
    errors: list[str] = []
    if set(evidence) != TOP_LEVEL:
        errors.append("Unexpected or missing top-level keys")
    if evidence.get("version") != "0.5.12" or evidence.get("scope") != "paid_pilot":
        errors.append("Incorrect version/scope")
    candidate = evidence.get("candidate_sha")
    deployed = evidence.get("deployed_sha")
    if (not isinstance(candidate, str) or SHA.fullmatch(candidate) is None or
            not isinstance(expected_sha, str) or candidate != expected_sha):
        errors.append("Candidate SHA does not match explicitly requested release")
    if not isinstance(deployed, str) or deployed != candidate:
        errors.append("Deployed SHA must equal reviewed candidate SHA")
    observed = parse_date(evidence.get("observed_at"))
    if observed is None or observed > now + timedelta(minutes=10) or now - observed > timedelta(days=7):
        errors.append("Manifest observation must be recent and timezone aware")
    checks = evidence.get("checks")
    if not isinstance(checks, dict):
        errors.append("Missing check map")
    else:
        if set(checks) != REQUIRED:
            errors.append("Required evidence set is incomplete or contains unknown checks")
        for key in REQUIRED:
            check = checks.get(key)
            if not isinstance(check, dict) or set(check) != CHECK_KEYS or check.get("passed") is not True:
                errors.append("Missing or failed approval: " + key)
                continue
            if not safe_evidence_url(check.get("evidence_url")):
                errors.append("Invalid evidence reference: " + key)
            checked = parse_date(check.get("checked_at"))
            if checked is None or checked > now + timedelta(minutes=10) or now - checked > timedelta(days=30):
                errors.append("Outdated/malformed evidence date: " + key)
    approval = evidence.get("approval")
    if not isinstance(approval, dict) or set(approval) != APPROVAL_KEYS:
        errors.append("Human approval must be explicitly recorded")
    else:
        reviewer = approval.get("reviewed_by")
        if not isinstance(reviewer, str) or not reviewer.strip() or len(reviewer)>100:
            errors.append("Human reviewer name missing")
        if approval.get("decision") != "approved":
            errors.append("Explicit approval decision missing")
        if not safe_evidence_url(approval.get("ticket_url")):
            errors.append("Human approval ticket URL invalid")
        reviewed = parse_date(approval.get("reviewed_at"))
        if reviewed is None or reviewed > now + timedelta(minutes=10) or now-reviewed>timedelta(days=7):
            errors.append("Human approval must be recent")
    return errors

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--candidate-sha", required=True, help="40-character exact reviewed commit")
    args = parser.parse_args()
    try:
        raw = args.manifest.read_text(encoding="utf-8")
        if len(raw)>32768:
            raise ValueError("oversized")
        data = json.loads(raw)
    except (ValueError, OSError, UnicodeError):
        print("BLOCKED: evidence absent, oversized or malformed")
        return 2
    errors = validate(data, args.candidate_sha, datetime.now(timezone.utc))
    for problem in errors:
        print("BLOCKED: " + problem)
    if errors:
        return 1
    print("DOCUMENTARY GATE PASS — HUMAN/INDEPENDENT EVIDENCE MUST STILL BE VERIFIED")
    print("NOT A DEPLOYMENT AUTHORIZATION; no external service was contacted")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
