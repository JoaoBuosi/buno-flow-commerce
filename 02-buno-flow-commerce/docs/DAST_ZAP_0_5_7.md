# Flow Commerce 0.5.7 — OWASP ZAP DAST

Status: PR em rascunho. O alvo da auditoria é apenas localhost do runner GitHub, nunca Railway, Vercel ou Supabase reais.

## Execução automática

1. Cria PostgreSQL 16 temporário e compila o backend Spring Boot (Java 21).
2. Gera o build do frontend React a partir de package-lock.json, usando npm ci.
3. Sobe o frontend de produção num servidor de CI que aplica os cabeçalhos definidos no vercel.json e encaminha somente GET e HEAD para a API temporária.
4. Valida acesso anônimo: health = 200; me, orders e auth/mfa/status = 401.
5. Executa OWASP ZAP Baseline em dois pontos do localhost: interface React anônima e índice temporário de endpoints GET protegidos. Este índice não existe na aplicação comercial.
6. Publica relatórios HTML e JSON como artifacts do GitHub Actions por sete dias.
7. Reprova quando encontra alerta HIGH, falta relatório, relatório malformado ou alvo fora do localhost. MEDIUM e LOW são apresentados para triagem.

## Proteções

- Nenhum segredo do Railway ou token de usuário é passado ao CI.
- Nenhuma conta, pedido, pagamento, Supabase ou endpoint externo é examinado.
- O servidor CI recusa métodos POST e não permite modificar recursos da API.
- O token GitHub tem permissão somente de leitura; a auditoria não abre issues automaticamente.
- Os dados e a infraestrutura de teste são descartados ao término do runner.

## Limitações

O scan é PASSIVO, não um pentest: não demonstra ausência de SQLi, IDOR, MFA bypass, XSS explorável ou vulnerabilidade em uma sessão autenticada. Cabeçalhos de segurança são reproduzidos a partir do vercel.json: a aplicação real em Vercel precisa de validação independente. WAF, TLS, domínio, envio de e-mails e recuperação de dados estão fora do escopo. Não declare produção liberada com base apenas neste relatório.

Um scan ativo futuro só poderá ser feito em outro workflow com autorização explícita e ambiente exclusivamente descartável. Nunca apontar active scan para o staging compartilhado.

## Ver resultados

GitHub → Actions → Flow Commerce - OWASP ZAP DAST (isolated baseline) → Summary / Artifacts.
Investigar cada alerta HIGH pelo ID e contexto; não reduzir severidades automaticamente para fazer o build passar.

Referências oficiais:
- https://www.zaproxy.org/docs/docker/baseline-scan/
- https://owasp.github.io/DevSecOpsGuideline/2-Process/2-4-Test/2-4-2-Dynamic-Application-Security-Testing/
