# Flow Commerce 0.5.6 — ativação gradual e segura do 2FA OWNER

Data: 12/11/2026. **PR #12 em rascunho. Ainda NÃO publicado no Railway/Vercel.**
Base técnica: PR #11, com migração V6 e TOTP validados no staging.

## Estado real de início (consulta agregada ao Supabase staging)
- Contas com papel OWNER: **2**
- OWNER com 2FA concluído: **0**
- Códigos de recuperação usados: **0**
- Pedidos: **2**
- 2FA obrigatório para todos: `COMMERCE_OWNER_MFA_REQUIRED=false`
- Piloto por e-mail: desativado.
- A chave AES está em variável Railway, mas sua **cópia independente recuperável ainda não foi comprovada**.

## Nova experiência de configuração voluntária (PR #12)
1. O OWNER já autenticado abre **Configurações → Segurança da conta → Autenticação em duas etapas**.
2. A interface consulta `GET /api/v1/auth/mfa/status`: `enrolled`, `required`, `enrollmentAvailable`.
3. A opção só aparece se `COMMERCE_OWNER_MFA_SELF_ENROLLMENT_ENABLED=true`; **default false** para impedir cadastro sem backup comprovado da chave cifrante.
4. O OWNER revalida a senha no backend via `POST /api/v1/auth/mfa/self/enroll` (Bearer + X-Store-Id + `currentPassword`). Apenas papel OWNER pode iniciar. A rota também recebe rate limit por IP.
5. O backend emite desafio de 5 minutos, **não revoga a sessão atual enquanto o código TOTP ainda não foi confirmado**.
6. O OWNER abre app autenticador, adiciona conta por chave/URI TOTP e confirma código de 6 dígitos.
7. O backend salva o segredo cifrado e 8 hashes de recuperação e **revoga sessões pré-2FA**; a conta passa a exigir MFA em TODOS os próximos logins, mesmo com a flag global desligada.
8. A UI exibe os oito códigos de recuperação **uma única vez**; para avançar, o OWNER deve declarar que os guardou em um cofre pessoal.
9. Depois disso, testar logout/login com TOTP e novo login com **um** código de recuperação. O código usado deve falhar em tentativas seguintes.

## Bloqueios antes de disponibilizar a tela aos dois OWNER existentes
- [ ] **Backup independente da chave** `COMMERCE_MFA_ENCRYPTION_KEY`: salvar diretamente num gerenciador de segredos (ex.: Bitwarden/1Password), protegido por 2FA, com controle de acesso e backup testado. **Nunca enviar para chat, GitHub, arquivos de frontend, e-mail ou logs**. Se a chave não for legível/recuperável do Railway, gerar e provisionar nova chave ANTES da primeira conta real ser inscrita; não há usuários MFA inscritos atualmente.
- [ ] Revisão humana de PR #12 e CI de integração, navegador, CodeQL, Gitleaks, Trivy e npm.
- [ ] Publicar backend e frontend primeiro com `COMMERCE_OWNER_MFA_SELF_ENROLLMENT_ENABLED=false` e `COMMERCE_OWNER_MFA_REQUIRED=false`.
- [ ] Provar health, login das duas contas e permissões das tabelas MFA; não executar login com senhas pessoais em automação.
- [ ] Somente com escrow comprovado, habilitar `COMMERCE_OWNER_MFA_SELF_ENROLLMENT_ENABLED=true`; manter obrigatoriedade global false.

## Acompanhamento individual
Para cada OWNER, um por vez:
- [ ] Proprietário confirmou autenticação por senha e cadastro TOTP no próprio dispositivo.
- [ ] Oito códigos de emergência guardados sem exposição no sistema de chamados/chat.
- [ ] Logout e novo login com TOTP aprovados.
- [ ] Um código de recuperação testado; **o mesmo código reutilizado é negado**.
- [ ] Conferência agregada no servidor: a quantidade de OWNER inscritos aumenta em 1. Não consultar/expor segredo TOTP ou códigos de recuperação.
- [ ] Confirmar acesso às operações da própria loja e isolamento entre lojas.

SQL de monitoramento sem dados pessoais:
```sql
SELECT
  (SELECT count(DISTINCT user_id) FROM flow_commerce.memberships WHERE role='OWNER') AS owner_total,
  (SELECT count(DISTINCT m.user_id)
     FROM flow_commerce.memberships m
     JOIN flow_commerce.owner_mfa f ON f.user_id=m.user_id
     WHERE m.role='OWNER' AND f.enabled_at IS NOT NULL) AS owner_enrolled;
```

## Habilitação global (somente quando TODOS concluírem)
Após confirmar `owner_total = owner_enrolled` e os testes de recuperação, habilitar `COMMERCE_OWNER_MFA_REQUIRED=true` no Railway em janela supervisionada. Monitorar 401, 429, 503, falhas TOTP e disponibilidade do serviço. Se houver interrupção, não desabilitar o 2FA de quem já está inscrito como atalho; usar procedimento de recuperação auditado. Não compartilhar a chave de criptografia com suporte ou usuários.

**Atenção:** o código do PR #12 ainda não realiza a recuperação assistida por perda de dispositivo E códigos; esse caso precisa de um processo operacional de verificação de identidade e registro de auditoria.
