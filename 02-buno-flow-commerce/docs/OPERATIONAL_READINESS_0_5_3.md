# Flow Commerce 0.5.3 — Operação segura antes de 3 lojistas-piloto
**Situação:** desenvolvimento / PR em rascunho (11/11/2026).
**NÃO LIBERADO** para dados de clientes reais, checkout, SMTP produção ou tráfego comercial.

## Arquitetura e sequência de liberação

```text
CASE26 0.5.2 staging protegido (preservado)
     |
     v
0.5.3 PR: verificadores, recuperação por e-mail simulada, hardening, monitoramento opt-in
     |
     +--> Teste SMTP local sem credenciais reais (token, expiração, revogação)
     +--> Cópia pg_dump Supabase READ ONLY --> PostgreSQL LOCAL VAZIO
     +--> Checagem de disponibilidade SEM autenticar --> issue GitHub se indisponível
     +--> Revisão de isolamento, RLS, logs, rate limits e API
     |
     v
  GATE A: CI PASS + backup remoto restaurado localmente (evidência)
     |
     v
  GATE B: SMTP real configurado + e-mail entregue + domínio verificado,
          alertas reais e resposta a incidente, isolamento remoto
     |
     v
  GATE C: política/termos/DPA/faturamento/aceite responsáveis
     |
     v
Até 3 lojas-piloto reais, sem abertura pública
```

