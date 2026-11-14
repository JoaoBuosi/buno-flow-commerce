# Backup real — Flow Commerce 0.5.12 (Windows e PostgreSQL 17)

**Status: procedimento preparado; nenhuma exportação/restauração real executada.**

## Inventário confirmado somente por catálogo (13/11/2026)

- Supabase: projeto buno-flow-commerce, referência pqdwxihinbmeiorkqdtq.
- PostgreSQL 17.11; 13 tabelas no schema privado flow_commerce.
- Tabelas: auth_ip_rate_limits, memberships, order_events, order_imports, order_items, orders, owner_mfa, owner_mfa_challenges, owner_mfa_recovery_codes, password_resets, stores, user_sessions, users.
- A role flow_backup_export já foi criada e auditada: NOLOGIN, sem senha provisionada, com SELECT nas 13 tabelas e sem escrita, BYPASSRLS ou privilégios administrativos. **Não executar novamente CREATE ROLE.**
- O catálogo mostrou RLS desativada nessas 13 tabelas privadas. Confirmar que flow_commerce não está exposto diretamente na Data API e que a API Spring aplica separação entre lojas.
- O Security Advisor apontou três avisos informativos de RLS habilitada sem policy em public.orders, public.workflow_requests e public.workflow_audit_events. Investigar separadamente sem confundir public.orders com flow_commerce.orders ou ampliar acesso.

## Pré-requisitos — responsável precisa validar

- [ ] Estação Windows autorizada e ferramenta PostgreSQL 17: pg_dump, pg_restore, psql.
- [ ] Volume local privado criptografado, BitLocker ligado e completamente criptografado, ou solução equivalente com revisão independente; acesso e retenção definidos.
- [ ] PostgreSQL 17 local, banco vazio chamado flow_restore_*, endpoint em 127.0.0.1 sem túnel para hosts remotos.
- [ ] Role remota de exportação aprovada, **somente leitura**; senha gerada e armazenada em cofre independente. NÃO registrar valores no chat, GitHub, GitHub Actions, shell history ou arquivos de evidência.
- [ ] Origem Supabase do projeto correto, conexão TLS com verificação apropriada e permissões mínimas; não usar postgres, supabase_admin ou service_role.
- [ ] Responsável autorizar janela, política de descarte e local de evidência sanitizada. Dados de contas e MFA são sensíveis.

## Registro histórico da role — NÃO executar CREATE ROLE novamente

O SQL abaixo é apenas um exemplo histórico do provisionamento planejado. A conta já existe no Supabase e a etapa de criação foi executada anteriormente. **Não reexecutar.** Não reabrir a conta para login antes de provisionar a senha em um cofre e obter autorização específica.

~~~sql
-- Conta sem senha até provisionamento seguro por administrador autorizado.
CREATE ROLE flow_backup_export WITH NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
GRANT CONNECT ON DATABASE postgres TO flow_backup_export;
GRANT USAGE ON SCHEMA flow_commerce TO flow_backup_export;
GRANT SELECT ON ALL TABLES IN SCHEMA flow_commerce TO flow_backup_export;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA flow_commerce TO flow_backup_export;
~~~

Use uma role existente, se houver uma aprovada e equivalente, em vez de criar outra. Não conceder INSERT/UPDATE/DELETE, superuser, BYPASSRLS, CREATE, USAGE/UPDATE em sequences (permitem avançar ou alterar sequência), acesso público ou permissões de outros schemas. Reavaliar grants após migrações; revogar credenciais temporárias ao final. O helper verifica privilégios reais e aborta antes de criar dump se houver escrita/administração, BYPASSRLS ou participação em outras roles. A role foi criada com **NOLOGIN**: mesmo que seja criada, não poderá autenticar. Após o responsável registrar uma senha forte em cofre independente e verificar as permissões, a ativação de LOGIN e a configuração da senha exigem operação supervisionada e autorização específica; nunca informar a senha pelo chat ou por script versionado.

### Verificação da role após aprovação (somente metadados)

A role exata `flow_backup_export` está presente no PostgreSQL e sua configuração NOLOGIN foi verificada. Quando houver autorização para criá-la, o responsável deverá confirmar em consulta de leitura que:
- Durante o provisionamento inicial, `rolcanlogin=false`; somente após a etapa supervisionada de senha e ativação, confirmar `rolcanlogin=true`. Em ambas, `rolsuper=false`, `rolcreaterole=false`, `rolcreatedb=false`, `rolreplication=false`, `rolbypassrls=false`;
- não há associações de role em `pg_auth_members`;
- não existem privilégios de escrita em tabelas, colunas ou sequências; somente leitura de `flow_commerce`;
- o acesso com credencial do cofre funciona por TLS e as consultas de leitura são permitidas, mas INSERT/UPDATE/DELETE/nextval não são.

Nunca registrar segredo em PR, SQL, saída de log ou chat. Permissões mínimas não substituem confirmação de que o schema está privado e o destino é realmente isolado.

## Executar em Windows com BitLocker — caminho supervisionado (0.5.13)

O roteiro acima continua válido; há agora um executor que adiciona checagem de ACL da pasta,
solicita credenciais sem imprimi-las e registra SHA-256 depois de concluir dump e restauração.
**Não foi executado na máquina do proprietário.**

Antes de executar, o administrador deverá **ativar o login da role somente após**
provisionar sua senha diretamente no cofre independente, por método que não exponha o
segredo em chat, arquivo versionado ou histórico. A role foi criada com NOLOGIN e segue assim.
Não reutilizar a conta administrativa do Supabase.

