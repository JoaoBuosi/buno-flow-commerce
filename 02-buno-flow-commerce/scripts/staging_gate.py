#!/usr/bin/env python3
"""Non-destructive release probe for a private staging API.

Reads no tokens or DB passwords. Does not create accounts or orders.
An invitation-less registration attempt must be denied before database writes.
"""
from __future__ import annotations

import argparse
import json
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def origin(text: str) -> str:
    u = urlsplit(text.rstrip("/"))
    if u.scheme != "https" or not u.hostname or u.username or u.password or u.path not in ("", "/") or u.query or u.fragment:
        raise ValueError("Use somente uma origem HTTPS, sem caminho, usuário ou consulta.")
    return f"https://{u.netloc}"


def fetch(url: str, method: str = "GET", headers: dict[str, str] | None = None,
          body: bytes | None = None) -> tuple[int, dict[str, str], bytes]:
    req = Request(url, method=method, headers=headers or {}, data=body)
    try:
        with urlopen(req, timeout=15) as response:
            return response.status, dict(response.headers.items()), response.read(2048)
    except HTTPError as e:
        return e.code, dict(e.headers.items()), e.read(2048)


def header(headers: dict[str, str], name: str) -> str:
    return next((value for key, value in headers.items() if key.lower() == name.lower()), "")


def main() -> int:
    parser = argparse.ArgumentParser(description="Verifica saúde e segurança do staging sem gravar dados.")
    parser.add_argument("--api-origin", required=True, help="https://<host-da-api-railway>")
    parser.add_argument("--frontend-origin", required=True, help="https://<frontend-vercel>")
    args = parser.parse_args()
    try:
        api, frontend = origin(args.api_origin), origin(args.frontend_origin)
        if api == frontend:
            raise ValueError("A API e o frontend devem ter origens diferentes.")
    except ValueError as e:
        print(f"CONFIGURAÇÃO INVÁLIDA: {e}", file=sys.stderr)
        return 2

    checks: list[tuple[str, bool, str]] = []
    def check(name: str, passed: bool, hint: str = "") -> None:
        checks.append((name, passed, hint))
        print(f"{'PASSOU' if passed else 'FALHOU'} | {name}" + (f" | {hint}" if not passed and hint else ""))

    try:
        status, headers, body = fetch(api + "/actuator/health")
        try:
            state = json.loads(body.decode()).get("status")
        except (UnicodeDecodeError, ValueError):
            state = None
        check("API HTTPS responde e banco/health UP", status == 200 and state == "UP",
              f"HTTP {status}, health={state!r}")

        protected = ("/api/v1/orders", "/api/v1/orders/export", "/api/v1/orders/count",
                     "/api/v1/dashboard", "/api/v1/store", "/api/v1/insights/demand")
        for endpoint in protected:
            code, h, _ = fetch(api + endpoint)
            ok = code == 401 and header(h, "Cache-Control") == "no-store"
            check(f"{endpoint} rejeita sessão ausente e impede cache", ok, f"HTTP {code}")

        payload = json.dumps({"email": "no-invite@example.test",
            "password": "safe-test-password-no-invite", "storeName": "NEVER CREATE"}).encode()
        code, _, _ = fetch(api + "/api/v1/auth/register", "POST", {
            "Content-Type": "application/json"}, payload)
        check("Cadastro sem convite recusado no backend", code == 403, f"HTTP {code}")
        # Note: HTTP 503 indicates missing deployment secret, which also blocks onboarding
        # but is not considered a configured, usable staging.

        preflight = {"Origin": frontend, "Access-Control-Request-Method": "GET",
                     "Access-Control-Request-Headers": "authorization,x-store-id"}
        code, headers, _ = fetch(api + "/api/v1/orders", "OPTIONS", preflight)
        check("CORS permite apenas frontend de staging declarado",
              code == 200 and header(headers, "Access-Control-Allow-Origin") == frontend,
              f"HTTP {code}")
        bad_origin = "https://unexpected-origin.invalid"
        code, headers, _ = fetch(api + "/api/v1/orders", "OPTIONS", {
            **preflight, "Origin": bad_origin})
        check("CORS recusa origem não autorizada",
              code == 403 and not header(headers, "Access-Control-Allow-Origin"),
              f"HTTP {code}")
    except (URLError, TimeoutError, OSError) as e:
        # Never print headers, passwords, tokens or server traces.
        print(f"FALHOU | Rede/TLS indisponível ({type(e).__name__}).", file=sys.stderr)
        return 1

    failed = sum(not success for _, success, _ in checks)
    print(f"RESULTADO: {len(checks) - failed}/{len(checks)} verificações aprovadas")
    return int(failed > 0)


if __name__ == "__main__":
    raise SystemExit(main())
