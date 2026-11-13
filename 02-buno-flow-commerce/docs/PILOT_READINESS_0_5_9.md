# Buno Flow Commerce 0.5.9 — Pilot Readiness / Segurança e Recuperação

**Status:** draft. Nenhuma aprovação para produção. Base: 0.5.8, com CSP corrigida e DAST autenticado em ambiente descartável.

## Entregas automatizadas neste PR

- Gate documental offline de liberação `scripts/pilot_readiness_gate.py`. Não consulta Railway, Supabase, Vercel ou segredos; aceita apenas referências HTTPS e metadados restritos. **Não prova por si só a autenticidade do conteúdo ou a validade da evidência**, que devem ser conferidas por um revisor.
- Testes unitários negativos contra ausência/falha de restauração, recuperação de senha, rotação e cópia externa da chave MFA, recuperação MFA, revisão humana, pentest, ambiente incorreto, links inseguros e campos sensíveis.
- Workflow `flow-commerce-pilot-readiness.yml` que testa a política sem acesso a segredos e sem qualquer deploy.
- As auditorias de backend/Postgres, Playwright, CodeQL e ZAP da branch-base continuam necessárias; a aprovação do gate documental **não substitui** seus resultados.

## Checklist de liberação do piloto (requer operador autorizado)

| Controle | Evidência exigida | Estado inicial |
|---|---|---|
| Java/PostgreSQL CI | Workflow verde no commit candidato | Pendente de conferência no PR |
| Chromium/Playwright | Workflow verde no mesmo commit | Pendente |
| CodeQL, Gitleaks, Trivy, npm audit | Workflows verdes + achados triados | Pendente |
| ZAP Baseline e ZAP autenticado | Relatórios sanitizados, zero HIGH, fluxo executado em localhost descartável | Pendente |
| Isolamento entre lojas | Negação de GET/PATCH/CSV e acesso indireto entre dois tenants sintéticos | Pendente |
| MFA | Teste TOTP, replay negado e recuperação de uso único | Pendente |
| Recuperação de senha | Domínio remetente verificado, e-mail real recebido em conta QA, token único/expirável e sessões revogadas conforme política | **Não executado** |
| Backup real | Dump autorizado (acesso mínimo), armazenado cifrado, restore em PostgreSQL 17 isolado, sem acesso ao banco original | **Não executado** |
| Integridade do restore | Comparação agregada de tabelas/migrações, tempos de restauração e evidência sanitizada, sem PII | **Não executado** |
| Chave AES-256 MFA | Rotação antes de enrolamento real, cópia independente em cofre e teste controlado de recuperação | **Não executado** |
| Alertas ZAP remanescentes | Triagem justificada de COEP, cache e detecção de SPA, sem silenciar alertas | Pendente |
| Revisão independente | Análise humana de autorização, rate limiting, segurança de sessões, CSRF, headers e segredos | Pendente |
| Pentest independente | Revisão de resultados e plano de correção, sem escanear ambientes reais sem autorização | Pendente |
| Rollback e suporte | Critérios de parada, restauração, monitoramento e contato humano estabelecidos | Pendente |

**Importante:** passar CI é necessário, mas não suficiente para abrir o piloto. Nenhuma evidência é considerada aprovada automaticamente.

## Como usar o gate, quando as evidências existirem

Preparar arquivo JSON local, fora do repositório, contendo:
- `version: "0.5.9"` e `environment: "staging"`.
- `checks`: para cada chave de `REQUIRED` em `pilot_readiness_gate.py`, objeto `{"passed": true, "evidence_url": "https://..."}`.
- `approval`: `reviewed_by`, `reviewed_at`, `ticket` com dados não sensíveis.

Executar `python3 scripts/pilot_readiness_gate.py /caminho/seguro/evidence.json`. Código de saída 0 indica **somente que o documento está estruturalmente completo**, não que os testes ocorreram ou que o piloto pode ser automaticamente liberado.

Não enviar ao repositório: dumps, nomes/e-mails de clientes, segredos MFA, tokens, senhas, chaves, links de acesso privado ou tráfego HTTP capturado. Guardar evidências completas em repositório de acesso restrito; no gate, somente referências sem tokens.

## Sequência operacional sem impacto em produção

1. Revisar e aprovar code review e CI do PR 0.5.9. Não fazer merge até homologação.
2. Em estação/ambiente autorizado, executar `scripts/homologar_backup_real_windows.ps1` da frente 0.5.3, após revisão de permissões e armazenamento cifrado; restaurar exclusivamente em PostgreSQL local isolado.
3. Verificar domínio e provedor de e-mail; testar reset com conta descartável em staging, sem expor OTP/token nos logs.
4. Rotacionar `COMMERCE_MFA_ENCRYPTION_KEY` com plano de migração/recuperação e cofre independente; testar recuperação antes de habilitar cadastro 2FA para proprietários reais. Nunca trocar chave enquanto houver dados MFA cifrados sem estratégia segura.
5. Revisar avisos residuais ZAP e solicitar pentest autorizado.
6. Submeter evidências sanitizadas à revisão humana e decidir explicitamente se o piloto limitado pode começar.
7. Somente em mudança operacional separada, com aprovação, planejar rollout, canário, monitoramento e rollback.

## Não incluído / proibições

Nenhuma rotação automática de segredos, nenhuma integração de e-mail de produção, nenhum banco real, nenhum merge e nenhum deploy. CI desta branch **não executa restauração real nem disparo de e-mail**, e não deve alegar tais validações.
