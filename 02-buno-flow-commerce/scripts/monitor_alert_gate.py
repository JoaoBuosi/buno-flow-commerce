#!/usr/bin/env python3
"""Offline, fail-closed monitor evaluation. No network, accounts, or credentials."""
from __future__ import annotations
import argparse
import json
from pathlib import Path

REQUIRED = frozenset(("api_health", "anonymous_api_denied", "frontend_reachable"))
EXPECTED = {
    "api_health": (200,),
    "anonymous_api_denied": (401, 403),
    "frontend_reachable": (200, 401, 403),
}
FORBIDDEN = ("token", "secret", "password", "cookie", "authorization", "email", "api_key")

def evaluate(payload: object) -> list[str]:
    if not isinstance(payload, dict) or set(payload) != {"environment", "checks"}:
        return ["Missing or unexpected top-level evidence"]
    if payload["environment"] != "disposable":
        return ["Monitoring rehearsals must be disposable"]
    checks = payload["checks"]
    if not isinstance(checks, dict) or set(checks) != REQUIRED:
        return ["Missing or unexpected probe results"]
    failures = []
    for name, allowed in EXPECTED.items():
        record = checks[name]
        if not isinstance(record, dict) or set(record) != {"http_status", "latency_ms"}:
            failures.append(name + ": malformed result")
            continue
        status, latency = record["http_status"], record["latency_ms"]
        if type(status) is not int or status not in allowed:
            failures.append(name + ": status violates availability/security gate")
        if type(latency) not in (int, float) or not (0 <= latency <= 5000):
            failures.append(name + ": missing/slow response")
    return failures

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    args = parser.parse_args()
    try:
        raw = args.report.read_text(encoding="utf-8")
        data = json.loads(raw)
    except (OSError, ValueError, UnicodeError):
        print("ALERT: missing or malformed synthetic monitor evidence")
        return 2
    if len(raw) > 8192 or any(word in raw.lower() for word in FORBIDDEN):
        print("ALERT: unexpected sensitive material in monitor evidence")
        return 2
    errors = evaluate(data)
    for reason in errors:
        print("ALERT: " + reason)
    if errors:
        return 1
    print("PASS: synthetic availability and unauthorized-access monitoring policy")
    print("LIMIT: not a real delivered notification or production SLO measurement")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
