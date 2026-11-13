# Flow Commerce 0.5.9 — homologação operacional isolada

Estado: preparação técnica. **Não é autorização para implantar, ativar MFA ou usar dados reais.**

## 1. O que o PR consegue verificar automaticamente

- **CI completo 0.5.9**: Java/PostgreSQL, navegador, DAST anônimo e autenticado, CodeQL/dependências, gate documental.
- **Backup e restore sintéticos de PostgreSQL 17**: workflow `flow-commerce-disposable-restore.yml`. Usa contêiner com `--network none`, banco sintético com três pedidos fictícios, `pg_dump -Fc`, `pg_restore --exit-on-error`, compara contagem, integridade do conteúdo e presença da estrutura. Destrói contêiner e dump ao sair; nenhum volume ou conexão com o Supabase.
- O script exige `CI=true`, `FLOW_DISPOSABLE_RESTORE=explicit-local-only` e rejeita `DATABASE_URL`, `PGHOST` ou `PGSERVICE`. Não usar o script para dados reais.
- Nenhum segredo de produto ou credencial existente é requerido. O PostgreSQL tem senha sintética exclusiva do job.

## 2. Backup REAL — homologação separada com operador autorizado

Não executar no GitHub runner. Requer máquina segura, armazenamento cifrado fora do repositório, PostgreSQL 17 local descartável, acesso de exportação mínimo ao banco de origem e destino **sem conexão possível com Supabase de staging/produção**.

1. Conferir e documentar autorização, escopo, retenção, espaço em disco e possibilidade de exportação consistente.
2. Gerar `pg_dump` com ferramentas PostgreSQL 17 a partir de uma credencial de exportação temporária, sem registrar string de conexão, dump ou dados de clientes nos logs.
3. Armazenar dump cifrado em diretório de acesso restrito, com checksum SHA-256; não enviar para GitHub Actions, PR ou chat.
4. Restaurar somente em PostgreSQL 17 local de teste, com identificadores de conexão explicitamente revisados e sem privilégios de acesso a bancos compartilhados.
5. Verificar migrações, objetos de schema, contagens agregadas e hash do arquivo; não divulgar PII ou detalhes de linhas.
6. Medir RPO/RTO, registrar evidência sanitizada e apagar dump/instância conforme política de retenção.

**Critério de aprovação:** restauração íntegra e comprovada, isolamento físico/lógico, acesso autorizado e revisão humana. O drill sintético não substitui a prova real.

## 3. Recuperação de senha por e-mail — conta descartável

1. Confirmar domínio remetente verificado (DNS SPF/DKIM/DMARC conforme provedor) e credenciais armazenadas somente no cofre/ambiente autorizado.
2. Habilitar envio apenas em staging separado, com destinatário QA explícito, nunca broadcast nem base de clientes.
3. Solicitar recuperação com conta sintética; confirmar recebimento real, URL HTTPS correta, expiração, token de uso único e resposta genérica para endereços inexistentes.
4. Verificar revogação de sessões e limites de taxa, ausência de token em logs e comportamento de repetição/expiração.
5. Desabilitar configuração temporária, remover a conta de QA, registrar somente resultados e referências de evidência sanitizada.

**Critério de aprovação:** envio e redefinição efetivos observados pelo operador em ambiente autorizado. Este PR não realiza envio de e-mail.

## 4. MFA — cofre e recuperação

1. Identificar se existem segredos MFA persistidos sob a chave atual; se existirem, **não substituir a chave diretamente**, pois isso pode inutilizar os dados cifrados.
2. Definir plano de migração/versionamento com recriptografia auditada ou reenrolamento supervisionado antes de rotacionar.
3. Gerar nova chave com gerador criptográfico em ambiente autorizado; armazenar no gerenciador independente com controle de acesso e recuperação.
4. Testar restauração da chave em ambiente descartável e depois atualizar o segredo no ambiente correto por fluxo aprovado, sem registrar valores.
5. Verificar TOTP, código de recuperação único e revogação de sessões numa conta QA. Manter `COMMERCE_OWNER_MFA_REQUIRED=false` até aprovação explícita.

## 5. Revisão independente e gate final

- Triar os avisos restantes de ZAP (COEP, cache, SPA), sessões, CSP, isolamento, IDOR, autorização de exportação, CSRF, rate limits e proteção de segredos.
- Solicitar revisão/pentest independente com autorização e escopo explícitos. **Não fazer active scan em ambiente compartilhado**.
- Preencher evidências **sem credenciais** para `pilot_readiness_gate.py`; o gate verifica apenas a forma dos dados, não a veracidade das provas.
- Aprovação humana assinada e estratégia de rollback exigidas antes de qualquer piloto com lojas reais.

## 6. Estado e restrições

- PR #16 permanece rascunho; sem merge, deploy, DNS, banco compartilhado ou mudança de chave real.
- As evidências de backup real, e-mail real, cofre MFA e pentest são **pendentes**, mesmo quando todos os jobs de CI estiverem verdes.
- Os scripts de homologação do PR #10 não estão presentes nesta branch; conferir e revisar esses scripts separadamente antes de reutilizá-los.
