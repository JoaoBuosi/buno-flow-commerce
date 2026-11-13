# Flow Commerce 0.5.11 — Pilot Experience & Operational Monitoring

**Status: PR rascunho empilhado sobre 0.5.10. Não implantado e NÃO liberado para clientes reais.**

## Implementado

1. **Painel de experiência do piloto:** interface React na visão geral mostra preparação do piloto privado, direciona para pedidos e segurança e disponibiliza teste de acesso **somente leitura**. O teste valida perfil da loja e indicadores por sessão autenticada; não concede autorização comercial.
2. **Emissão de alerta de QA:** `scripts/qa_alert_delivery.py` transforma relatórios sintéticos do gate anterior em eventos `type/status` sem tokens, URLs, endereços ou corpos de requisição. O canal de entrega é apenas um arquivo JSONL temporário em runner descartável (não e-mail, Telegram ou webhook).
3. **Testes de alerta:** `test_qa_alert_delivery.py` cobre evento de indisponibilidade, bloqueio de usuário anônimo, frontend indisponível, proteção contra overwrite, ambiente não descartável, falta de autorização, caminho relativo e redaction.
4. **Workflow:** `flow-commerce-pilot-monitoring-0511.yml` roda testes em CI e verifica uma emissão sintética local, eliminando o arquivo após o teste.
5. **Segurança e confiabilidade:** testes existentes de autenticação, MFA, isolamento por loja, regressão Java, recuperação de senha, ZAP autenticado e restauração descartável continuam obrigatórios no CI. Não há alterações no backend de produção ou em permissões reais.

## Homologação automatizada do painel do lojista (Playwright)

Novo arquivo `web/tests/pilot-readiness.e2e.spec.ts`, integrado à suíte browser existente. Somente no runner descartável, com API Spring em `127.0.0.1:8080`, PostgreSQL do CI e convite sintético:

- Sessão anônima não tem acesso ao painel; cadastro da loja QA e autenticação são necessários.
- Desktop: botão **Verificar meu acesso** usa somente `GET /store` e `GET /dashboard`, sempre com token e identificador da loja corretos, e não cria pedidos.
- Mobile (390×844): navegação painel → pedidos → visão geral → segurança, com verificação de overflow horizontal.
- Falha simulada HTTP 503 em `GET /store`: o painel informa indisponibilidade e não libera a demonstração.
- Segurança: backend nega token de uma loja associado a outra; se a resposta do gateway simular perfil de outra loja, o frontend também rejeita o resultado.
- `commerce.e2e.spec.ts` continua responsável pela cobertura integrada de cadastro, pedidos, pagamento/status, CSV, histórico, cancelamento, exportação, login novamente e isolamento.

**Escopo:** estes testes verificam o navegador Chromium em viewport de desktop e de celular, não substituem revisão humana no dispositivo nem comprovam e-mails reais, backups reais ou disponibilidade do staging.

## Evidências visuais para revisão humana (QA isolado)

A suíte Playwright ganhou `web/tests/pilot-visual-evidence.e2e.spec.ts`, com seis capturas de telas sintéticas:

- Desktop (1440×900): visão geral, pedidos e configurações.
- Mobile (390×844): visão geral, pedidos e configurações.
- Em todas as capturas, o teste comprova ausência de rolagem horizontal na página inteira. O identificador da loja, mesmo sendo fictício, é mascarado.
- Só é permitido executar a suíte contra API e frontend em `127.0.0.1`; `EXTERNAL_FRONTEND_URL` bloqueia o teste.
- Nenhum token, cookie, log de tráfego, senha, caixa QA ou backup é anexado.

O workflow `.github/workflows/flow-commerce-e2e.yml` publica o artifact **`flow-commerce-0511-synthetic-visual-review`** por **3 dias**. Para revisar: GitHub → PR #18 → Checks → execução `Flow Commerce - browser E2E` → Artifacts → download do ZIP. Inspecionar visualmente cada uma das seis telas no desktop e no celular. Caso não apareça o artifact, não marcar a revisão como concluída.

**Evidência automatizada não equivale a aprovação de UX por pessoa:** verificar legibilidade, foco, touch targets, contraste, clareza de mensagens, ordem de navegação e consistência entre telas. Aprovação depende de revisão humana explícita.

## Refino de acessibilidade e usabilidade (próxima etapa)

O formulário **Novo pedido** agora é reconhecido como diálogo modal com rótulo acessível, foco no campo Cliente ao abrir, `Tab`/ `Shift+Tab` restritos aos seus controles, encerramento por `Escape` e restauração do foco ao botão de origem quando possível. O menu indica `aria-current="page"` para a seção ativa e mostra orientação de rolagem horizontal em telas estreitas. Botões primários/secundários e itens de navegação usam altura mínima de 44 px no mobile.

O teste `web/tests/pilot-accessibility.e2e.spec.ts` verifica teclado, foco, `Escape`, identificação semântica, estado do menu e altura dos controles a 390 px, sempre com lojista QA no PostgreSQL descartável.

**Limites:** isto não constitui auditoria WCAG completa, avaliação de leitores de tela, contraste colorimétrico nem aceitação humana. Navegação por teclado e touch targets não substituem testes em dispositivos físicos.

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
