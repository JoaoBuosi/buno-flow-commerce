#!/usr/bin/env python3
"""Deliver a minimal synthetic QA alert to a local JSONL spool.

No HTTP, SMTP, third-party services, customer data or production alerts.
The local spool is evidence that an alert was emitted, not that it was
delivered to a human. Only callable with explicit disposable QA opt-in.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys
from monitor_alert_gate import evaluate

KINDS = {
    "api_health": "api_unavailable",
    "anonymous_api_denied": "anonymous_access_violation",
    "frontend_reachable": "frontend_unavailable",
}
KEYS = frozenset(("environment", "checks"))

def build_alerts(report: object) -> list[dict[str, str]]:
    if not isinstance(report, dict) or set(report) != KEYS:
        return [{"type": "invalid_qa_report", "status": "alert"}]
    problems = evaluate(report)
    if not problems:
        return []
    if not isinstance(report.get("checks"), dict):
        return [{"type": "invalid_qa_report", "status": "alert"}]
    events = []
    for problem in problems:
        name = problem.split(":", 1)[0]
        events.append({"type": KINDS.get(name, "invalid_qa_report"), "status": "alert"})
    # Intentionally no timestamps, hostnames, raw requests or incident secrets.
    return list({(event["type"], event["status"]): event for event in events}.values())

def emit(report: object, spool: Path, *, authorized: bool) -> int:
    if not authorized:
        raise ValueError("Disposable QA confirmation required")
    if not isinstance(report, dict) or report.get("environment") != "disposable":
        raise ValueError("Only synthetic disposable evidence is allowed")
    if not spool.is_absolute() or spool.is_symlink() or not spool.parent.is_dir():
        raise ValueError("Spool must be an absolute local regular path with existing parent")
    if spool.exists():
        raise ValueError("Spool must not exist; overwrite/append is forbidden")
    alerts = build_alerts(report)
    fd = os.open(str(spool), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as output:
        for event in alerts:
            output.write(json.dumps(event, separators=(",", ":")) + "\n")
    return len(alerts)

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--spool", required=True, type=Path)
    args = parser.parse_args()
    if os.getenv("CI") != "true" or os.getenv("FLOW_QA_ALERT_DELIVERY") != "disposable-only":
        print("BLOCKED: synthetic CI approval required")
        return 2
    try:
        raw = args.report.read_text(encoding="utf-8")
        if len(raw) > 8192:
            raise ValueError("Report too large")
        report = json.loads(raw)
        # Refuse any unexpected structure to prevent logging user data.
        if (not isinstance(report, dict) or set(report) != KEYS or
                not isinstance(report.get("checks"), dict)):
            raise ValueError("Unexpected evidence fields")
        for key, item in report["checks"].items():
            if key not in KINDS or not isinstance(item, dict) or set(item) != {"http_status", "latency_ms"}:
                raise ValueError("Unexpected probe content")
        amount = emit(report, args.spool, authorized=True)
        print(f"QA_ALERT_SPOOL_EMITTED={amount} local_only=true")
        return 0
    except (ValueError, OSError, UnicodeError, TypeError):
        print("BLOCKED: invalid synthetic alert report or spool")
        return 2

if __name__ == "__main__":
    sys.exit(main())
