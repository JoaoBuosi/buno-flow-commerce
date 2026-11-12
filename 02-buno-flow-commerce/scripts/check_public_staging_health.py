#!/usr/bin/env python3
"""Read-only availability probe for Buno Flow Commerce.

Probes only HTTPS Railway app health and a private Vercel staging origin.
Never authenticates, writes orders, records credentials or bypasses SSO.
"""
from __future__ import annotations

import argparse
import json
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def origin(text: str, kind: str) -> str:
    u = urlsplit(text.rstrip("/"))
    if (u.scheme != "https" or not u.hostname or u.username or u.password or
        u.port is not None or u.path not in ("", "/") or u.query or u.fragment):
        raise ValueError("Exige origem HTTPS limpa sem porta, caminho, usuário ou query")
    host = u.hostname.lower()
    if kind == "api":
        if not host.endswith(".up.railway.app") or host.count(".") < 3:
            raise ValueError("Somente domínio Railway .up.railway.app para API")
    elif kind == "frontend":
        if not host.endswith(".vercel.app") or host.count(".") < 2:
            raise ValueError("Somente domínio de implantação privada .vercel.app")
    else:
        raise ValueError("Tipo de origem desconhecido")
    return "https://" + host


def probe(url: str) -> tuple[int, bytes]:
    req = Request(url, method="GET", headers={
        "User-Agent": "BunoFlowCommerceStagingAvailability/0.5.3",
        "Cache-Control": "no-store",
    })
    try:
        with urlopen(req, timeout=15) as response:
            return response.status, response.read(2048)
    except HTTPError as exc:
        return exc.code, exc.read(2048)


def check(api: str, frontend: str) -> list[tuple[str, bool]]:
    results: list[tuple[str, bool]] = []
    try:
        status, body = probe(api + "/actuator/health")
        up = status == 200 and json.loads(body.decode("utf-8")).get("status") == "UP"
        results.append(("Railway API e banco: health UP",up))
    except (ValueError, UnicodeDecodeError, URLError, TimeoutError, OSError):
        results.append(("Railway API e banco: health UP",False))
    try:
        status, _ = probe(api + "/api/v1/orders")
        results.append(("Railway bloqueia acesso direto sem gateway",status == 403))
    except (URLError, TimeoutError, OSError):
        results.append(("Railway bloqueia acesso direto sem gateway",False))
    try:
        status, _ = probe(frontend + "/")
        # The Vercel page may show an auth challenge; this does NOT validate
        # frontend login, serverless gateway, merchant session or SSO rules.
        results.append(("Vercel acessível (conteúdo pode exigir SSO)",status in (200, 401, 403)))
    except (URLError, TimeoutError, OSError):
        results.append(("Vercel acessível (conteúdo pode exigir SSO)",False))
    return results


def main() -> int:
    parser = argparse.ArgumentParser(description="Disponibilidade somente leitura; sem tokens nem dados de pedidos")
    parser.add_argument("--api-origin", required=True)
    parser.add_argument("--frontend-origin", required=True)
    args = parser.parse_args()
    try:
        api = origin(args.api_origin, "api")
        frontend = origin(args.frontend_origin, "frontend")
    except ValueError as exc:
        print("CONFIGURAÇÃO INVÁLIDA: " + str(exc), file=sys.stderr)
        return 2
    checks = check(api, frontend)
    for name, passed in checks:
        print(("PASSOU" if passed else "FALHOU") + " | " + name)
    print("LIMITAÇÃO: verificação pública não valida login, escrita nem restauração do banco.")
    return 0 if all(passed for _, passed in checks) else 1


if __name__ == "__main__":
    raise SystemExit(main())
