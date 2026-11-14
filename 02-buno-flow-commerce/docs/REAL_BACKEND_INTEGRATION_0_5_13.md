# Flow Commerce 0.5.13 — Integração do backend real

**Status: código e testes em revisão; sem publicação com dados reais.**

Esta etapa reutiliza a API Java 21 / Spring Boot / Flyway que já existe. Não substitui o backend, não executa migrações em produção e não altera o Supabase.

## Implementação
- O frontend React em build de produção só aceita VITE_API_URL=same-origin; tokens e senha não são enviados diretamente a URLs Railway arbitrárias no navegador.
- O gateway existente do Vercel recebe /api/v1/* e usa COMMERCE_UPSTREAM_ORIGIN e COMMERCE_EDGE_SECRET apenas no servidor. Seu segredo deve ser fornecido no cofre aprovado ao Vercel e Railway; nunca por VITE_*.
- Testes Node validam roteamento, POST, repasse de Authorization e X-Store-Id, bloqueio de cookies, redirecionamentos e métodos inseguros.
- Testes Playwright sobem a API Spring Boot **real** com PostgreSQL **17 descartável**, comprovando login por convite, criação de pedido, gravação persistida após recarregar o browser, consulta autenticada e isolamento entre duas lojas.
- CI não se conecta ao Supabase: usa somente banco Postgres17 local e lojas fictícias example.test.

## Não confundir com a demonstração atual

A demonstração mobile da branch qa/flow-commerce-0511-mobile-synthetic-preview ainda é uma simulação no navegador. O Railway staging existente utiliza backend de branch 0.5.6 e não deve receber testes de escrita sem comprovada separação do banco. Não alteramos essa implantação.

## Para publicar frontend e backend reais num preview autorizado
1. Preparar banco PostgreSQL17 **separado do Supabase real**, com backup, retenção e orçamento aprovados.
2. Implantar API 0.5.13 em serviço privado e conectar somente ao banco QA. Migrar esquema no QA e executar testes com contas fictícias.
3. Gerar segredo de gateway no vault; configurar COMMERCE_EDGE_SECRET somente no servidor Railway e Vercel e COMMERCE_UPSTREAM_ORIGIN no Vercel. Manter VITE_API_URL=same-origin.
4. Conferir gateway exige o segredo e rejeita chamadas diretas não autorizadas; manter proteção de acesso ao projeto Vercel.
5. Testar pelo celular as rotas e funções reais (login, pedidos, CSV, auditoria, status, duas lojas). Desabilitar cobranças e email real.
6. Conferir todos os gates de lançamento da 0.5.12 antes de inserir dados de clientes reais.

## Backup real: ainda não concluído

A role flow_backup_export foi criada com NOLOGIN e sem senha, e não pode exportar. O conector Supabase aqui disponível **não oferece exportação de backup PostgreSQL ou transferência criptografada** para cofre privado. Não é permitido copiar dados pessoais para mensagens ou artefatos CI. O procedimento autorizado continua documentado em docs/REAL_BACKUP_WINDOWS_0_5_12.md e exige senha provisionada no cofre, BitLocker/volume cifrado, pg_dump e pg_restore em PostgreSQL 17 local isolado.

Backups gerenciados (quando o plano do Supabase os oferece) podem ser conferidos no Dashboard → Database → Backups; a presença e integridade precisam ser verificadas, e a restauração isolada continua necessária.

## Aceite
- [x] Integração do frontend para gateway da mesma origem e testes de contrato escritos.
- [x] Workflow Java + PostgreSQL 17 real em CI com dados descartáveis criado.
- [ ] Todos checks do último commit aprovados.
- [ ] Backup real produzido em volume criptografado e restauração conferida.
- [ ] Preview com backend real conectado a banco **isolado** implantado e aprovado em celular.
- [ ] Homologação humana, operacional e revisão independente; liberação comercial.

**PR em rascunho. Sem merges, deploy, cobranças, modificações no Supabase ou exportações reais.**
