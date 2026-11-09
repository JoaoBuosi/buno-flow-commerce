# Buno Flow Commerce + Buno Core: primeiro workflow (0.2)

Fluxo: **pedido no Flow Commerce -> Kafka orders.created -> proposta da Agent Platform -> aprovacao -> execucao pelo Core -> auditoria**.

O **Buno Core** comecou como modulo logico de governanca no backend Spring Boot existente, para reduzir custo operacional. Ele ainda **nao e microservico independente**. A **Agent Platform real nao foi conectada**: o script Python abaixo simula o cliente/agente para validar o contrato.

## APIs

Todos os endpoints em `/api/` exigem `X-Buno-Key` enviado apenas por backend confiavel. O tenant e determinado no servidor, sem aceitar identificador de tenant do cliente.

| Endpoint | Papel |
| --- | --- |
| POST /api/orders | STORE |
| GET /api/orders, GET /api/orders/{id} | STORE, APPROVER |
| POST /api/v1/workflows/proposals | AGENT |
| GET /api/v1/workflows | STORE, APPROVER |
| POST /api/v1/workflows/{id}/approve | APPROVER |
| POST /api/v1/workflows/{id}/reject | APPROVER |
| POST /api/v1/workflows/{id}/execute | CORE |
| GET /api/v1/workflows/{id}/audit | STORE, APPROVER |

Exemplo de proposta: `{"orderId":1,"desiredStatus":"REVIEWED","reason":"Revisar pedido","idempotencyKey":"order-1-review-v1"}`

A unica acao permitida neste MVP e `CREATED -> REVIEWED`. Execucao antes da aprovacao retorna 409. O mesmo idempotencyKey nao gera duas propostas. Eventos PROPOSED, APPROVED/REJECTED e EXECUTED sao registrados em Postgres na mesma transacao que as mudancas.

## Execucao local

1. Copie `.env.example` para `.env`, substitua os placeholders por segredos aleatorios longos e distintos.
2. Entre em `02-buno-flow-commerce` e rode `docker compose --env-file .env up --build`.
3. Disponibilize BUNO_STORE_API_KEY, BUNO_AGENT_API_KEY, BUNO_APPROVER_API_KEY e BUNO_CORE_API_KEY no terminal do script Python.
4. Execute `python scripts/demo_agent_workflow.py` para testar criacao, proposta, aprovacao, execucao e auditoria.
5. Para executar testes automatizados use Java 21 e `mvn test`. O pull request tambem executa CI em GitHub Actions.

## Supabase

O projeto `buno-flow-commerce` e o banco PostgreSQL escolhido. O script para **base nova/vazia** esta em `infra/supabase/001_core_workflows.sql`. Configure backend JDBC com TLS e `HIBERNATE_DDL_AUTO=validate` depois da aplicacao do esquema. Nunca exponha credenciais de Postgres ou X-Buno-Key no frontend.

## Limites (ainda nao comercial)

Instancia **single-tenant**, credenciais de servico (nao login de pessoas), sem painel de aprovacao, sem autenticacao/gestao de usuarios, sem filas duraveis, sem outbox transacional, sem retry/DLQ e sem runtime real de Agent Platform. O evento Kafka ainda e publicado sem garantia transacional de entrega; isso sera corrigido no proximo incremento.

Nao publique esta API aberta na internet. Proximas entregas: login/organizacoes, outbox + consumidor idempotente, UI de aprovacao, integracao com Agent Platform real, isolamento multi-tenant verificavel e testes com PostgreSQL real.
