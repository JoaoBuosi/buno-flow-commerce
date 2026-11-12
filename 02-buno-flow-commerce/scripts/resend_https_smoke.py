#!/usr/bin/env python3
"""One-shot HTTPS Resend smoke test. Runs without a database or app permissions.

Requires RESEND_API_KEY, RESEND_QA_RECIPIENT and RESEND_QA_CONFIRM=send-one.
Designed as a disposable Railway service, never a background worker.
"""
import json
import re
import os
import ssl
import sys
import urllib.error
import urllib.request

def main():
    key = os.getenv("RESEND_API_KEY", "").strip()
    recipient = os.getenv("RESEND_QA_RECIPIENT", "").strip()
    if not key or not recipient or os.getenv("RESEND_QA_CONFIRM") != "send-one":
        print("BLOCKED: missing credentials, recipient or explicit one-shot confirmation", flush=True)
        return 2
    if recipient.count("@") != 1 or any(ch in recipient for ch in "\r\n<> ,"):
        print("BLOCKED: invalid controlled recipient", flush=True)
        return 2

    data = json.dumps({
        "from": "Buno Flow Commerce QA <onboarding@resend.dev>",
        "to": [recipient],
        "subject": "Buno Flow Commerce - verificacao Resend HTTPS",
        "text": ("Este e um teste isolado de envio por HTTPS do Flow Commerce. "
                 "Nenhum link, senha ou dado de cliente foi utilizado.")
    }).encode("utf-8")
    request = urllib.request.Request(
        "https://api.resend.com/emails",
        data=data,
        headers={
            "Authorization": "Bearer " + key,
            "Content-Type": "application/json",
        },
        method="POST"
    )
    try:
        with urllib.request.urlopen(request, context=ssl.create_default_context(), timeout=20) as response:
            code = response.status
            # Never echo provider response body, keys or addresses into logs.
            print(f"RESEND_QA_RESULT=accepted HTTP={code} one_message=true", flush=True)
            return 0 if code in (200, 201, 202) else 1
    except urllib.error.HTTPError as exc:
        # Provider diagnostics only. Never echo raw HTML, email addresses or API keys.
        category="unknown"
        safe_message="unavailable"
        content_type=exc.headers.get("Content-Type", "") if exc.headers else ""
        if "json" in content_type.lower():
            try:
                body=json.loads(exc.read(2048).decode("utf-8",errors="replace"))
                if isinstance(body, dict):
                    category=re.sub(r"[^a-zA-Z0-9_-]", "", str(body.get("name","unknown")))[:80]
                    candidate=str(body.get("message",""))
                    candidate=re.sub(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+", "[email]", candidate)
                    candidate=re.sub(r"re_[A-Za-z0-9_-]+", "[secret]", candidate)
                    candidate=re.sub(r"[^A-Za-z0-9 .,;:_()\-/]", " ", candidate)
                    safe_message=candidate[:250]
            except (ValueError,UnicodeError):
                category="invalid_json"
        print(f"RESEND_QA_RESULT=rejected HTTP={exc.code} category={category} detail={safe_message} one_message=true", flush=True)
        return 1
    except Exception:
        print("RESEND_QA_RESULT=connection_error one_message=false", flush=True)
        return 1

if __name__ == "__main__":
    sys.exit(main())
