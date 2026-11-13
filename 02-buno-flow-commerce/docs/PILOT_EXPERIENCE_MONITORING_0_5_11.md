# Flow Commerce 0.5.11 — Pilot Experience & Operational Monitoring

**Status: PR rascunho empilhado sobre 0.5.10. Não implantado e NÃO liberado para clientes reais.**

## Implementado

1. **Painel de experiência do piloto:** interface React na visão geral mostra preparação do piloto privado, direciona para pedidos e segurança e disponibiliza teste de acesso **somente leitura**. O teste valida perfil da loja e indicadores por sessão autenticada; não concede autorização comercial.
2. **Emissão de alerta de QA:** `scripts/qa_alert_delivery.py` transforma relatórios sintéticos do gate anterior em eventos `type/status` sem tokens, URLs, endereços ou corpos de requisição. O canal de entrega é apenas um arquivo JSONL temporário em runner descartável (não e-mail, Telegram ou webhook).
3. **Testes de alerta:** `test_qa_alert_delivery.py` cobre evento de indisponibilidade, bloqueio de usuário anônimo, frontend indisponível, proteção contra overwrite, ambiente não descartável, falta de autorização, caminho relativo e redaction.
4. **Workflow:** `flow-commerce-pilot-monitoring-0511.yml` roda testes em CI e verifica uma emissão sintética local, eliminando o arquivo após o teste.
5. **Segurança e confiabilidade:** testes existentes de autenticação, MFA, isolamento por loja, regressão Java, recuperação de senha, ZAP autenticado e restauração descartável continuam obrigatórios no CI. Não há alterações no backend de produção ou em permissões reais.

## O que NÃO foi implementado

- Não há notificações externas reais. Arquivo JSONL local é somente uma prova do mecanismo de emissão, **não** de entrega a alguém.
- Não há gestão de estoque nova. O produto mantém a gestão de pedidos existente, e qualquer estoque real exige modelagem, regras de concorrência e testes específicos.
- Nenhuma melhoria visual alegada foi verificada manualmente em aparelho físico; compilar React e rodar Playwright não substitui revisão de UX mobile.
- Nenhum deploy, merge, DNS, cobrança, SMTP externo, rotação de chave MFA ou conexão a dados reais.

## Gates antes do piloto

- [ ] Todos os workflows de GitHub Actions do commit final aprovados
- [ ] Aprovação humana da experiência do lojista, em desktop e celular
- [ ] Revisão de cadastro, autorização de proprietário, isolamento entre duas lojas e logs
- [ ] Backup REAL restaurado com evidência sanitizada em PostgreSQL autorizado, vazio e isolado
- [ ] Remetente de recuperação por e-mail aprovado e entrega testada, ou alternativa formalmente aprovada
- [ ] Chave MFA rotacionada/migrada com cofre independente e recuperação validada
- [ ] Canal externo de alerta selecionado e teste de entrega autorizado
- [ ] Revisão independente de segurança, rollback e aprovação comercial

**Decisão do projeto:** compra do domínio próprio foi adiada; não bloquear engenharia, mas bloquear pilotos reais enquanto os requisitos obrigatórios permanecerem pendentes.

## Limites do QA

O painel de preparação de piloto é exibido para a sessão autenticada, sem criar pedidos. A checagem de conta usa os endpoints existentes `GET /api/v1/store` e `GET /api/v1/dashboard`. O backend continua como autoridade final para permissões. O simulador de alerta executa apenas no runner, sem credenciais e sem capacidade de comunicação com destinatários.
