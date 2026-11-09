# Flow Commerce 0.4 — Staging protegido (09/11/2026)

## Estado real da entrega

**Railway API Spring Boot: implantada e saudável.**
- Projeto: `buno-flow-commerce-staging` (id `4782a855-711d-4cc5-b673-a725b3f2d823`).
- Ambiente: `staging` (id `3ee3eb6f-cfa0-47ac-bfea-4043910f15f7`).
- Serviço: `flow-commerce-api` (id `9ae95dd7-c461-494a-a9a5-2fc41bf00420`).
- Deploy confirmado `SUCCESS`: `349f310c-684a-46f6-ad2c-089f88b5dd64`, commit `6fc70621f0ecc8992798e2062a75506e9cefcd7b`.
- Java 21 / Spring Boot 3.4.5, porta 8080, profile `supabase`. Logs confirmaram Hikari obtendo conexão PostgreSQL e Railway aprovando o health check.
- Domínio HTTPS de infraestrutura protegido por segredo compartilhado, NÃO uma API pública aberta. A `/actuator/health` está acessível para o monitoramento do Railway; os endpoints `/api/v1/**` exigem `X-Buno-Edge-Key` **antes** da autenticação por lojista, além de convite para novos cadastros.

**Supabase:**
- Projeto dedicado `buno-flow-commerce`, ref `pqdwxihinbmeiorkqdtq`, schema não exposto `flow_commerce`, 9 tabelas e índices. Bootstrap SQL V1–V4 já aplicado, Flyway desligado apenas nesse serviço (role operacional sem privilégio DDL).
- Role de runtime `flow_commerce_staging_app` com privilégios apenas de DML no schema dedicado. Credenciais JDBC em segredos Railway e conexão com TLS obrigatório.
- Em 09/11/2026, consultas constataram **0 usuários e 0 pedidos reais**. Não usar dados pessoais de clientes antes de revisão.

**Vercel React: publicado, protegido, gateway criado.**
- Projeto Vercel separado `buno-flow-commerce-staging-web`, id `prj_9B8xiQsHwAu79Cd8dYUirqgyAAPL`, com Vercel Authentication habilitada em TODOS os deployments.
- Último deployment `READY` `dpl_2SJ2SqcsMphqwKJrLSTfpQz3KNLa`:
  `https://buno-flow-commerce-staging-pb2a7ief8-joaobuosis-projects.vercel.app`
- `VITE_API_URL=same-origin` (sem credenciais no bundle). `/api/v1/:path*` é reescrito para a função Node `/api/gateway`, que injeta a credencial de borda guardada na Vercel e encaminha apenas headers de negócio relevantes.
- `COMMERCE_EDGE_SECRET` foi gerado via CSPRNG e enviado diretamente aos cofres de segredos Railway e Vercel, sem registrar o valor no GitHub. `COMMERCE_UPSTREAM_ORIGIN` somente nas variáveis do Vercel.
- A inspeção autenticada via conector confirmou HTML da SPA retornando HTTP 200 e função `out/api/gateway` compilada. **A verificação remota de `/api/v1/orders` pelo conector foi barrada pela autenticação SSO (401) antes da função**, portanto a integração browser→gateway→API ainda NÃO foi comprovada ponta a ponta. Não reportar que o staging já pode ser usado por lojistas.

## Evidências automatizadas verificadas
- Testes Java/Postgres: **16/16**, incluindo 3 novos de edge secret e 3 de cadastro/segurança.
- Testes unitários Vercel gateway: **4/4**.
- Verificador Python de publicação: **4/4**.
- Teste Playwright Chrome de cadastro com convite, criação de pedidos, importação/exportação CSV, mudança de status, cancelamento, auditoria, isolamento entre duas lojas e logout/relogin: **1 cenário aprovado** com Spring Boot + PostgreSQL efêmero no GitHub Actions. Isto **não é** E2E no Supabase hospedado.
- CI backend https://github.com/JoaoBuosi/buno-flow-commerce/actions/runs/42646082381
- CI navegador https://github.com/JoaoBuosi/buno-flow-commerce/actions/runs/42647772380
- CI UI com gateway explícito https://github.com/JoaoBuosi/buno-flow-commerce/actions/runs/42647772366

## Próximo teste com autenticação Vercel

1. Entrar na Vercel com usuário autorizado e abrir a URL protegida de staging.
2. Na aba Network do navegador, confirmar que `/api/v1/orders` responde **401 (sem sessão do lojista)**, e NÃO 403 (secret ausente), 404 (rewrite) ou 502/503 (rede indisponível).
3. Com código de convite guardado no Railway, criar lojas **sintéticas** A e B; validar cadastro, login, pedidos, ciclo de status, importação CSV, exportação, cancelamento, auditoria, logout/relogin e verificação de isolamento via header alterado. Não divulgar código de convite em mensagens ou capturas de tela.
4. Conferir no Supabase contagens criadas e logs de auditoria; excluir dados sintéticos somente quando houver plano de limpeza auditável. Não usar clientes reais ainda.
5. Concluir backup/restauração remota, SMTP seguro, verificação de e-mail, monitoramento, proteção de abuso centralizada, LGPD/termos e controles de produção.

## Segurança e lançamento

- Staging não é produção comercial. A API possui endereço técnico na internet, mas não aceita requisições do navegador diretamente sem segredo de gateway.
- O frontend continua protegido pela Vercel Authentication. Nenhum segredo SQL ou de gateway é disponibilizado via `VITE_*`.
- **Não** retirar os controles de proteção, não mesclar o PR em `main` e não comercializar antes de concluir testes remotos e auditoria de segurança.
