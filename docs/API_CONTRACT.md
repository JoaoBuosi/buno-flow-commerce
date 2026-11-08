# Buno Flow Commerce — Contrato inicial da API

Base path: /api/v1

Este contrato é a referência funcional do MVP. Os DTOs Java devem seguir estes formatos; detalhes de implementação podem evoluir sem quebrar os campos públicos sem versionamento.

## Criar pedido
POST /orders

Request:
{
  "externalOrderId": "LOJA-10042",
  "customerId": "CUS-921",
  "customerName": "Maria Silva",
  "customerEmail": "maria@example.com",
  "currency": "BRL",
  "source": "API",
  "items": [
    {
      "sku": "CAMISETA-001",
      "name": "Camiseta",
      "quantity": 2,
      "unitPrice": 59.90
    }
  ]
}

O backend calcula o total a partir dos itens. Não confiar em total enviado pelo cliente da API. Se o MVP precisar aceitar pedidos sem itens, isso deverá ser uma decisão explícita, não um comportamento implícito.

Response: 201 Created com id, externalOrderId, customerId, totalAmount, currency, status, items, createdAt e updatedAt.

## Listar pedidos
GET /orders?page=0&size=20&status=PROCESSING&customerId=CUS-921&from=2026-01-01&to=2026-01-31

Response:
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}

Limitar size no servidor (máximo inicial: 100).

## Consultar pedido
GET /orders/{id}

Retorna os dados do pedido e seus itens. Pedido inexistente: 404.

## Atualizar status
PATCH /orders/{id}/status

Request:
{
  "status": "PROCESSING",
  "reason": "Pagamento confirmado",
  "source": "DASHBOARD"
}

A transição deve ser validada pelo domínio. Status inválido ou transição não permitida: 409 Conflict. Cada transição bem-sucedida cria um registro de histórico na mesma transação do pedido.

## Histórico
GET /orders/{id}/history

Retorna eventos ordenados cronologicamente, incluindo status anterior, novo status, motivo, origem e data.

## Erros
Formato:
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/orders"
}

Códigos principais: 400 validação/formato; 404 recurso inexistente; 409 conflito de domínio/duplicidade; 500 erro inesperado sem detalhes internos.

## Segurança e idempotência — próximas entregas
- Não publicar endpoints de escrita sem autenticação quando houver dados reais.
- Adicionar Idempotency-Key para criação por integração antes de ativar webhooks/retries.
- Nunca registrar tokens, segredos ou dados pessoais completos nos logs.