## 1. Backup/restore real do PostgreSQL dedicado (ainda pendente)
- Fonte existente: projeto Supabase `buno-flow-commerce` (Postgres 17, schema privado `flow_commerce`).
- **Script pronto**: `scripts/verify_isolated_restore.py`. Ele efetua `pg_dump` APENAS LEITURA na origem; só restaura em destino com host `localhost/127.0.0.1/::1`, banco com prefixo `flow_restore_` e **nenhuma tabela existente**. Não utiliza `DROP`, `--clean` ou `createdb` contra o banco da Buno.
- O script verifica contagens de nove tabelas e relações básicas após `pg_restore --single-transaction`, falhando caso haja divergência. Sua execução **NÃO foi autorizada/realizada sobre os dados hospedados** nesta etapa: não declarar o Gate A concluído.
- O arquivo `.dump` contém usuários, dados de pedidos e hashes de sessão; **é sensível**. Use sistema de arquivos privado com criptografia e política de descarte; nunca envie ao chat, GitHub Actions artifacts, Vercel ou anexos públicos.
- Antes de executar, garantir que a credencial de origem tenha **somente SELECT** e que nenhum pedido real precise ser exposto num notebook pessoal. Se houver dados de clientes reais, obter autorização, estabelecer política de backup e preferir o recurso gerenciado do Supabase em projeto novo.
- [Documentação Supabase: Backups](https://supabase.com/docs/guides/platform/backups)
- [Restore a new project](https://supabase.com/docs/guides/platform/clone-project) — disponível conforme plano e condições; não executar sem aprovação de custos.

### Exemplo de execução CONTROLADA no Windows (operador autorizado)
```powershell
# Pré-requisitos: PostgreSQL 17 client tools (pg_dump, pg_restore, psql)
# e banco PostgreSQL LOCAL NOVO e VAZIO chamado flow_restore_qa.
# NÃO use o Supabase como destino.
$env:FLOW_BACKUP_SOURCE_PGHOST = "<HOST aprovado Supabase>"
$env:FLOW_BACKUP_SOURCE_PGPORT = "5432"
$env:FLOW_BACKUP_SOURCE_PGDATABASE = "postgres"
$env:FLOW_BACKUP_SOURCE_PGUSER = "<usuário read-only>"
$env:FLOW_BACKUP_SOURCE_PGSSLMODE = "require"
$secret = Read-Host "Senha da origem" -AsSecureString
$env:FLOW_BACKUP_SOURCE_PGPASSWORD = [System.Net.NetworkCredential]::new("",$secret).Password
$env:FLOW_RESTORE_PGHOST = "127.0.0.1"
$env:FLOW_RESTORE_PGPORT = "5432"
$env:FLOW_RESTORE_PGDATABASE = "flow_restore_qa"
$env:FLOW_RESTORE_PGUSER = "<usuário local>"
$env:FLOW_RESTORE_PGSSLMODE = "disable"
$targetSecret = Read-Host "Senha PostgreSQL local" -AsSecureString
$env:FLOW_RESTORE_PGPASSWORD = [System.Net.NetworkCredential]::new("",$targetSecret).Password

# Armazene fora do repo numa pasta criptografada e privada já existente:
py -3 scripts/verify_isolated_restore.py --output "E:\PrivadoCriptografado\FlowBackups\flow-commerce-$(Get-Date -Format yyyyMMdd-HHmmss).dump"
# A linha acima apenas descreve a execução. Depois de conferir origem, destino,
# privacidade e permissões, repita com o argumento --execute para iniciar.
# Ao terminar, limpe credenciais do processo e descarte o arquivo por procedimento aprovado.
Remove-Item Env:\FLOW_BACKUP_SOURCE_PGPASSWORD
Remove-Item Env:\FLOW_RESTORE_PGPASSWORD
```

## 2. Envio de e-mail e recuperação (SMTP REAL desativado)
- `PasswordResetService` já gera token opaco aleatório, armazena somente SHA-256, expira em 20 minutos, invalida token após uso e revoga sessões anteriores.
- Esta PR adiciona testes de envio com `JavaMailSender` simulado, links únicos, e-mail inexistente sem enumeração por conteúdo, token expirado e rollback se SMTP falhar.
- Em falha SMTP, retorno HTTP 503 genérico. Não incluir token, endereço pessoal ou informações do provedor nos logs.
- Para habilitar envios reais, primeiro escolher um provedor, verificar domínio/SPF/DKIM/DMARC e aprovar o remetente. Configurar **apenas no Railway Variables** SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, COMMERCE_MAIL_FROM, COMMERCE_FRONTEND_URL, SMTP_STARTTLS etc. **Não habilitar COMMERCE_MAIL_ENABLED=true antes de teste real e revisão**.
- **Verificação de e-mail de cadastro ainda NÃO está implementada**. Conta é criada sem confirmar endereço, inadequado para liberação aberta; deve ser desenvolvida em incremento específico com revisão de migração, rate limiting e estados de conta.
- O e-mail de recuperação depende de senha da conta existente. Teste E2E remoto com endereço controlado antes de liberar.

## 3. Monitoramento e alerta (código pronto, workflow ainda NÃO ativo)
- `scripts/check_public_staging_health.py` verifica GET `/actuator/health` no Railway, 403 esperado para acesso direto sem segredo e disponibilidade básica de origem Vercel, **sem login, tokens, dados de clientes ou escrita**.
- Workflow `.github/workflows/flow-commerce-staging-monitor.yml` com execução horária e manual. Ele depende de **duas variables do repositório**: `FLOW_COMMERCE_HEALTH_API_ORIGIN` e `FLOW_COMMERCE_HEALTH_WEB_ORIGIN`. No primeiro erro abre uma única issue “Flow Commerce staging: indisponibilidade detectada”; na recuperação fecha. Precisa GitHub Issues/Actions autorizados.
- **Cron GitHub Actions só é executado na branch default após merge/revisão**, então código em PR não significa monitor ativo. Sem as duas variables, workflow declara configuração pendente sem gerar alertas.
- Não cobre monitoramento autenticado de gateway nem disponibilidade do Supabase do ponto de vista da loja. Complementar com telemetria 5xx e incident response quando autorizado, sem logar dados pessoais.

## 4. Revisão de segurança (em andamento)
| Item | Situação conhecida | Medida |
|---|---|---|
| Gateway Vercel → Railway | Segredo somente servidor e entrada direta negada | Preservar; testar 403 e CORS |
| Tenant isolation | Aplicação filtra `store_id`; testes CI de duas lojas | Repetir remoto com contas sintéticas |
| JWT/sessões | Usa bearer aleatório hash SHA-256 e expiração 12h (não JWT) | Rejeitar strings malformadas antes de hash |
| Rate limiter | Em memória e IP de socket; atrás do proxy pode agrupar clientes | BLOQUEIO para escalar; usar mecanismo centralizado de borda/distribuído |
| RLS no banco | 9 tabelas privadas sem RLS; `anon`, `authenticated` e `service_role` sem USAGE/SELECT no schema | Defesa em profundidade pendente; **não ativar RLS sem política/testes**, pois quebraria o usuário PostgreSQL dedicado |
| Logs e retenção | Dados de auditoria por pedido existentes | Definir retenção, minimizar PII, não registrar tokens e credenciais |
| SMTP | Desativado no staging | Provedor verificado + ensaios antes de ligar |
| Backup remoto | Nunca restaurado num banco isolado nesta conversa | Executar procedimento com autorização e evidência |
| Login remoto | SSO Vercel exige sessão do dono | Homologação manual sem desativar SSO |

## 5. Critérios de aceite e rollback
- [ ] GitHub Actions: Java integration, front build, Python tests e browser Playwright PASS.
- [ ] Backup real exportado de fonte aprovada e restaurado no PostgreSQL local dedicado, sem alterações na origem; contagens e integridade conferidas, backup protegido, evidência somente agregada.
- [ ] E-mail real recebido em endereço controlado; recuperação expira, uso único e sessões revogadas.
- [ ] Checagem horária configurada, issue de teste/recuperação validada e destinatário que monitora as notificações.
- [ ] Revisão de RLS/permissões e tenant isolation, teste de rate-limit real atrás do proxy.
- [ ] Verificação do cadastro, política de privacidade, DPA, suporte, faturamento/CNPJ e aval dos responsáveis.
- [ ] Manter PR draft, sem merge na main nem deploy backend automático.
- Rollback: remover variáveis de monitor quando ativo, desabilitar SMTP, selecionar Vercel preview READY anterior. Mudanças no Java requerem redeploy aprovado da versão anterior; nunca realizar restore destrutivo sobre Supabase.

**Nenhuma declaração de "piloto real liberado" até todas as evidências críticas.**


## Evidência remota não destrutiva — 11/11/2026

A verificação automatizada de disponibilidade foi executada pela GitHub Action de branch
`.github/workflows/flow-commerce-live-readonly-smoke.yml` e **passou**:
- API Railway `/actuator/health`: HTTP 200 e `status=UP`;
- API `/api/v1/orders` acessada diretamente sem segredo do gateway: HTTP 403;
- Staging privado da Vercel: host acessível (isso **não** substitui sessão SSO).

Execução: https://github.com/JoaoBuosi/buno-flow-commerce/actions/runs/43038932227

A verificação SQL `scripts/validate_flow_commerce_integrity.sql` foi executada no
Supabase dedicado por conector autorizado, usando **somente SELECT**, e retornou
**zero** para nove verificações: pedidos, itens, eventos, associação a lojas,
importações, memberships, sessões e validação das FKs. Esse resultado **não**
comprova isolamento entre usuários ou recuperação de backup. Não exibe dados pessoais.

### Execução repetível

Execute o SQL apenas com uma conta de leitura aprovada e sob conexão SSL/TLS:
```powershell
# PostgreSQL client instalado e conexão read-only previamente autenticada.
# Não salve senha ou URI com credenciais no arquivo, GitHub ou PowerShell history.
psql -X -v ON_ERROR_STOP=1 -f scripts/validate_flow_commerce_integrity.sql
```

**O que continua bloqueado:** restore de uma cópia real em PostgreSQL isolado,
SMTP real e verificação de e-mail, monitor horário ativo em `main`, homologação
SSO com duas lojas sintéticas e documentos/condições legais e fiscais. A Action
de *live smoke* roda apenas no PR de engenharia e **não** envia alertas recorrentes.
O workflow de alertas por GitHub Issues permanece opt-in após revisão.
