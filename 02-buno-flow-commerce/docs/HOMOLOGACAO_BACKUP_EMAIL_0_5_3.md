# Flow Commerce 0.5.3 — homologação de backup real e e-mail
**Estado: PREPARADO, não homologado.** Data: 11/11/2026. Projeto Supabase dedicado: `pqdwxihinbmeiorkqdtq` (PostgreSQL 17.11).

## Situação confirmada
- Testes com PostgreSQL temporário do CI passaram, mas um backup REAL do Supabase não foi restaurado.
- Railway não possui SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD nem remetente de domínio verificado; envio real permanece desativado.
- O projeto Flow Commerce na Vercel lista apenas domínio vercel.app; é possível que a Buno tenha domínio em outro registrador, ainda não identificado.
- **Nunca enviar dumps, credenciais, tokens, dados de clientes ou logs pessoais ao GitHub/ChatGPT.**

## Gate A — backup e restauração real LOCAL, sem alterar o Supabase
Fluxo: fonte Supabase com usuário **somente leitura** → `pg_dump -Fc` → volume Windows **criptografado** fora do Git → PostgreSQL 17 **local e vazio** → `pg_restore` → conferir contagens e integridade.

Pré-requisitos: Windows autorizado, Python 3, ferramentas PostgreSQL 17 (`psql`, `pg_dump`, `pg_restore`), conexão TLS ao Supabase e diretório criptografado e privado. Os scripts não conseguem provar criptografia do volume; o operador deve verificá-la.

Se não existir PostgreSQL 17 local, crie um banco **descartável**, com Docker Desktop já iniciado, sem substituir nenhum container existente:
```powershell
docker info
$secure = Read-Host "Senha PostgreSQL LOCAL descartável" -AsSecureString
$env:POSTGRES_PASSWORD = [System.Net.NetworkCredential]::new("",$secure).Password
docker run --name flow-restore-qa -d --restart no -p 127.0.0.1:55432:5432 -e POSTGRES_PASSWORD -e POSTGRES_DB=flow_restore_qa postgres:17-alpine
Remove-Item Env:\POSTGRES_PASSWORD
```
A senha do container permanece acessível a administradores locais por Docker inspect: não compartilhe os metadados do container. A porta é publicada somente em loopback.

Crie por administração aprovada um usuário PostgreSQL de BACKUP SOMENTE LEITURA: `USAGE` no schema `flow_commerce`, `SELECT` em todas as tabelas/sequências; nenhuma permissão de INSERT/UPDATE/DELETE/TRUNCATE. Não registre senha em SQL no Git nem no chat.

Execute na pasta `02-buno-flow-commerce` do Windows, com volume protegido já preparado:
```powershell
.\scripts\homologar_backup_real_windows.ps1 -SourceHost "db.pqdwxihinbmeiorkqdtq.supabase.co" -SourceUser "<USUARIO_READ_ONLY>" -BackupDirectory "X:\FlowBackupsPrivados" -LocalDatabase "flow_restore_qa" -LocalPort 55432 -ConfirmEncryptedStorage
```
Se a rede não suportar IPv6 para o host direto, use o Session Pooler apresentado em Supabase → Connect. O nome do usuário pooler deve terminar em `.pqdwxihinbmeiorkqdtq`.

O script confere origem específica, exige armazenamento criptografado confirmado pelo operador, verifica ferramentas PG17, inspeciona se a role possui permissão de escrita e bloqueia-a, solicita senhas localmente sem imprimi-las, executa o verificador já testado e remove as variáveis de credenciais ao final.

**Não executar restore em nenhuma instância hospedada existente.** O dump lógico do schema privado não recupera Storage, configurações do projeto nem dados de outro schema. Para homologação registrar contagens equivalentes, integridade, data, duração, política de retenção e responsável; apenas resultados agregados, sem arquivos sensíveis.

## Gate B — provedor Resend, domínio DNS e SMTP
1. Identificar o **domínio que a Buno controla** e onde o DNS é administrado. Não há prova de titularidade nesta conversa.
2. Criar/acessar uma conta Resend autorizada. Em https://resend.com/domains cadastrar o domínio/subdomínio de envio; copiar exatamente os registros SPF, DKIM e outros **específicos do provedor** para o DNS. Não inventar registros e não substituir MX existentes de e-mail sem revisão.
3. Aguardar o status **Verified** no Resend. Se tiver uma chave com permissão de leitura dos domínios, usar localmente:
```powershell
.\scripts\verificar_dominio_resend.ps1 -Domain "<DOMINIO_VERIFICADO_DA_BUNO>"
```
O script lê o estado sem publicar a chave nem modificar DNS. Ele não comprova entrega SMTP.
4. Criar chave de envio com menor escopo possível e configurar no Railway **somente após verificação do domínio**. Não enviar segredo pelo chat ou commit:

| Variável | Valor |
|---|---|
| `SMTP_HOST` | `smtp.resend.com` |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME` | `resend` |
| `SMTP_PASSWORD` | Chave Resend privada, somente no Railway |
| `SMTP_AUTH` | `true` |
| `SMTP_STARTTLS` | `true` |
| `COMMERCE_MAIL_FROM` | Endereço no domínio realmente verificado |
| `COMMERCE_FRONTEND_URL` | HTTPS do Flow Commerce realmente acessível ao testador |
| `COMMERCE_MAIL_ENABLED` | `false` até o teste autorizado |

5. O backend 0.5.3 ainda não foi implantado no Railway; após PR aprovado, planejar deploy privado com rollback, habilitar o SMTP **apenas em staging**, solicitar recuperação com e-mail de conta fictícia controlada, verificar recebimento, uso único, expiração e revogação da sessão anterior.
6. **Vercel Authentication permanece ligada**: o destinatário de teste precisa de autorização Vercel para abrir o link. Nunca remover SSO apenas para completar o teste.
7. O cadastro ainda não tem verificação de endereço: isso continua bloqueio antes de liberar novos lojistas.

## Gate C — critérios finais
- [ ] Backup REAL exportado de Supabase, armazenado cifrado e restaurado em banco PostgreSQL 17 isolado com verificação de integridade.
- [ ] Domínio Buno verificado no Resend e DNS publicado no painel competente.
- [ ] E-mail real de recuperação recebido por conta fictícia, com sessões revogadas e token de uso único.
- [ ] Monitoramento, isolamento entre lojas, LGPD, termos, faturamento e autorização para até 3 pilotos.

**Fonte:** https://supabase.com/docs/guides/platform/backups ; https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore ; https://resend.com/docs/send-with-smtp ; https://resend.com/domains

Este PR não cria backups reais, não envia e-mails e não altera Supabase, DNS ou Railway.