# Flow Commerce 0.4 — Railway staging handoff (2026-11-09)

**Status: preparada, mas NÃO publicada como API.**

## Infraestrutura real

- Supabase PostgreSQL: projeto exclusivo `buno-flow-commerce`, ref `pqdwxihinbmeiorkqdtq`, região `us-east-1`, schema privado `flow_commerce`, 9 tabelas, migrations V1–V4 aplicadas.
- Railway projeto privado: `buno-flow-commerce-staging` (id `4782a855-711d-4cc5-b673-a725b3f2d823`), ambiente `staging` (id `3ee3eb6f-cfa0-47ac-bfea-4043910f15f7`), serviço ainda sem deployment `flow-commerce-api` (id `9ae95dd7-c461-494a-a9a5-2fc41bf00420`).
- Vercel frontend privado: `buno-flow-commerce-staging-web` (projeto `prj_9B8xiQsHwAu79Cd8dYUirqgyAAPL`). Link verificado: https://buno-flow-commerce-staging-jsffagrlq-joaobuosis-projects.vercel.app. Vercel Authentication ativada em todos os deployments.
- GitHub PR de trabalho: https://github.com/JoaoBuosi/buno-flow-commerce/pull/1 (branch `feat/commerce-saas-mvp`; não mesclar antes de pronto).

## O que já foi configurado no Railway

- Serviço vazio foi criado, sem domínio público e sem processo em execução.
- Configurações **staged**: root `/02-buno-flow-commerce`, Dockerfile `Dockerfile`, health `/actuator/health`, IPv6 egress para alcançar diretamente o host Supabase.
- Variáveis **staged** no Railway: `SPRING_PROFILES_ACTIVE`, `SPRING_FLYWAY_ENABLED`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `COMMERCE_CORS_ORIGIN`, `COMMERCE_PILOT_INVITE_CODE`, `COMMERCE_MAIL_ENABLED`, `COMMERCE_ML_PILOT_ENABLED`, `DB_POOL_MAX`. Os valores sensíveis foram gerados e encaminhados diretamente entre os serviços, sem inclusão no GitHub, no frontend ou nesta documentação.
- Conta exclusiva no PostgreSQL para a API: `flow_commerce_staging_app`, com USAGE no schema privado e SELECT/INSERT/UPDATE/DELETE somente nas tabelas da aplicação; sem CREATE no schema. As permissões foram testadas. `anon` não tem acesso.
- TLS exigido (`sslmode=require`), banco acessado somente via JDBC backend (nunca por anon key no frontend).
- **Migrations durante staging:** a aplicação executará com `SPRING_FLYWAY_ENABLED=false`, porque V1–V4 foram aplicadas previamente via Supabase e o usuário de aplicação tem somente privilégios DML. Novas alterações devem ser versionadas no repositório e aplicadas no Supabase antes de implantar a nova versão. Não tentar rodar migrations com o usuário limitado.

## BLOQUEIO ATUAL

**Railway ainda não tem uma conta GitHub vinculada.** A tentativa de conectar `JoaoBuosi/buno-flow-commerce` foi recusada com: `No GitHub account linked. Please connect a GitHub account to perform this action.`

Ação necessária do proprietário: entrar no Railway e conectar sua conta GitHub; garantir acesso do app Railway ao repositório `JoaoBuosi/buno-flow-commerce`. Não enviar tokens/segredos por chat.

Depois da vinculação:
1. Conectar repo à fonte do serviço pelo Railway, branch `feat/commerce-saas-mvp`, idealmente preso ao commit revisado.
2. Revisar o patch staged e confirmar deploy. Não adicionar domínio público antes de conferir logs, migrations desligadas, conexão ao PostgreSQL e acesso autorizado.
3. Testar que `/actuator/health` retorna UP no ambiente real e que não há secrets em logs.
4. Gerar domínio HTTPS do Railway para a API; domínio é tecnicamente público, mesmo com frontend protegido. Manter autenticação/convite e aplicar proteção contra abuso, ingress, rate limiting e revisão de segurança.
5. Configurar no Vercel, em ambiente staging, somente `VITE_API_URL=https://<DOMINIO_API_VERIFICADO>/api/v1`. Não usar chave do Supabase ou credenciais JDBC em `VITE_*`.
6. Reimplantar frontend protegido e validar via navegador contra API + Supabase reais.

## Evidência automatizada que já existe

- CI Java/PostgreSQL: 10 testes de integração, backup/restore, reinício e baseline Flyway.
- Playwright Chromium: cenário único completo aprovado no CI contra banco efêmero, contemplando convite, cadastro, pedido com itens, pagamento, CSV, cancelamento, histórico, exportação, isolamento entre duas lojas, logout e relogin.
- Resultado: https://github.com/JoaoBuosi/buno-flow-commerce/actions/runs/42635767476
- **Esses testes NÃO são execução remota contra Supabase/Railway.** Exigir novo teste pós-deploy sem dados pessoais de clientes.

## Gates antes de receber lojistas reais ou cobrar

HTTPS, limitação de abuso centralizada, restauração de backup offsite, monitoramento, autorização/invite controlado, SMTP seguro e recuperação de senha, verificação de e-mail, documentos LGPD, validação multi-tenant remota, regras de cobrança/atendimento e aprovação de lançamento.
