# Flow Commerce 0.5.10 — Account Recovery & Operational Reliability

**Status:** implementação em PR rascunho, empilhada sobre 0.5.9 / PR #16. Não implantada. Todos os dados e usuários dos testes são sintéticos.

## Entregas deste marco

1. **Recuperação de contas:** novo caso de integração `inventedResetTokenNeverRevokesValidSession` em `EmailRecoveryIntegrationTest`, garantindo que um token inexistente não altera senha nem invalida sessão legítima. Mantidos testes existentes de expiração, replay, revogação, rollback em falha de SMTP, cooldown e conta inexistente. `flow-commerce-isolated-email-recovery.yml` executa esses testes em banco temporário com entrega mockada.
2. **Backup e restauração:** reforçado `disposable_pg_restore_drill.sh`: dados separados por duas lojas, contagem e digest de pedidos, contagem de lojas, restauração das FKs e quantidade de índices. `--network none`, PostgreSQL 17 e nenhuma conexão à base comercial. Executado em `flow-commerce-disposable-restore.yml`.
3. **MFA / isolamento:** permanecem como bloqueios de regressão os testes de `flow-commerce-zap-auth.yml`, incluindo TOTP, desafio não reutilizável, códigos de recuperação de uso único, IDOR e isolamento entre dois tenants descartáveis. Esta versão **não altera chaves nem ativa MFA para contas reais**.
4. **Monitoramento:** novos `monitor_alert_gate.py` e `test_monitor_alert_gate.py` avaliam resultados sintéticos e falham de modo fechado diante de `503`, `502`, `200` indevido em endpoint restrito, latência acima de 5s, dados ausentes, ambiente errado ou relatório com conteúdo sensível. `flow-commerce-reliability-0510.yml` testa a decisão de alerta offline. Os limites são valores conservadores do teste, **não SLOs comerciais aprovados**.
5. **Piloto:** mantém `pilot_readiness_gate.py` (0.5.9) para exigir provas revisadas de recuperação, backup real, MFA em cofre e autorização humana. Nenhuma evidência real é marcada como pronta por estes testes.

## Critérios para aceitar o PR

- [ ] GitHub Actions do último commit: Java/Postgres, Playwright, CodeQL/dependências, ZAP baseline, ZAP autenticado, QA de e-mail mockado, backup sintético, política de piloto e monitoramento.
- [ ] Sem vazamento de e-mails de clientes, credenciais, tokens, relatórios HTTP brutos ou dumps para os artifacts.
- [ ] Nenhum deploy, merge ou ativação de envio real de e-mails.
- [ ] Revisão manual da política de uso do sistema, permissões do cadastro/lojista, acesso direto à API, isolamento entre lojas e resposta em dispositivos móveis.

## Pendências que o CI não resolve

- Registro e verificação de domínio **adiados por decisão do projeto**. O envio real e a recuperação real por e-mail continuam bloqueados até a configuração de remetente autorizado ou alternativa previamente aprovada.
- Backup real do Supabase: exportação autorizada com credencial restrita, armazenamento criptografado fora do GitHub, restauração num PostgreSQL isolado na estação aprovada e inspeção de integridade.
- Chave de criptografia MFA: inventário de dados cifrados, rotação/migração supervisionada, cofre independente e ensaio de recuperação. Nunca rotacionar às cegas.
- Teste de recebimento de alertas pelo responsável: o novo gate avalia **quando alertar**, não entrega notificações; escolher e validar o canal depois com permissão.
- Revisão independente/pentest autorizado e decisão explícita para piloto com clientes.

## Limites de segurança

Os workflows não acessam Railway, Supabase de produção, Resend, Vercel privado ou contas reais; não enviam mensagens e não requerem `Secrets` do GitHub. O estudo da recuperação de senha e o DAST fazem operações somente contra PostgreSQL temporário do CI. Mesmo que todos os workflows passem, o projeto não recebe autorização automática de lançamento.
