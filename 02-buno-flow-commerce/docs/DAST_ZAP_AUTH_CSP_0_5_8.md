# Flow Commerce 0.5.8 — CSP strict + authenticated DAST (ZAP)

Status: branch em revisão, **NÃO implantada no Railway/Vercel**; nenhuma conta comercial ou pedido real será usado no scan.

## Correção de CSP
- Removido `style-src 'unsafe-inline'` do `web/vercel.json`.
- Permitidos estilos estáticos apenas da própria origem: `style-src 'self'; style-src-elem 'self'; style-src-attr 'none'`.
- `script-src 'self'` mantido, sem JavaScript inline.
- `web/tests/csp-isolated.e2e.spec.ts` roda Chromium contra a aplicação React compilada, recebendo a CSP como HTTP header. Bloqueios CSP/erros de página reprovam a execução.
- O ZAP Baseline da versão anterior continua analisando interface e endpoints GET; espera-se eliminar o aviso CSP por `unsafe-inline`, sem silenciar outras regras.

## Authenticated DAST real via proxy OWASP ZAP

Novo workflow `.github/workflows/flow-commerce-zap-auth.yml`:
1. Inicializa PostgreSQL temporário `commerce_auth_dast` no runner do GitHub.
2. Compila o backend real Spring Boot Java 21 e aplica migrações; configura uma chave AES de 32 bytes **aleatória, somente no processo local**.
3. Inicia ZAP em `127.0.0.1:8090` como **proxy HTTP passivo**. Não expõe interface externa e não executa active scan.
4. Executa `scripts/zap_authenticated_qa.py`, que envia requisições pela porta do proxy em formato HTTP absolute-form contra `127.0.0.1:8080`. Cria duas lojas, usuários e pedidos fictícios; nenhuma URL externa pode ser configurada.
5. Confirma que `GET /orders` sem sessão é negado; sessão de loja A não acessa B e vice-versa; GUID de pedido estrangeiro não pode ser lido, ter histórico consultado ou status modificado (IDOR).
6. Habilita MFA **somente nesse runner descartável**. Revalida a senha sintética, configura TOTP, rejeita challenge como bearer, revoga a sessão pré-MFA, exige segundo fator após senha, impede replay de TOTP e de código de recuperação, preserva login do outro `OWNER` não inscrito.
7. Aguarda a fila de scanner passivo do ZAP. Consulta os alertas localmente, **descarta todo o tráfego HTTP bruto** e preserva apenas ID, nome, severidade e número de ocorrências de regras.
8. O validador `zap_report_gate.py --port 8080` reprova relatórios ausentes, alvos fora de loopback ou alertas HIGH. Um segundo artifact contém nomes de asserções e HTTP status, nunca credenciais, tokens, e-mails, OTP ou cabeçalhos.
9. Retenção de sete dias. Não há automação de merge, deployments ou edições no Supabase.

## Critérios de aprovação
- Teste do navegador com CSP real e sem violações.
- Testes de autorização, isolamento e MFA via proxy ZAP (nenhum bypass).
- ZAP Baseline anônimo continua aprovado, e alertas MEDIUM/LOW são registrados e triados.
- Zero HIGH no scan autenticado. Todo resultado ausente ou malformado falha.
- Java/PostgreSQL E2E, CodeQL, Gitleaks e CVEs não podem regredir.

## Limites de escopo

A integração usa o ZAP **em modo proxy passivo** e asserções autenticadas específicas do negócio. Não equivale a pentest ativo profundo nem garante ausência de SQL injection, XSS, concorrência maliciosa, fraude ou vulnerabilidades da infraestrutura do provedor. O MFA de produção segue desligado para as contas reais; não alterar essa configuração como parte do workflow.

Nenhum arquivo bruto da sessão ZAP é enviado aos artifacts: mensagens autenticadas incluem material sensível mesmo se oriundas de usuários sintéticos. A saída preservada é propositalmente mínima.

Referências:
- https://www.zaproxy.org/docs/automate/automation-framework/
- https://www.zaproxy.org/docs/desktop/start/features/intercept/
- https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/style-src
