# Buno Flow Commerce — staging comercial 0.4 (privado)

## Recursos verificados

- Frontend React: Vercel project `buno-flow-commerce-staging-web`, projeto ID `prj_9B8xiQsHwAu79Cd8dYUirqgyAAPL`.
- URL protegida (Vercel Authentication para ALL deployments): https://buno-flow-commerce-staging-jsffagrlq-joaobuosis-projects.vercel.app
- Deploy Vercel id `dpl_8KsNHQcF8LtjiNyLDsHn7T8S4gn9`, estado READY, teste HTTP 200.
- O Java Spring Boot **NÃO está hospedado ainda**. A interface falha de maneira segura quando `VITE_API_URL` está ausente.
- Banco Supabase existente `buno-flow-commerce` (ref `pqdwxihinbmeiorkqdtq`), schema privado `flow_commerce` com 9 tabelas. Bootstrap Supabase V1–V4 aplicado fora do Flyway, e baseline Flyway V3 + V4 idempotente verificados no GitHub Actions.
- PR de desenvolvimento `https://github.com/JoaoBuosi/buno-flow-commerce/pull/1`; nenhuma alteração incorporada em `main`.

## Testes automatizados comprovados (staging funcional ainda pendente de API remota)

- GitHub Actions: https://github.com/JoaoBuosi/buno-flow-commerce/actions/runs/42635767476
- Playwright 1.65.0 + Chromium: **1 cenário completo aprovado**, incluindo cadastro convidado, pedidos, status, importação/exportação CSV, auditoria, cancelamento, isolamento entre lojas, logout e novo login.
- Java/Spring Boot + PostgreSQL real efêmero em CI: https://github.com/JoaoBuosi/buno-flow-commerce/actions/runs/42635767434 — backend e React verdes.
- Esses testes **não** acessaram o Supabase de produção/staging e não validam integração remoto-para-remoto.

## Implantar backend no Railway (requer conexão autorizada pelo proprietário)

O app Railway foi sugerido; não existe conexão Railway confirmada nesta conversa. **Não presumir que o backend foi implantado.** Com a conexão disponível:

1. Criar projeto/serviço Railway com deploy a partir do GitHub `JoaoBuosi/buno-flow-commerce`.
2. Selecionar branch `feat/commerce-saas-mvp`. Configurar Root Directory `/02-buno-flow-commerce` e Config as Code path `/02-buno-flow-commerce/railway.json`. Builder: Dockerfile, Java 21, serviço single-instance de staging.
3. Criar segredos **diretamente na hospedagem**, nunca em arquivos, no frontend, em chat ou no GitHub:

```dotenv
SPRING_PROFILES_ACTIVE=supabase
SPRING_DATASOURCE_URL=jdbc:postgresql://<SESSION_POOLER_HOST>:5432/postgres?currentSchema=flow_commerce&sslmode=require
SPRING_DATASOURCE_USERNAME=<SUPABASE_SESSION_POOLER_USERNAME>
SPRING_DATASOURCE_PASSWORD=<DATABASE_PASSWORD>
COMMERCE_CORS_ORIGIN=https://buno-flow-commerce-staging-jsffagrlq-joaobuosis-projects.vercel.app
COMMERCE_PILOT_INVITE_CODE=<RANDOM_SECRET_SHARED_ONLY_WITH_TESTERS>
COMMERCE_MAIL_ENABLED=false
COMMERCE_ML_PILOT_ENABLED=false
DB_POOL_MAX=5
```

O backend deve falhar fechado para cadastro sem código de convite no profile `supabase`. Ativar envio de recuperação de senha só quando houver HTTPS e SMTP real configurado. Nunca usar chave publicável `anon` como senha de PostgreSQL. Evitar usuário `postgres` com superprivilégios em produção; preparar role de acesso mínimo.

4. Antes de atribuir domínio público ao serviço, concluir a revisão de autenticação, abuso e logs, e definir controles de acesso à API. Um domínio Railway é acessível pela internet, **mesmo se o frontend estiver atrás da autenticação Vercel**. O código de convite não substitui a autenticação de cada usuário nem um gateway/WAF.
5. Após subir o container, consultar `GET /actuator/health`; verificar conexão TLS, histórico Flyway baseline V3, V4, esquema de tenants e erro seguro de credenciais erradas. Não imprimir conexões completas ou passwords nos logs.

## Ativar frontend somente quando a API está disponível

Na Vercel, configurar **Preview/Staging** `VITE_API_URL=https://<BACKEND_VERIFIED_HOST>/api/v1` e criar um novo deployment privado. A Vite injeta `VITE_*` no bundle; somente a **URL pública HTTPS** pode aparecer ali, jamais passwords ou banco.

## Aceite de e2e

O workflow `.github/workflows/flow-commerce-e2e.yml` testa localmente navegador Chromium + React Vite + API Spring Boot + PostgreSQL real efêmero, com:
- Cadastro convidado, autenticação e sessão.
- Pedidos com cálculo no servidor e persistência.
- Mudança de status e auditoria.
- Importação CSV com validação, exportação CSV.
- Cancelamento com justificativa.
- Nova loja sem acesso aos pedidos da primeira, headers trocados recusados.
- Logout e login com dados persistentes.

Esses testes **não** são prova do deploy remoto: repetir no staging privado protegido após integração com Railway. Antes de abrir para lojistas reais: revisão de segurança, backups externos no Supabase com restauração comprovada, política LGPD, verificação de e-mail, monitoramento e autorização explícita para produção.

## Limite comercial

Este estágio **não é produção pública** e não deve ser promovido automaticamente. Não colocar contatos reais, dados sensíveis ou dados de empresas clientes até autorização e validação operacional.
