# Buno Flow Commerce — backend MVP em desenvolvimento

Banco no schema privado `flow_commerce`, fora do schema `public` exposto pela Data API do Supabase. Configurar `currentSchema=flow_commerce` na URL JDBC e `sslmode=require` no ambiente hospedado. Não conceder acesso ao schema a `anon`, `authenticated` ou `service_role` nem ativá-lo entre os schemas expostos. O banco e credenciais são exclusivos deste produto.

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

## Importação CSV
Envie multipart/form-data com campo file, autenticação e cabeçalho de loja para POST /api/v1/orders/import. Cabeçalho: order_key,customer,sku,description,quantity,price. Linhas agrupadas por order_key. Toda a operação reverte em caso de erros ou duplicatas. Máximo de 500 linhas e 1 MB.

## Endpoints
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/me`
- `POST /api/v1/orders/import` — CSV com chave externa única por loja
- `POST /api/v1/orders`
- `GET /api/v1/orders?page=0&size=20&status=CREATED`
- `GET /api/v1/orders/{id}`
- `PATCH /api/v1/orders/{id}/status` — um avanço por vez
- `POST /api/v1/orders/{id}/cancel` — exige motivo
- `GET /api/v1/orders/{id}/history`

Envie `Authorization: Bearer <token>` e `X-Store-Id: <uuid>` nos endpoints protegidos. O servidor valida a associação usuário/loja; nunca confia apenas no cabeçalho de loja.

## Testes
```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/orders_test?currentSchema=flow_commerce"
$env:SPRING_DATASOURCE_USERNAME="app"
$env:SPRING_DATASOURCE_PASSWORD="senha-local-forte"
mvn test
```
A suíte de integração espera PostgreSQL real com um banco `orders_test` isolado. O workflow GitHub Actions provisiona o banco automaticamente.

## Limites atuais
- Recuperação de senha implementada, mas envio depende de SMTP/HTTPS configurados; gestão de funcionários não disponível.
- CSV possui endpoint transacional; frontend React deve utilizá-lo.
- Sem gateway de pagamento, envio WhatsApp ou marketplace.
- Há testes automatizados de reinicialização, backup e restore em PostgreSQL local; falta revisão independente de segurança e confirmação na infraestrutura real.
- O repositório contém código legado desativado; verificar migrations com base de dados anterior antes de atualizar instâncias existentes.
- A Vercel hospeda apenas a **demo**, não o backend Spring Boot. Configurar um host Java com TLS e PostgreSQL antes de conectar frontend real.

**Não vincular dados de clientes reais antes de completar segurança, testes, backup e validação.**

## Staging Supabase dedicado
Projeto privado `buno-flow-commerce` no Supabase (ref `pqdwxihinbmeiorkqdtq`). V1–V3 criadas no banco e V4 de índices aplicada. A conexão real com a API Java requer segredos JDBC configurados no host. Usar perfil `supabase` que faz baseline Flyway em V3 e execução idempotente de V4; veja `docs/SUPABASE_STAGING.md`.
