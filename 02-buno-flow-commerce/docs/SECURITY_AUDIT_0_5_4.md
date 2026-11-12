# Buno Flow Commerce — Auditoria de Segurança 0.5.4 (12/11/2026)

## Escopo e metodologia

Revisão dirigida do código Spring Boot/Java 21, PostgreSQL/Flyway, Vercel gateway Node, React/Vite, jobs GitHub Actions e credenciais configuradas via nomes de variáveis (valores ocultos). Executados SAST CodeQL (Java e JavaScript/TypeScript), varredura histórica de segredos Gitleaks, CVEs Maven e Python via Trivy, CVEs npm via npm audit e os workflows CI de integração/Playwright.

**Não é pentest independente, SOC2/ISO27001, nem garantia de ausência de falhas.** Código em PR #9, implantações de staging documentadas à parte.

## Principais achados e correções

| Área | Achado | Tratamento |
|---|---|---|
| Bibliotecas Java | Spring Boot 3.4.5 e pgJDBC 42.7.5 com cinco CVEs HIGH no Trivy (CVE-2025-49146, CVE-2026-42198, CVE-2026-54291, CVE-2026-22731, CVE-2026-22733) | Alterado para Boot 3.5.16 e pgJDBC 42.7.14; Trivy reexecutado com sucesso |
| Login | Limitador anterior usava somente o endereço do socket do proxy e mapa local; atrás de Vercel podia agrupar clientes e não escalar entre réplicas | Novo limitador transacional PostgreSQL, chave HMAC do IP e token validado do gateway; limitador legado desativado só quando novo flag for ativado |
| Gateway | Necessário vincular identidade por IP ao edge sem confiar em cabeçalhos enviados pelo cliente | Gateway passa X-Buno-Client-IP somente do x-vercel-forwarded-for; edgeKey continua injetado apenas no servidor; testes de spoofing |
| Recuperação | Falta de antiabuso IP global e repetição por conta | Cooldown por conta de 5 min e novo limite IP em 5 min para login (30), cadastro (12), solicitar reset (10), concluir reset (15) |
| CSV | Fórmulas precedidas por espaços/BOM/zero-width podiam escapar sanitização antiga | Exportação protege =,+,-,@ após prefixos ignoráveis; testes cobrindo várias variantes |
| Headers web | Proteções de conteúdo/navegador parciais | CSP, frame-ancestors none, no-referrer, nosniff, permissions-policy no vercel.json; CI de browser validado, revisão de produção necessária |
| Supply chain | Ausência de rotina de segurança automatizada | Gitleaks, CodeQL Java/JS, Trivy, npm audit via GitHub Actions; Dependabot semanal configurado |
| SQL e RBAC | Review estático de consultas parametrizadas e escopo por loja | Observados parâmetros JdbcTemplate e filtros por store_id nas operações principais; recomenda-se pentest de isolamento em staging |
| IA / prompt injection | Flow Commerce nesta versão não possui LLM conversacional em endpoints | Risco atual não aplicável; se Agent Platform / Buno AI integrar, adotar autorização por ferramenta, minimização de contexto, controles de saída e aprovação humana |

## Segurança do rate limit

- Flag default `COMMERCE_IP_RATE_LIMIT_ENABLED=false` até implantação/validação.
- Backend exige chave HMAC de no mínimo 32 caracteres quando ligado e segredo do gateway no modo edgeRequired.
- Novo armazenamento `flow_commerce.auth_ip_rate_limits` (migração aditiva V5), com limpeza de buckets após 48 horas.
- Não confie em `X-Forwarded-For` ou `X-Buno-Client-IP` de clientes diretos; confirme que Vercel fornece `x-vercel-forwarded-for` assinado implicitamente pela autenticação gateway.
- Em falta de banco o filtro retorna 503 para não permitir tentativas ilimitadas; monitorar 503/429 para falso positivo.
- Ao ligar: verificar chamadas de IPs distintos e sem IP confiável. O proxy pode causar bloqueio coletivo se x-vercel-forwarded-for não for repassado corretamente.
- **WAF Vercel:** tentativas de obter/alterar regras via API conectada retornaram HTTP 404 Seawall Config not found; regra de WAF **não foi ativada**. Ativar separadamente no projeto, preferindo observação antes de mitigação.

## Verificações e evidência

- Workflow **Flow Commerce backend integration**: Spring Boot + PostgreSQL + migrações e testes automatizados. (Executar antes de liberar).
- Workflow **Flow Commerce - browser E2E**: React e fluxos de lojistas, isolamento entre lojas.
- Workflow **Flow Commerce — Security SAST and dependency audit**: CodeQL Java/JS, Gitleaks, npm audit, Trivy.
- GH Security tab deve ser revisada para todos os alertas CodeQL e Dependabot, inclusive alertas informacionais e falsos positivos.
- Segredos são mantidos no Railway/Vercel e não copiados para o GitHub. A varredura automatizada não demonstra inexistência de segredos em logs externos ou histórico de chats.

## Pendências para primeiro lojista

1. Habilitar limitador em staging somente após migração V5 confirmada e frontend gateway validado, com smoke de clientes e rollback pronto.
2. Liberar WAF/observabilidade (API Vercel retorna 404 nesta conexão). Monitorar aumentos de 401/403/429/503, alertas por origem e tempo de resposta.
3. **2FA/MFA**: ainda não existe; planejar TOTP ou WebAuthn (passkeys), recuperação segura e sessões verificadas antes da abertura comercial.
4. **Domínio verificado**: Resend só autoriza envios de teste para e-mail da conta com `onboarding@resend.dev`. Manter envio global desativado, verificar DNS SPF/DKIM/DMARC quando houver domínio.
5. **Backup persistente cifrado** com política de retenção, restauração testada a partir de artefato durável e gestão de chaves; restauração transiente anterior não substitui backups de recuperação de desastre.
6. Migrar Spring Boot 3.5.x para linha de suporte ativo/contratado. A versão 3.5.16 elimina os CVEs detectados, mas isso não garante suporte indefinido.
7. Adicionar `package-lock.json` para reprodutibilidade npm; atual CI instala e audita sem lockfile.
8. Threat modeling, revisão LGPD, respostas à incidentes, autenticação resistente à enumeração/timing, rate limit de payload e teste externo DAST/pentest.
9. Checar logs e erros de terceiros contra exposição de tokens de recuperação, PII e segredo de API; rotacionar credenciais suspeitas.
10. Bloquear merge se novo CI de segurança falhar e exigir revisão humana de alterações sensíveis.

## Critério de saída

Não declarar produção pronta apenas pela aprovação do CI. Requer sucesso da implantação real, prova do IP por origem, domínio autenticado, backup durável, MFA/admin, análise de alertas e avaliação de segurança independente.
