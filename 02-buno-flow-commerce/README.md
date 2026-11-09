# Buno Flow Commerce — backend MVP em desenvolvimento

Backend Java 21 / Spring Boot 3.4.5 / PostgreSQL 16 com Flyway e autenticação por token aleatório com hash SHA-256 persistido em banco. **Implementação em validação, não liberada para clientes reais**.

## Rodar no Windows PowerShell

Configure uma senha de desenvolvimento:
```powershell
$env:DB_PASSWORD = "senha-local-forte"
docker compose up -d --build
```
A API estará em http://localhost:8102 e o health check em http://localhost:8102/actuator/health. PostgreSQL persiste em volume Docker, sem expor a porta do banco à rede do host.

## Fluxo
```powershell
$body = @{email="lojista@example.test";password="senha-segura-2026";storeName="Minha Loja"} | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri http://localhost:8102/api/v1/auth/register -ContentType application/json -Body $body
$headers = @{Authorization="Bearer $($session.accessToken)";"X-Store-Id"="$($session.storeId)"}
$pedido = @{customer="Cliente Teste";items=@(@{sku="SKU-1";description="Produto";quantity=2;price=39.90})} | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Uri http://localhost:8102/api/v1/orders -Headers $headers -ContentType application/json -Body $pedido
Invoke-RestMethod -Uri http://localhost:8102/api/v1/orders -Headers $headers
```

## Endpoints
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/me`
- `POST /api/v1/orders`
- `GET /api/v1/orders?page=0&size=20&status=CREATED`
- `GET /api/v1/orders/{id}`
- `PATCH /api/v1/orders/{id}/status` — um avanço por vez
- `POST /api/v1/orders/{id}/cancel` — exige motivo
- `GET /api/v1/orders/{id}/history`

Envie `Authorization: Bearer <token>` e `X-Store-Id: <uuid>` nos endpoints protegidos. O servidor valida a associação usuário/loja; nunca confia apenas no cabeçalho de loja.

## Testes
```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/orders_test"
$env:SPRING_DATASOURCE_USERNAME="app"
$env:SPRING_DATASOURCE_PASSWORD="senha-local-forte"
mvn test
```
A suíte de integração espera PostgreSQL real com um banco `orders_test` isolado. O workflow GitHub Actions provisiona o banco automaticamente.

## Limites atuais
- Ainda não há recuperação de senha ou gestão de funcionários.
- Importação CSV da demo ainda não está conectada à API.
- Sem gateway de pagamento, envio WhatsApp ou marketplace.
- Sem teste de reinicialização comprovado e sem revisão de segurança para produção.
- O repositório contém código legado desativado; verificar migrations com base de dados anterior antes de atualizar instâncias existentes.
- A Vercel hospeda apenas a **demo**, não o backend Spring Boot. Configurar um host Java com TLS e PostgreSQL antes de conectar frontend real.

**Não vincular dados de clientes reais antes de completar segurança, testes, backup e validação.**
