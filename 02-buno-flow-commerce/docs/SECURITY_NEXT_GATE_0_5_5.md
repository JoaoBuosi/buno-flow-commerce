# Flow Commerce — Administração 2FA, backup durável e liberação comercial

**Status em 12/11/2026:** arquitetura e requisitos de implementação. **2FA não está habilitado nem homologado.** Esta especificação não deve ser confundida com autenticação multifator funcional.

## 1. Modelo de autorização atual
- A aplicação utiliza papéis de loja `OWNER` e `STAFF`; `OWNER` é o papel administrativo atual. Ainda não existe papel `ADMIN` global comprovado.
- O login atual emite token após validar exclusivamente a senha. Habilitar 2FA só na interface seria um bypass: a exigência deve existir no serviço que emite sessões.
- Não alterar as duas contas de staging até existir migração testada, interface para cadastrar 2FA e mecanismo de recuperação.

## 2. Fluxo 2FA proposto (prioritário)
1. `OWNER` entra com senha em sessão curta de inscrição, restrita a endpoints de 2FA, quando ainda não tem segundo fator. Rotas de pedido, exportação e administração não aceitam sessão apenas com senha após enforcement.
2. A pessoa confirma senha novamente para iniciar cadastro de TOTP, por aplicativo autenticador; gerar segredo criptograficamente aleatório com criptografia autenticada no banco e chave somente no servidor.
3. Verificar TOTP com tolerância mínima de relógio, aceitar um passo temporal somente uma vez por conta, e gerar códigos de recuperação de uso único, apresentados só no momento de cadastro e armazenados com hash forte.
4. Login regular: após senha correta, não emitir sessão operacional; gerar desafio de curta duração, limitado por IP/conta, e emitir token somente após TOTP ou código de recuperação válido.
5. `OWNER` deve confirmar 2FA para operações especialmente sensíveis (troca de credenciais, exportação em massa, novas permissões, exclusões).
6. Reset por e-mail não desabilita automaticamente 2FA. Prover recuperação assistida auditável, sem perda de isolamento entre lojas.
7. Desativação/troca de dispositivo somente com segundo fator válido e senha; revogar outras sessões e gerar evento de auditoria sem armazenar segredos/OTP em logs.
8. Suporte a passkeys/WebAuthn pode substituir ou complementar TOTP posteriormente. Não usar SMS como fator principal.

## 3. Testes obrigatórios antes de ativar
- Não criar token se TOTP obrigatório estiver ausente.
- Não permitir bypass enviando requisições diretas ao backend, ignorando telas do frontend.
- TOTP vencido, errado, reutilizado e submetido em paralelo falha.
- Códigos de recuperação: um uso, expiração/revogação no re-cadastro, sem retorno do código após a criação.
- Isolamento OWNER/STAFF, duas lojas, transações de revogação, testes E2E após restart, perda e rotação de chave e lockout/rate limit.
- Chave cifrante/TOTP nunca aparecer no banco em texto simples, URLs HTTP ou observabilidade.

## 4. Backup persistente cifrado
- Script experimental e **não agendado**: `scripts/durable_encrypted_backup.py`.
- Requer aprovação explícita, PostgreSQL Supabase aprovado (leitura somente, TLS obrigatório), age para cifrar dump antes de salvar localmente, e bucket S3 privado versionado com Object Lock e política de retenção pré-configurada.
- O objeto remoto precisa passar na verificação de tamanho. Isso **não prova recuperação**: restauração e auditoria de integridade a partir do objeto armazenado precisam ser executadas em banco local isolado, sem alterar Supabase.
- Antes de agendar: criar bucket com acesso mínimo, bloquear leitura pública, configurar retenção, monitorar falhas, fornecer age recipient público. A chave privada de descriptografia não deve coexistir com as credenciais de criação do backup.
- A integração não foi executada porque não há bucket nem credenciais desse storage disponíveis neste contexto.

## 5. WAF Vercel e prompt injection
- API Seawall da Vercel respondeu 404 nas tentativas anteriores. Nenhuma regra WAF foi configurada. O rate limit efetivo do Spring/Railway segue funcionando e sua atividade deve ser monitorada.
- Quando a Agent Platform/Buno AI integrar o Flow Commerce, trate texto de cliente, pedidos e páginas como **dados não confiáveis**, nunca instruções para o agente.
- Ferramentas de leitura/escrita precisam ser permitidas por tenant, com escopos mínimos e verificação no servidor, aprovação humana para ações financeiras/irreversíveis, auditoria, proteção contra exfiltração e testes adversariais.

## 6. Critério de liberação
Não liberar lojistas até comprovar: fluxo 2FA de OWNER end-to-end, proteção da credencial de recuperação, retenção de backup cifrado restaurável, domínio próprio verificado no provedor de e-mail, revisão de alertas individuais e teste de invasão autorizado independente.
