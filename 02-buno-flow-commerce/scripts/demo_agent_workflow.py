#!/usr/bin/env python3
"""Simulador da Agent Platform: Flow -> proposta -> aprovacao -> execucao -> auditoria.
Necessario backend Flow Commerce rodando e variaveis BUNO_*_API_KEY configuradas.
NAO representa um agente de IA real; so demonstra o contrato de integracao.
"""
import json
import os
import urllib.request
import uuid

BASE = os.getenv("FLOW_COMMERCE_URL", "http://localhost:8102").rstrip("/")

def call(method, path, role, payload=None):
    key = os.environ["BUNO_" + role + "_API_KEY"]
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(
        BASE + path, method=method, data=data,
        headers={"X-Buno-Key": key, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as response:
        return json.load(response)

def main():
    order = call("POST", "/api/orders", "STORE",
                 {"customerId": "cliente-demonstracao", "total": 129.90})
    print("ORDER", order["id"], order["status"])
    workflow = call("POST", "/api/v1/workflows/proposals", "AGENT", {
        "orderId": order["id"],
        "desiredStatus": "REVIEWED",
        "reason": "Agente solicita revisao manual do pedido",
        "idempotencyKey": "demo-" + str(uuid.uuid4()),
    })
    path = "/api/v1/workflows/" + workflow["id"]
    print("PROPOSAL", workflow["id"], workflow["status"])
    # Em producao, esta aprovacao vem de um usuario autenticado no painel Buno.
    workflow = call("POST", path + "/approve", "APPROVER")
    print("APPROVAL", workflow["status"])
    workflow = call("POST", path + "/execute", "CORE")
    print("EXECUTION", workflow["status"])
    print("ORDER AFTER", call("GET", "/api/orders/" + str(order["id"]), "STORE")["status"])
    print("AUDIT", json.dumps(call("GET", path + "/audit", "APPROVER"), indent=2))

if __name__ == "__main__":
    main()
