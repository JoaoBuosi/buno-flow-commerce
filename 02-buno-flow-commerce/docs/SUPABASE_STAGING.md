# Flow Commerce — Supabase PostgreSQL dedicado (staging privado)

**Supabase project:** `buno-flow-commerce` — ref `pqdwxihinbmeiorkqdtq`, região `us-east-1`.
**Status 09/11/2026:** Projeto criado pelo proprietário; V1–V3 aplicadas com sucesso usando o conector Supabase; V4 (índices de chaves estrangeiras) também aplicada. Sem dados de clientes. O backend **ainda não foi implantado e conectado remotamente**.

## Esquema de dados

Todas as tabelas da aplicação estão no schema **`flow_commerce`**:
`stores`, `users`, `memberships`, `user_sessions`, `orders`, `order_items`, `order_events`, `order_imports`, `password_resets`.

Supabase SQL migration registrada: `bootstrap_flow_commerce_v1_v3_private_schema`. O SQL é idêntico à sequência das migrations versionadas V1/V2/V3, com prefixo de criação de schema e restrições de privilégios.

O schema **não** deve ser adicionado aos schemas expostos pela Data API Supabase. Verificado: `anon`, `authenticated` e `service_role` não têm USAGE nele. Dados de lojas são sempre acessados através da API Spring Boot, que aplica `store_id` nos filtros de consultas.

## Preparar conexão da API Spring Boot

1. Abrir o [Supabase Connect](https://supabase.com/dashboard/project/pqdwxihinbmeiorkqdtq?showConnect=true) e escolher **Session pooler JDBC**, ou conexão direta apenas quando a hospedagem suportar IPv6. Não usar chave de API Supabase como senha de banco.
2. Configurar os segredos no provedor que hospeda o **backend Java** (não no React/Vite e não no GitHub).
3. Em staging privado, definir:

```dotenv
SPRING_PROFILES_ACTIVE=supabase
SPRING_DATASOURCE_URL=jdbc:postgresql://<HOST_SESSION_POOLER>:5432/postgres?currentSchema=flow_commerce&sslmode=require
SPRING_DATASOURCE_USERNAME=<DATABASE_USERNAME_FROM_CONNECT>
SPRING_DATASOURCE_PASSWORD=<PRIVATE_PASSWORD_IN_HOST_SECRET_STORE>
COMMERCE_CORS_ORIGIN=https://<EXACT_PRIVATE_FRONTEND_DOMAIN>
COMMERCE_PILOT_INVITE_CODE=<SECRET_DEFINED_ONLY_IN_BACKEND_HOST>
DB_POOL_MAX=5
COMMERCE_ML_PILOT_ENABLED=false
COMMERCE_MAIL_ENABLED=false
```

Não há senha real neste arquivo e **não enviar esses segredos pelo chat**. Usar conexão com criptografia TLS e credenciais de banco com acesso mínimo possível ao schema. Revisar o usuário PostgreSQL antes de produção: `postgres` tem privilégios amplos e não deve ser a identidade de longo prazo da API.

**Piloto privado:** o perfil Supabase exige `COMMERCE_PILOT_INVITE_CODE`. Sem ele, o cadastro falha fechado com 503. O código é digitado pelo lojista convidado durante cadastro e enviado somente por HTTPS; não empacotar no React/Vite nem versionar no GitHub. O link Vercel fica protegido por Vercel Authentication.\n\n**Flyway:** o perfil `supabase` usa `baseline-on-migrate=true` e `baseline-version=3` exclusivamente porque o Supabase foi inicializado fora do Flyway. Na primeira partida da API nesse projeto, o Flyway marca V1–V3 como baseline no schema existente. V4 é idempotente e já aplicada via Supabase, então ao rodar pelo Flyway registrará a versão sem duplicar índices. V5+ será gerenciada por Flyway no backend. **Nunca apagar `flyway_schema_history`, não rodar V1–V3 novamente nesse projeto e não habilitar o perfil `supabase` em DB desconhecido.**

Nos testes locais/CI, o perfil Supabase **não** é usado: Flyway executa V1–V3 do zero e o PostgreSQL fica isolado.

## Critérios de aceite da próxima entrega

- [x] Projeto Supabase `buno-flow-commerce` identificado.
- [x] V1–V3 criadas no schema privado.
- [x] Sem acesso público Supabase a `flow_commerce`, confirmado por consulta SQL.
- [x] Código adaptado ao baseline Flyway e script de testes de compatibilidade.
- [ ] Configurar URL JDBC e segredos na hospedagem do backend.
- [ ] Confirmar Flyway baseline real, conexão TLS e isolamento de tenants após start em ambiente remoto.
- [ ] Publicar frontend React na URL privada de staging apontando para a API hospedada.
- [ ] Executar E2E de cadastro, pedido, importação, status, cancelamento, auditoria, exportação e autorização entre lojas.
- [ ] Backups automáticos offsite, restauração no ambiente de hospedagem e observabilidade.
- [ ] Auditoria de segurança, verificação e recuperação de conta por e-mail, termos/LGPD e teste piloto.

Até todos esses gates passarem, manter o acesso privado e não aceitar dados pessoais reais de lojistas.
