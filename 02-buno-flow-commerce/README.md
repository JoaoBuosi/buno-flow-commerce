# Buno Flow Commerce — adaptador do Buno Core (0.2)

Flow Commerce cuida de **pedidos e dominio comercial**. Aprovações, propostas de agentes, auditoria de workflow e execução supervisionada pertencem ao serviço **Buno Core independente**. Nenhuma tabela de workflow e criada pelo Flow Commerce neste branch.

## Contrato

- `POST /api/orders`: cria pedido; requer chave de backend STORE.
- `GET /api/orders`, `GET /api/orders/{id}`: consulta pedidos da loja configurada.
- `GET /api/orders/{id}/workflows`: o backend consulta Buno Core (`GET /v1/workflows?source=flow-commerce&resource_id={id}`) usando credencial READER privada; verifica antes se pedido pertence ao tenant.
- `POST /api/internal/orders/{id}/mark-reviewed`: somente chave CORE; body `{"workflowId":"UUID"}`; transicao idempotente CREATED -> REVIEWED. Outros status nao podem ser enviados.
- `orders.created` e publicado para posterior consumo por Agent Platform. A entrega Kafka atual **nao e transacional**: necessario transactional outbox antes de comercializar.

A chave de serviço jamais pode ficar no frontend. Instancia atual utiliza um tenant configurado no backend e nao oferece autenticacao de lojistas nem multi-tenant comercial.

## Rodar com Buno Core

1. Crie a rede Docker compartilhada: `docker network create buno-ecosystem`.
2. No projeto do Buno Core configure `.env` com as suas credenciais e inicie `docker compose --env-file .env up --build`.
3. No Flow Commerce copie `.env.example` para `.env` e configure as quatro variaveis BUNO_* e a URL do Core.
4. Garanta que `FLOW_COMMERCE_CORE_KEY` do Core seja exatamente igual a `BUNO_CORE_API_KEY` no Flow; `BUNO_CORE_READ_KEY` no Flow seja uma credencial `READER` configurada no Core.
5. Inicie `docker compose --env-file .env up --build` nesta pasta.
6. Testes Java 21 + Maven: `mvn test` ou GitHub Actions do PR.

## Banco de dados

O Flow Commerce usa `orders` no seu PostgreSQL. Se usar a base Supabase ja criada, aplique apenas a migracao incremental `infra/supabase/002_order_workflow_reference.sql` depois de validar os dados; nao exclua automaticamente as tabelas antigas de workflow. O Core possui banco **separado**.

**Estado:** integracao por contrato e testes de adaptador. Falta login de humanos/organizacoes, conector real da Agent Platform e garantir entrega de eventos via outbox. Nao publicar API diretamente na internet.