1. No Windows autorizado, selecione uma pasta exclusiva em volume BitLocker totalmente
   criptografado, com ACL sem acesso a Everyone, Users, Authenticated Users ou Guests.
   Confira a cópia offline da chave de recuperação BitLocker em local independente.
2. Instale PostgreSQL **17** local (psql, pg_dump e pg_restore) e Python 3 (com py.exe).
   Crie manualmente uma base **vazia** cujo nome inicie com flow_restore_, apenas em
   127.0.0.1, sem túnel ou proxy remoto.
3. Configure as variáveis FLOW_BACKUP_SOURCE_PGHOST, PGPORT, PGDATABASE,
   PGUSER=flow_backup_export (ou o formato de pooler específico aprovado),
   PGSSLMODE=verify-full, e FLOW_RESTORE_PGHOST=127.0.0.1, PGPORT,
   PGDATABASE=flow_restore_*, PGUSER, PGSSLMODE=disable. **Não configure**
   FLOW_BACKUP_SOURCE_PGPASSWORD nem FLOW_RESTORE_PGPASSWORD previamente:
   o executor solicitará as senhas diretamente, lidas do seu cofre.
4. Faça o pré-check sem backup:

~~~powershell
Set-Location "<repositorio>\02-buno-flow-commerce"
.\scripts\run_windows_encrypted_backup.ps1 -OutputDirectory "X:\FlowBackupsPrivados"
~~~

5. Depois de confirmar manualmente origem, rede, BitLocker, permissões, base de
   restauração realmente vazia e a role habilitada, execute:

~~~powershell
.\scripts\run_windows_encrypted_backup.ps1 -OutputDirectory "X:\FlowBackupsPrivados" -Execute
~~~

O comando 5 pergunta as senhas de origem e destino no console sem ecoar, exige
a confirmação literal BACKUP-ISOLADO, cria arquivo de nome exclusivo, executa
pg_dump e pg_restore com contas de escopo mínimo e grava um arquivo .sha256 no
mesmo volume privado. A verificação de integridade complementar e o aceite humano
continuam necessários. Não enviar o dump ao GitHub ou ao ChatGPT; enviar
somente status e evidências **sanitizadas**.

**Limite técnico:** o executor não consegue provar sozinho que 127.0.0.1
não foi redirecionado por software de túnel/proxy e que a senha está guardada
em cofre externo. Essas validações permanecem responsabilidade do operador.

## Etapa 1 — estação e preflight SEM conexão

Na estação Windows autorizada, configure a pasta no volume cifrado. Os caminhos são exemplos, não valores da sua máquina.

~~~powershell
Set-Location "<repositorio>\02-buno-flow-commerce"
.\scripts\check_windows_backup_station.ps1 -OutputDirectory "X:\FlowBackupsPrivados"
~~~

O script apenas verifica volume local com BitLocker ativo e PostgreSQL 17 instalado. Se houver VeraCrypt ou outra solução cifrada, exigir avaliação manual, não ignorar o bloqueio.

Configure em segredo variáveis com prefixos FLOW_BACKUP_SOURCE_ e FLOW_RESTORE_ em sua estação, incluindo PGHOST, PGPORT, PGDATABASE, PGUSER e PGSSLMODE. Para o projeto atual, o database normalmente é postgres; confirme pela conexão aprovada, não por suposição. A origem deve exigir TLS; o destino deve ser local e vazio. Não passar senhas na linha de comando e não usar variáveis globais libpq que possam redirecionar conexões.

~~~powershell
py -3 scripts/real_restore_preflight.py --output "X:\FlowBackupsPrivados\backup-novo.dump"
~~~

Esse preflight é estrutural e não acessa dados. O operador ainda precisa verificar criptografia, ACL, DNS/certificado, porta sem encaminhamento, role, destino vazio, retenção e destino correto.

## Etapa 2 — execução exclusivamente na estação autorizada

**Somente após** aprovação dos pré-requisitos acima:

~~~powershell
py -3 scripts/verify_isolated_restore.py --output "X:\FlowBackupsPrivados\backup-novo.dump" --execute
~~~

O helper exporta apenas flow_commerce, cria um arquivo novo com acesso restrito e restaura no banco local vazio usando transação única. O verificador agora exige as 13 tabelas críticas, também detecta novas tabelas por catálogo e compara o conjunto completo e as respectivas contagens. Não faz writes na origem. Se falhar, o dump parcial ainda pode conter dados pessoais: proteger, registrar e descartar segundo política; nunca enviá-lo ao GitHub ou ao chat.

## Aceite operacional

- [ ] Origem, destino, credenciais e volume auditados.
- [ ] Backup real armazenado de forma criptografada, com checksum registrado em evidência sem PII.
- [ ] Restauração íntegra e verificador executado com sucesso.
- [ ] Integridade complementar de dados e dependências da aplicação conferida no destino desconectado, sem enviar mensagens nem consultar terceiros.
- [ ] Janela de recuperação e RPO/RTO registrados; retenção e descarte definidos.
- [ ] Evidência sanitizada assinada por responsável, com referência do commit usado.
- [ ] Gate comercial 0.5.12 atualizado somente após revisão independente das evidências.

A comparação de contagens não é uma prova criptográfica de equivalência linha a linha. Backups automáticos do Supabase podem complementar a estratégia, mas não substituem este ensaio de restauração em ambiente isolado. Nunca executar restauração sobre o banco de origem.
