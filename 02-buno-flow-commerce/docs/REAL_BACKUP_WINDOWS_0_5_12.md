# Backup real — Flow Commerce 0.5.12 (Windows e PostgreSQL 17)

**Status: procedimento preparado; nenhuma exportação/restauração real executada.**

## Inventário confirmado somente por catálogo (13/11/2026)

- Supabase: projeto buno-flow-commerce, referência pqdwxihinbmeiorkqdtq.
- PostgreSQL 17.11; 13 tabelas no schema privado flow_commerce.
- Tabelas: auth_ip_rate_limits, memberships, order_events, order_imports, order_items, orders, owner_mfa, owner_mfa_challenges, owner_mfa_recovery_codes, password_resets, stores, user_sessions, users.
- Nenhuma role com nome iniciando em flow_backup_ foi encontrada. Isso não exclui a possibilidade de uma conta de exportação com outro nome — verificar antes de criar qualquer papel.
- O catálogo mostrou RLS desativada nessas 13 tabelas privadas. Confirmar que flow_commerce não está exposto diretamente na Data API e que a API Spring aplica separação entre lojas.
- O Security Advisor apontou três avisos informativos de RLS habilitada sem policy em public.orders, public.workflow_requests e public.workflow_audit_events. Investigar separadamente sem confundir public.orders com flow_commerce.orders ou ampliar acesso.

## Pré-requisitos — responsável precisa validar

- [ ] Estação Windows autorizada e ferramenta PostgreSQL 17: pg_dump, pg_restore, psql.
- [ ] Volume local privado criptografado, BitLocker ligado e completamente criptografado, ou solução equivalente com revisão independente; acesso e retenção definidos.
- [ ] PostgreSQL 17 local, banco vazio chamado flow_restore_*, endpoint em 127.0.0.1 sem túnel para hosts remotos.
- [ ] Role remota de exportação aprovada, **somente leitura**; senha gerada e armazenada em cofre independente. NÃO registrar valores no chat, GitHub, GitHub Actions, shell history ou arquivos de evidência.
- [ ] Origem Supabase do projeto correto, conexão TLS com verificação apropriada e permissões mínimas; não usar postgres, supabase_admin ou service_role.
- [ ] Responsável autorizar janela, política de descarte e local de evidência sanitizada. Dados de contas e MFA são sensíveis.

## Proposta de criação da role, NÃO executar sem aprovação de mudança no Supabase

O SQL abaixo é um modelo para revisão por responsável autorizado. Não o apliquei.

~~~sql
-- Conta sem senha até provisionamento seguro por administrador autorizado.
CREATE ROLE flow_backup_export WITH NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
GRANT CONNECT ON DATABASE postgres TO flow_backup_export;
GRANT USAGE ON SCHEMA flow_commerce TO flow_backup_export;
GRANT SELECT ON ALL TABLES IN SCHEMA flow_commerce TO flow_backup_export;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA flow_commerce TO flow_backup_export;
~~~

Use uma role existente, se houver uma aprovada e equivalente, em vez de criar outra. Não conceder INSERT/UPDATE/DELETE, superuser, BYPASSRLS, CREATE, USAGE/UPDATE em sequences (permitem avançar ou alterar sequência), acesso público ou permissões de outros schemas. Reavaliar grants após migrações; revogar credenciais temporárias ao final. O helper verifica privilégios reais e aborta antes de criar dump se houver escrita/administração, BYPASSRLS ou participação em outras roles. A role é proposta inicialmente com **NOLOGIN**: mesmo que seja criada, não poderá autenticar. Após o responsável registrar uma senha forte em cofre independente e verificar as permissões, a ativação de LOGIN e a configuração da senha exigem operação supervisionada e autorização específica; nunca informar a senha pelo chat ou por script versionado.

### Verificação da role após aprovação (somente metadados)

A role exata `flow_backup_export` não existia na consulta de catálogo mais recente. Quando houver autorização para criá-la, o responsável deverá confirmar em consulta de leitura que:
- Durante o provisionamento inicial, `rolcanlogin=false`; somente após a etapa supervisionada de senha e ativação, confirmar `rolcanlogin=true`. Em ambas, `rolsuper=false`, `rolcreaterole=false`, `rolcreatedb=false`, `rolreplication=false`, `rolbypassrls=false`;
- não há associações de role em `pg_auth_members`;
- não existem privilégios de escrita em tabelas, colunas ou sequências; somente leitura de `flow_commerce`;
- o acesso com credencial do cofre funciona por TLS e as consultas de leitura são permitidas, mas INSERT/UPDATE/DELETE/nextval não são.

Nunca registrar segredo em PR, SQL, saída de log ou chat. Permissões mínimas não substituem confirmação de que o schema está privado e o destino é realmente isolado.

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
