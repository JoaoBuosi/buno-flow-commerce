# 02 — Buno Flow Commerce
Order API + PostgreSQL + Kafka, com publicação de `orders.created` e consumidor de fulfillment.
`docker compose up --build`; API `http://localhost:8102`.
`POST /api/orders` body `{"customerId":"customer-42","total":129.90}`; `GET /api/orders`.
O envio Kafka no exemplo ocorre no fluxo transacional sem outbox durável; a documentação deixa o limite explícito. Evoluções sugeridas: transactional outbox, retry/DLQ, schema registry, saga de pagamento/estoque e consumidor idempotente.
