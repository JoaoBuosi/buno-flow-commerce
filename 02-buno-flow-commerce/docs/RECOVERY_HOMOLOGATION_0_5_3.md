# Flow Commerce 0.5.3 — Homologação da recuperação de senha

## Estado e fronteiras de segurança (12/11/2026)

**O fluxo com uma conta nova e uma mensagem real de reset ainda NÃO está homologado em staging.**

- Resend HTTPS: teste isolado aceito pelo provedor, HTTP 200, com recebimento verificado em Gmail controlado. Foi um e-mail de smoke test sem token de recuperação.
- Transporte Java: `ResendEmailSender` ganhou cabeçalhos `Accept` e `User-Agent` confirmados no smoke test. CI backend/browser verde no commit de implantação b3f0b5d9.
- Banco: a conta destinatária autorizada pelo remetente de testes `onboarding@resend.dev` **já existe** no banco de staging. Não alterar a senha, os tokens ou as sessões desse usuário para fins de QA.
- Resend sem domínio próprio: tentativa de enviar para alias da mesma caixa recebeu **403 validation_error**, com a mensagem de que só se pode enviar testes ao e-mail exato da conta Resend. Não repetir indefinidamente.
- Segurança: manter `COMMERCE_MAIL_ENABLED=false`, `COMMERCE_MAIL_PROVIDER=resend`, remetente de testes e segredo `RESEND_API_KEY` apenas no Railway. Não reproduzir chaves ou tokens em logs, repositório e issues.
- Backup: a restauração lógica real de `flow_commerce` em PostgreSQL 17 isolado e volátil foi aprovada anteriormente; não equivale à retenção criptografada de backups.

## Cobertura de testes com banco efêmero e sem entrega real

- Spring Integration: solicitação não enumera usuários; token randômico 256 bits, hash somente no banco, expiração de 20 min, uso único, revogação das sessões anteriores, login com senha nova, rollback do token em falha de transporte.
- Spring Integration adicional: a segunda solicitação invalida o link da primeira mesmo antes de vencer.
- Resend mock: erros da API HTTPS não expõem endereços/credenciais e não deixam tokens válidos sem envio.
- Playwright: tela "Esqueci minha senha", formulário a partir de `?reset_token=`, limpeza do token da URL após sucesso, erro seguro para token expirado. Rotas mockadas: nenhum email enviado e nenhuma senha real alterada.
- Verificar o status final dos workflows de CI após qualquer alteração.

## Passo necessário para homologação real

Usar uma conta e caixa **dedicadas a QA**, distintas das contas reais de staging. Para entregar e-mail ao endereço de QA, verificar domínio próprio no Resend (ou empregar outra integração de envio com domínio verificado). Como alternativa, criar um ambiente de banco totalmente isolado e uma instância privada do backend que permita testar com a caixa pessoal autorizada pelo Resend, sem tocar nas contas existentes.

Roteiro, com autorização:
1. Provisionar conta de teste descartável no banco de QA e registrar contagens iniciais.
2. Habilitar o envio **somente** para a conta de QA / no backend isolado, nunca globalmente em staging comercial.
3. Acionar `POST /api/v1/auth/reset/request` e confirmar e-mail real e link HTTPS do frontend.
4. Validar token expirado, token substituído, token usado duas vezes, login antigo recusado e novo aceito.
5. Revogar tokens e sessões de QA, eliminar dados de teste e desligar o envio, mantendo evidências sem PII.

## Critério de saída

Somente considerar a recuperação comercial homologada com **entrega na caixa de QA + fluxo HTTP real + testes de expiração/uso único/revogação + frontend HTTPS funcionando**, todos concluídos, e **sem alteração de conta real**. Não habilitar e-mails para lojistas antes de verificar domínio e fazer revisão de limites/rate limiting.
