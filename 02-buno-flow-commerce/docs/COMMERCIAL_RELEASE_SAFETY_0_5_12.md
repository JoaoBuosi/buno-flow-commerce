# Buno Flow Commerce 0.5.12 — Commercial Release Safety

**Estado:** em desenvolvimento, PR rascunho, empilhado na 0.5.11. **NÃO liberado para clientes**. Este marco adiciona dois bloqueios verificáveis; não executa procedimentos com dados reais.

## Diagnóstico verificado em 13/11/2026

- A 0.5.11 / PR #18 passou nas suítes Java/PostgreSQL, React/Playwright, SAST/dependências, ZAP baseline, DAST autenticado e monitoramento sintético no CI.
- **O Railway existente** (projeto `buno-flow-commerce-staging`, ambiente `staging`) ainda tem a API ligada à branch `feat/flow-commerce-owner-mfa-self-service-0.5.6`; não é a candidata comercial 0.5.11/0.5.12.
- O preview privado do celular foi uma simulação em memória, **sem backend de produção**. Não é prova de restauração, autenticação ou persistência reais.
- O procedimento `verify_isolated_restore.py` exige estação aprovada, PostgreSQL 17 isolado, origem de leitura autorizada e armazenamento cifrado. Ainda **não foi executado** contra backup real nesta etapa.
- O envio de recuperação por e-mail é verificado apenas com mock em CI; sem remetente/domínio autorizado e comprovação de entrega real.
- A chave MFA ainda precisa de migração/rotação coordenada e cofre externo; não alterá-la cegamente.
- O monitoramento atual valida alertas sintéticos. **O recebimento humano do alerta externo ainda não foi comprovado**.

## Proteção 1 — segurança de inicialização no modo produção

`CommercialStartupGuard.java` é chamado a partir de `Application.main` **antes de iniciar Spring Boot, datasource e migrações Flyway**.

Ativação automática do bloqueio se qualquer uma destas situações ocorrer:

- `RAILWAY_ENVIRONMENT_NAME=production`
- `COMMERCE_RUNTIME_ENV=production`
- `COMMERCE_LIVE_CUSTOMERS_ENABLED=true`

Nesses contextos, a aplicação se recusa a iniciar se não houver:

- liberação explícita `COMMERCE_LIVE_CUSTOMERS_ENABLED=true`;
- `COMMERCE_PILOT_ONLY=true` com código de convite não trivial (24 a 128 caracteres);
- rate limit ligado e chave de hash de IP provisionada;
- MFA obrigatório ao proprietário, inscrição disponível e chave de criptografia configurada;
- recuperação de e-mail ativada, remetente plausível, provedor configurado e frontend HTTPS;
- CORS HTTPS restrito à origem do frontend;
- PostgreSQL remoto usando conexão JDBC com `sslmode=require`, `verify-ca` ou `verify-full`, com credencial não padrão.

**IMPORTANTE:** isto é uma verificação de *presença e formato de configuração*, não prova que e-mails são recebidos, que o vault existe, que o banco usa papel mínimo, que o histórico foi restaurado ou que o pentest foi aprovado. `COMMERCE_LIVE_CUSTOMERS_ENABLED` é um marcador de política de startup, **não é um filtro de tráfego** e não substitui a proteção de acesso da API. O modo `staging` continua com comportamento atual e exige controle de acesso por conta/provedor.

**Restrições:** não definir `COMMERCE_LIVE_CUSTOMERS_ENABLED=true` no staging como alternativa para habilitar clientes. O rollout deve ser verificado por responsáveis. A inicialização padrão fora do modo produção continua inalterada, de modo a preservar os testes existentes.

## Proteção 2 — gate documental para beta pago

`scripts/commercial_go_live_gate_0512.py` exige um manifesto **sem senhas, tokens, dumps ou dados pessoais**, com:

