# Flow Commerce 0.5.8 — CSP estrita + DAST autenticado em CI

**Estado:** alterações propostas em branch isolada; não publicar no Railway ou Vercel antes de validar o CI e revisar os achados.

## 1. Correção de CSP

- A regra `style-src 'self' 'unsafe-inline'` foi substituída por `style-src 'self'` em `web/vercel.json`.
- O estilo inline React `style={{marginTop:12}}` foi movido para a classe `forecastHeading` em `web/src/style.css`.
- Testes em `scripts/test_csp_policy.py` impedem o retorno de `unsafe-inline`, `unsafe-eval` e estilos React inline.
- O scan reproduz a CSP definida em `vercel.json` no servidor de QA. É necessário verificar o cabeçalho real no domínio Vercel após uma publicação aprovada.

## 2. Validação DAST autenticada — ambiente descartável

- PostgreSQL 16 e Spring Boot Java 21 no GitHub Actions, nunca Supabase.
- Chave AES do TOTP gerada aleatoriamente no runner e descartada com ele; não usa nem lê a chave do Railway.
- `COMMERCE_OWNER_MFA_REQUIRED=true` **somente no CI**.
- Criação de dois proprietários fictícios e duas lojas, ambos exigindo TOTP RFC 6238 antes de obter bearer token.
- Um pedido sintético criado em cada loja para testar isolamento real da API.

O script `scripts/ci_authenticated_dast.py` verifica:
- senha sem TOTP não gera sessão; desafios não funcionam como bearer;
- login após TOTP válido; código inválido, TOTP reutilizado e código de recuperação reutilizado são recusados;
- troca de `X-Store-Id` entre dois proprietários retorna 401;
- IDOR em detalhes e histórico de pedidos alheios retorna 404;
- listagem, dashboard, contagem e exportação CSV não misturam dados de lojas;
- requisições anônimas e token malformado são rejeitados;
- os pedidos sintéticos próprios continuam acessíveis.

Depois dos testes, apenas um arquivo temporário com os tokens das sessões QA (sem senhas, TOTP ou recuperação) é gravado com permissão 0600 no `RUNNER_TEMP`. O servidor de DAST injeta os tokens **somente no lado do runner**, para que o ZAP observe respostas autenticadas GET. Tokens jamais entram nas URLs, HTML do índice de descoberta ou artifacts. O servidor de QA não encaminha POST, PATCH ou DELETE.

## 3. OWASP ZAP Baseline

Três varreduras passivas contra `127.0.0.1:5173`:
1. Interface pública React.
2. Rotas GET protegidas, sem autenticação — prova de HTTP 401.
3. Rotas GET sintéticas e autenticadas dos dois proprietários — conteúdo real da aplicação no banco fictício.

HTML e JSON anexados por sete dias; o gate falha em alerta HIGH, relatório ausente ou domínio fora do localhost. Os demais níveis são apresentados para triagem, não ignorados.

**Não é pentest completo ou teste ativo de exploração.** Não inclui scan autenticado via navegador com login automático do ZAP; o contexto de autenticação é aplicado por proxy temporário que só aceita GET/HEAD. A bateria HTTP direta faz os testes negativos de autorização e MFA. Não cobre abuso de todos os métodos ou concorrência em produção.

## 4. Segurança operacional

- Nenhum deploy nos projetos existentes, mudança de variável Railway ou acesso a Supabase.
- Sem credenciais reais, logs de tokens, upload de dump, dados de usuários reais ou scanner externo.
- O runner e seu banco efêmero são destruídos após a execução.
- CodeQL, Trivy, Gitleaks, npm audit, backend Java e Playwright continuam necessários antes de qualquer merge.

## 5. Próximas evoluções

- Triagem dos achados ZAP autenticados e correção de riscos reais.
- Validação da CSP em Vercel após revisão e publicação controlada.
- Scan ativo autorizável **exclusivamente em ambiente descartável**, com quota de requisições e credenciais sintéticas.
- Pentest independente antes de liberar os primeiros lojistas.

Referências:
- https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/style-src
- https://www.zaproxy.org/docs/docker/baseline-scan/
- https://owasp.github.io/DevSecOpsGuideline/2-Process/2-4-Test/2-4-2-Dynamic-Application-Security-Testing/
