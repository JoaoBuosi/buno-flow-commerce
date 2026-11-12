#!/usr/bin/env python3
"""Validate and summarize local-only OWASP ZAP baseline JSON reports.

Policy: block HIGH findings; report MEDIUM/LOW/INFO for triage. Never silently
pass a missing/empty/malformed report. This does not certify the application.
"""
from __future__ import annotations
import argparse
from collections import Counter
import html
import json
import os
from pathlib import Path
import sys

SEVERITY = {0: "Informational", 1: "Low", 2: "Medium", 3: "High"}

def check_report(filename: Path) -> dict:
    if not filename.is_file() or filename.stat().st_size < 20:
        raise ValueError("ZAP report is absent or empty")
    document = json.loads(filename.read_text(encoding="utf-8"))
    sites = document.get("site", [])
    if isinstance(sites, dict):
        sites = [sites]
    if not isinstance(sites, list) or not sites:
        raise ValueError("ZAP has no scanned site; no evidence of a valid audit")

    total = Counter()
    findings = []
    for site in sites:
        host = site.get("@host", "")
        port = str(site.get("@port", ""))
        if host not in ("127.0.0.1", "localhost") or port != "5173":
            raise ValueError("ZAP report contains an unapproved, non-loopback target")
        alerts = site.get("alerts", [])
        if not isinstance(alerts, list):
            raise ValueError("Malformed ZAP alerts")
        for alert in alerts:
            try:
                severity = int(alert["riskcode"])
            except (ValueError, KeyError, TypeError) as exc:
                raise ValueError("Missing/unknown alert risk code") from exc
            if severity not in SEVERITY:
                raise ValueError("Unsupported ZAP risk severity")
            total[SEVERITY[severity]] += 1
            findings.append({
                "id": str(alert.get("pluginid", "unknown"))[:25],
                "name": str(alert.get("name") or alert.get("alert") or "Unnamed alert")[:130],
                "severity": SEVERITY[severity],
                "instances": len(alert.get("instances", []) or []),
            })
    return {"sites": len(sites), "counts": total, "findings": findings}

def summarize(paths: list[Path]) -> int:
    lines = ["## Buno Flow Commerce — OWASP ZAP (passive, isolated)",
             "",
             "Scanned only a disposable localhost application backed by a fake PostgreSQL database.",
             "No active attacks, real stores, payment integrations, credentials, or external URLs.",
             ""]
    failed = False
    for path in paths:
        result = check_report(path)
        counts = result["counts"]
        high = counts["High"]
        lines.extend([
            f"### {html.escape(path.stem)}",
            f"Scanned sites: **{result['sites']}**",
            "",
            "| High | Medium | Low | Informational |",
            "|---:|---:|---:|---:|",
            f"| {high} | {counts['Medium']} | {counts['Low']} | {counts['Informational']} |",
            "",
        ])
        failed |= high > 0
        if result["findings"]:
            lines.extend(["| Severity | ID | Finding | Instances |",
                          "|---|---|---|---:|"])
            for a in sorted(result["findings"],
                            key=lambda item: -next(k for k,v in SEVERITY.items()
                                                   if v == item["severity"]))[:25]:
                lines.append(
                    f"| {a['severity']} | {html.escape(a['id'])} | "
                    f"{html.escape(a['name'])} | {a['instances']} |")
            lines.append("")
    lines.extend([
        f"**Gate: {'FAIL — High findings need review' if failed else 'PASS — no High alerts detected'}**",
        "",
        "Medium/low findings still require review; a clean passive report does not exclude "
        "SQL injection, IDOR, MFA bypass, or other authenticated vulnerabilities.",
        "See HTML and JSON artifacts attached to the workflow run.",
    ])
    summary = "\n".join(lines) + "\n"
    out = os.getenv("GITHUB_STEP_SUMMARY")
    if out:
        with open(out, "a", encoding="utf-8") as f:
            f.write(summary)
    else:
        print(summary)
    return 1 if failed else 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("reports", nargs="+", type=Path)
    args = parser.parse_args()
    try:
        sys.exit(summarize(args.reports))
    except (ValueError, OSError, json.JSONDecodeError) as e:
        # Do not expose report contents or request bodies.
        print(f"::error::Cannot validate ZAP report: {type(e).__name__}: {e}", file=sys.stderr)
        sys.exit(2)
