# Flow Commerce 0.5.5 — OWNER TOTP MFA (PR #11)

**STATUS:** implementação em branch isolada; **NÃO IMPLANTADO NO STAGING**; exigência desativada por padrão.
**Base:** PR #9 / `feat/flow-commerce-0.5.3-operational-readiness`.

## Objetivo e fronteira de confiança

O backend, e não o React, controla a emissão de sessões para `OWNER`. Mesmo com senha correta, nenhuma sessão de loja é emitida sem verificação TOTP ou recuperação. Tokens de desafio são diferentes dos bearer tokens: validade de 5 minutos, SHA-256 no banco, até 5 tentativas e consumo único. Todas as rotas comerciais executam `AuthService.require`, que rejeita sessões `OWNER` sem `mfa_verified=true` quando a feature está ligada, inclusive sessões emitidas antes de sua ativação.

`STAFF` mantém login com senha nesta etapa; operações que exigem `OWNER` continuam verificadas pelo backend.

## Fluxos de API

1. `POST /api/v1/auth/register` ou `POST /api/v1/auth/login`: com `COMMERCE_OWNER_MFA_REQUIRED=true` e papel `OWNER`, devolve `{mfaRequired:true,enrollmentRequired:boolean,challengeToken,storeId,role:"OWNER"}` — **sem accessToken**.
2. Se não cadastrado: `POST /api/v1/auth/mfa/enroll/start` com `challengeToken`; retorna `secret` Base32 e `otpauthUri` (sensíveis, exibidos apenas ao usuário em transporte HTTPS; cabeçalho no-store).
3. `POST /api/v1/auth/mfa/enroll/complete` com `challengeToken` e TOTP de 6 dígitos; cadastra segredo criptografado em repouso (AES-256-GCM com AAD de usuário), retorna sessão `OWNER` verificada e **oito códigos de recuperação de uso único**. Não persistir códigos em frontend/storage.
4. Para contas cadastradas: `POST /api/v1/auth/mfa/login/verify` com `challengeToken` mais `code` ou `recoveryCode`; retorna sessão verificada somente após validação. Passo de tempo TOTP de 30 segundos, tolerância ±1 e trava de reutilização pelo contador.
5. Tokens `mfa` não são bearer; senha e token inicial sozinhos não autorizam rotas de pedidos/exportação. Reset de senha por e-mail **não desabilita 2FA**.

## Rollout seguro

- **Não habilitar nesta branch ainda.** Nunca passar valor de chave de criptografia em GitHub, issue, logs ou conversa.
- Migração Flyway aditiva `V6__owner_mfa.sql` deve rodar em QA isolado antes de qualquer banco de staging. Em Supabase, serviço usa papel `flow_commerce_staging_app`; proprietário das tabelas pode precisar conceder exclusivamente permissões `SELECT,INSERT,UPDATE,DELETE` nas novas tabelas MFA a esse papel, jamais a `anon`/`PUBLIC`.
- Ao preparar staging: gerar chave AES de 32 bytes aleatórios fora do GitHub, armazenar em segredo `COMMERCE_MFA_ENCRYPTION_KEY` no Railway, protegida e com backup cifrado **fora do banco**. Perder essa chave impede validar MFA existente.
- Confirmar que a API não inicia com MFA ligado sem chave; testar com conta descartável e banco de QA. Testar também rotação de chave com procedimento controlado, falha de banco, rate limit, OTP inválido e restauração.
- A migração altera `user_sessions` adicionando `mfa_verified=false` inicialmente. Ao ativar, sessões `OWNER` não verificadas tornam-se inválidas. Planejar janela de reautenticação, avisar proprietários e garantir que cada um tem autenticador e códigos de recuperação.
- Em caso de perda do autenticador **e** dos códigos de recuperação, não liberar sessão por e-mail sozinho. Exigir processo assistido de validação de identidade e ação auditável (fora do escopo de MVP).
- Após rollout, monitorar `401`, `429`, `503` e fluxos de registro. Não registrar senha, código OTP, segredo TOTP, recovery code ou desafio em logs.

## Testes automatizados

`OwnerMfaIntegrationTest`: TOTP RFC6238, cadastro sem bearer, tentativa de acesso direto e sessão legada recusadas, segredo não armazenado em claro, confirmação de TOTP, códigos de recuperação e uso único, limite de 5 tentativas, papel `STAFF`.
`web/tests/owner-mfa.e2e.spec.ts`: UI login OWNER bloqueado até segundo fator, cadastro TOTP e apresentação dos códigos de uso único.
Os testes usam chaves e contas sintéticas; não fazem envios reais, não modificam o Supabase atual.

## Ainda pendente para produção

Testes de concorrência real de desafios simultâneos, recuperação assistida, revogação de dispositivo TOTP, mecanismos de rotação da chave cifrante, verificação de alertas CodeQL e pentest autorizado. O PR precisa de revisão humana antes de merge.
