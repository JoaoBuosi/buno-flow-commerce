#!/usr/bin/env python3
"""Isolated staging IP limiter verification. No account data, no real user credentials.

Must run only in disposable Railway QA service using reference to API edge secret.
Produces up to 32 deliberately invalid login requests, all using reserved TEST-NET IPs.
"""
from __future__ import annotations
import json
import os
import sys
import urllib.error
import urllib.request

API="https://flow-commerce-api-staging.up.railway.app/api/v1/auth/login"

def post(fake_ip, secret):
    body=json.dumps({"email":"missing-ip-rate-qa@example.test","password":"not-a-valid-password"}).encode()
    req=urllib.request.Request(API, data=body, method="POST", headers={
        "Content-Type":"application/json",
        "X-Buno-Edge-Key":secret,
        "X-Buno-Client-IP":fake_ip,
        "X-Forwarded-For":"203.0.113.199",  # Must be ignored in favor of verified edge identity.
        "User-Agent":"BunoFlowSecurityQA/0.5.4"
    })
    try:
        with urllib.request.urlopen(req,timeout=12) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception:
        return -1

def main():
    secret=os.getenv("COMMERCE_EDGE_SECRET","")
    if len(secret)<32 or os.getenv("FLOW_QA_CONFIRM")!="one-shot-rate-limit":
        print("BLOCKED: protected gateway secret or one-shot opt-in absent",flush=True)
        return 2
    for i in range(30):
        status=post("192.0.2.211", secret)
        if status!=401:
            print(f"QA_IP_RATE_LIMIT_FAIL stage=within_allowance attempt={i+1} HTTP={status}",flush=True)
            return 1
    blocked=post("192.0.2.211",secret)
    other=post("192.0.2.212",secret)
    ok=blocked==429 and other==401
    print(f"QA_IP_RATE_LIMIT_RESULT={'PASS' if ok else 'FAIL'} blocked={blocked} distinct_client={other} sends=32",flush=True)
    return 0 if ok else 1

if __name__=="__main__":
    sys.exit(main())
