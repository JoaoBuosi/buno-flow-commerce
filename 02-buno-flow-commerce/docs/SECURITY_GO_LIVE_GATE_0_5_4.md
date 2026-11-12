# Buno Flow Commerce 0.5.4 — Security & first-merchant gate

Data: 12/11/2026. Escopo: apenas GitHub/CI, sem deploy automático do recurso novo.

## Implementado e testável

- Recuperação limitada a **uma mensagem por conta a cada 300 segundos** por padrão, configurável via `COMMERCE_MAIL_COOLDOWN_SECONDS`.
- O bloqueio usa dados existentes em `password_resets` e trava a linha do usuário dentro da transação PostgreSQL para impedir envios duplicados entre réplicas simultâneas. Não exige migração adicional.
- Durante o cooldown, o endpoint continua retornando 202, sem indicar se há uma conta cadastrada.
- O frontend adicionou política `referrer=no-referrer` para não divulgar tokens de recuperação em URLs de referência.
- Testes novos: múltiplos pedidos em sequência retornam 202, geram apenas uma chamada ao provedor e preservam um único token. O teste antigo de rotação explícita desativa o cooldown apenas no ambiente de testes.

## Não ativar em staging público ainda

- `COMMERCE_MAIL_ENABLED=false` até aquisição/verificação de domínio próprio e testes em contas isoladas.
- `onboarding@resend.dev` é remetente de QA; Resend não permite envio comercial indiscriminado a terceiros com ele.
- O teste real isolado anterior comprovou entrega do e-mail de recuperação, mas não completou expiração, uso único e revogação de sessões por falha na asserção de URL. Ainda não declarar homologação E2E concluída.
- A API em Railway permanece fixada em commit anterior. Os últimos commits de segurança aqui não foram publicados.

## Critérios para liberar primeiros lojistas

1. Registrar e verificar um domínio sob controle da Buno no Resend; publicar e validar SPF/DKIM e política DMARC.
2. Trocar `COMMERCE_MAIL_FROM` para endereço do domínio verificado. Provar entrega para uma conta exclusiva QA.
3. Concluir testes reais de expiração, uso único, troca de senha, revogação das sessões e recebimento do e-mail.
4. Adicionar limitação distribuída por origem/IP no proxy público e monitorar abuso; o cooldown por conta é complementar, não substitui este controle.
5. Homologar backup **durável e criptografado** com retenção e restauração a partir do artefato armazenado (a prova anterior de restauração foi volátil).
6. Revisar LGPD, política de privacidade, termos de uso, logs sem PII e monitoramento/alertas.
7. Revisar CI, PR e escopo do deploy antes de ativar `COMMERCE_MAIL_ENABLED` para lojistas.

## Referência

OWASP Forgot Password Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html
