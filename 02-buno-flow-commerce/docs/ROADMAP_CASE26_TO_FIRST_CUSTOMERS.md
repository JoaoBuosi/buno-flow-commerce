# Flow Commerce — do CASE26 aos primeiros clientes (0.5.2 → 0.6)
**Documento de execução | 11/11/2026 | uso interno Buno**

## Mapa de evolução

```text
0.5.1 — Demo técnica com CI verde
            |
            v
0.5.2 — Ensaio guiado + CSV fictício no navegador + smoke SSO
            |
         [Gate A]
       3 ensaios remotos com navegador autorizado + vídeo offline
            |
            v
CASE26 — 26 e 27 de novembro: demonstração privada/controle de acesso
            |
         [Gate B]
  backup/restore real, suporte, identidade, incidentes, LGPD e fiscal
            |
            v
0.6 — Piloto de até 3 lojistas; medir tempo e erros no fluxo
            |
         [Gate C]
  provas de uso, custos de suporte e preço aceito + autorização
            |
            v
Oferta paga — contratação e cobrança adequadas, sem prometer integrações
```

## Entregas desta versão 0.5.2
- **Interface de ensaio privado:** habilite `?case26=1` depois de autenticar-se; usa a autenticação atual e continua exigindo autorizações backend.
- **Verificação real e somente leitura:** botão consulta `GET /store` e `GET /dashboard`; sucesso confirma leitura da loja autenticada, NÃO comprova backup nem escrita/isolamento.
- **CSV fictício gerado na hora:** botões para dias 26 e 27 produzem arquivos únicos no navegador (sem mandar dados ao servidor até que usuário importe explicitamente).
- **Smoke de produção controlada:** script Playwright abre Vercel com SSO normal; o operador entra manualmente e o script verifica respostas 200 de loja e dashboard. Nenhum cookie ou token gravado no disco. Não cria pedidos.
- **E2E local de CI:** testa os botões de ensaio, download, importação dos CSVs gerados, pedido, auditoria, exportação e isolamento; banco PostgreSQL temporário.
- Sem alterações na API, Supabase, Railway, domínio, gateway, cobrança ou planos.

## Gate A — demo confiável antes de 26/11
| Item | Critério de aceite | Evidência | Estado inicial |
|---|---|---|---|
| Código e fluxo | Java, frontend, Python e Playwright verde | URL CI e commit SHA | Validar no PR 0.5.2 |
| Staging Vercel | build READY e SSO `all`, favicon/CSV HTTP 200 | URL de deploy | Validar após build |
| API e loja autorizadas | Vercel SSO → Spring no Railway → Supabase; GET store/dashboard 200 | resultado do script + observação em navegador | PENDENTE |
| Operações reais | criar, atualizar, importar, exportar, auditoria e relogin com conta demo; HTTP e persistência | checklist manual, nunca tokens | PENDENTE |
| Sem misturar lojas | conta demo B não acessa pedido demo A | resultado de teste de autorização autorizado | PENDENTE |
| Repetibilidade | 3 ensaios de ≤120 s com CSV recém gerado | tempos e operador | PENDENTE |
| Fallback | vídeo local de até 120 s reproduzido sem conexão | arquivo verificado no computador do apresentador | PENDENTE |

### Smoke remoto de leitura sem armazenamento de login
Na pasta `02-buno-flow-commerce/web` com Node.js 22:
```powershell
npm install
npx playwright install chromium
node scripts/check-case26-remote.mjs "https://buno-flow-commerce-staging-<ID>-<team>.vercel.app"
```
A ferramenta abrirá uma janela normal do navegador. Autentique-se na Vercel e no Flow Commerce com uma loja de ensaio. Ela apenas chama a verificação de leitura. Não armazena cookies/senhas; use URL do deploy privado verificado no PR (substitua o exemplo). **Nunca usar ferramenta de replay que desative o SSO.**

### Ensaiar a demonstração
1. Acesse a URL verificada do staging com `?case26=1`. Inicie sessão em loja 100% fictícia.
2. Clique em **Verificar API e loja**. Somente o texto "Conta e indicadores acessíveis" conta como verificação de leitura.
3. Clique em **Novo pedido** e crie pedido "Cliente Demonstração", 2 × R$39,90.
4. Abra o pedido em Pedidos; mude status para Pago, depois veja Histórico.
5. No guia de ensaio, gere um CSV para o dia. Na página Arquivos e CSV, importe o arquivo gerado; confirme quantidade retornada e confira no Dashboard.
6. Exporte CSV, confira cabeçalho e registros. Use um CSV **novo** em cada ensaio; não limpar dados de outras contas.
7. Grave a apresentação real e confira arquivo de fallback offline. Cronometre três tentativas.

## Gate B — preparação legal e operacional para piloto de verdade
**Não confundir backup/restauração CI do banco temporário com recuperação comprovada do Supabase.**
- [ ] Exportar backup do PostgreSQL dedicado usando acesso aprovado via console/segredos; armazenar cifrado com política de retenção e executar `pg_restore` somente em ambiente isolado e descartável, validando tabelas e chaves de referência. **NUNCA restaurar sobre o banco em uso.**
- [ ] Confirmar plano do provedor de e-mail, sender/domínio e testar verificação de cadastro e recuperação de senha; nenhum segredo em frontend.
- [ ] Alertas de 5xx, disponibilidade do gateway, indisponibilidade da API, banco, e indicadores de rate-limit; definir destinatário e procedimento de incidente.
- [ ] Revisar regras de isolamento `store_id`, autorização no backend, retenção de logs, requisições não autenticadas, headers e limites CSV.
- [ ] Rever LGPD (política, DPA, retenção, suporte, direitos dos titulares e subprocessadores internacionais) com responsável, sem publicar rascunhos como documentos finais.
- [ ] Confirmar CNPJ/regime de tributação, faturamento NFS-e e condições de cobrança/assinatura. Não cobrar antes de estar regular.
- [ ] Testar uso mobile com 2 ou 3 lojistas demonstrativos e ter canal de suporte.
- [ ] Registro expresso da autorização dos responsáveis pela Buno para aceitar até 3 pilotos reais.

## Gate C — aprendizado e vendas
Métricas mínimas por loja, com permissão: tempo até primeiro pedido, quantidade de pedidos semanais, falhas de importação, esforço de suporte, renovação e disposição a pagar. Não incluir pedidos pessoais de clientes em relatórios públicos.
- Rodada de descoberta: 10 conversas com lojistas potenciais, 5 demos, até 3 pilotos depois do Gate B.
- Testar hipótese de plano fundador R$99 por 3 meses vs referência Starter R$149/mês, **sem anunciar preço final antes de custos e escopo aprovados**.
- Não priorizar Mercado Livre/Shopee/WhatsApp/Aegis 2 antes de validar uso recorrente do núcleo de pedidos.

## Operações e rollback
- Branch 0.5.2 é separada; PR deve permanecer draft até a revisão dos testes.
- Deploy Vercel apenas em projeto com SSO ALL. Preservar o deployment READY anterior para rollback; não promover a produção nem atribuir domínio público.
- Caso haja falha do gateway, mostre o vídeo de contingência. Nunca remover autenticação para "corrigir" uma demo.
- Não executar migrations ou `pg_restore` no banco atual, não alterar Railway sem um teste específico de integração necessário.