- SHA completo da versão candidata, exatamente igual ao declarado pelo operador;
- SHA declarado do deploy alvo igual ao candidato;
- carimbos de data recentes e com fuso horário;
- URL HTTPS restrita às provas documentadas na área GitHub da Buno;
- 15 evidências aprovadas, incluindo restauração **real**, integridade, entrega real de recuperação, MFA em vault, alerta recebido, isolamento entre lojas, revisão de dispositivo físico, suporte/rollback, política legal e revisão independente;
- aprovação humana explicitamente registrada.

Não há manifesto com aprovações reais neste PR. A execução sem evidência válida deve **falhar**. Um manifesto estruturalmente correto também precisa de revisão independente das suas provas; o script **não consulta o GitHub** para verificar links, nem permite deploy. Não armazenar evidências contendo PII no repositório ou nos artifacts.

## Proteção 3 — exportação read-only do backup real

O script existente `verify_isolated_restore.py` foi reforçado para:
- exigir origem PostgreSQL remota com TLS e uma role explicitamente dedicada à exportação;
- rejeitar usuários administrativos, incluindo padrões `postgres`, `postgres.*`, `supabase_admin` e `service_role`;
- impor `default_transaction_read_only=on` à sessão de origem;
- verificar em consulta exclusivamente de leitura se o usuário atual possui poderes administrativos, `CREATE` no schema ou privilégios de escrita nas tabelas;
- **abortar antes de criar qualquer dump** quando houver privilégios de escrita, mantendo o destino local vazio e descartável.

Novos testes em `test_verify_isolated_restore.py` comprovam rejeição de origem insegura e ausência de `pg_dump` na tentativa bloqueada. A revisão manual da role, ausência de túnel e criptografia do armazenamento ainda é indispensável. A configuração de produção não foi alterada.

## Testes automáticos

- `CommercialStartupGuardTest`: 11 cenários, incluindo configuração incompleta, modo produção reconhecido automaticamente, password local, TLS incorreto, autenticação multifator, e-mail e CORS.
- `test_commercial_go_live_gate_0512.py`: 18 cenários de rejeição e formato; nenhum dado real.
- Workflow `.github/workflows/flow-commerce-release-safety-0512.yml`: confirma que o executável recusa produção insegura **antes de inicializar JDBC**, além dos testes de política.

## Ordem para concluir os bloqueios operacionais

1. **Isolamento e plano do piloto:** fixar SHA candidata e ambiente dedicado com PostgreSQL separado ou banco/instância dedicada, sem compartilhar dados de QA com clientes. Conferir roles e backups com autorização humana.
2. **Backup real:** validar `real_restore_preflight.py` numa estação Windows autorizada e **restaurar cópia real em PostgreSQL 17 isolado**, comparar integridade e registrar evidência sanitizada. É pré-requisito, não item opcional.
3. **MFA/segredos:** guardar chave válida em cofre independente, planejar migração da chave atual sem perder TOTP e confirmar recuperação do cofre; não compartilhar segredos no chat.
4. **E-mail:** verificar remetente, testar envio e recebimento, token expirado/usado e revogação de sessões em conta controlada de QA.
5. **Alertas e suporte:** definir canal humano, demonstrar indisponibilidade em ambiente descartável e comprovar recebimento/ack, escalonamento e rollback.
6. **Teste integrado e privacidade:** autenticação de duas lojas no destino real, autorização, controle de acesso, LGPD/termos/cobrança, revisão independente e aprovação humana final.

**Não alterar ou migrar o Supabase existente durante estes passos sem escopo/autorização explícita.**

## Status do lançamento

- [x] Guard de startup proposto no código, com falha fechada.
- [x] Política de liberação documental codificada.
- [ ] Todos os workflows da revisão final aprovados.
- [ ] Backend candidato implantado e verificado em ambiente isolado.
- [ ] Backup real restaurado e integridade aprovada.
- [ ] E-mail real, chave MFA, alerta recebido e rollback aprovados.
- [ ] Aceitação humana em celular com backend isolado (o simulador não conta).
- [ ] Revisão independente de segurança e documentação legal.
- [ ] Aprovação formal de abertura de beta pago.

Este PR continua em rascunho, sem merge, deploy, alterações de credenciais, uso de dados de clientes ou ativação de cobranças.
