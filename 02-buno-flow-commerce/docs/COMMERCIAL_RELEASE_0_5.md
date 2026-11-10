# Flow Commerce 0.5 — liberação comercial controlada

**Estado:** DESENVOLVIMENTO / STAGING. Não aceitar pedidos reais de novos lojistas, não ativar billing nem publicar landing page ao público antes do go/no-go aprovado.

## Escopo desta entrega
- Dashboard próprio com números reais da API e onboarding do primeiro pedido.
- Página de importação e exportação com contrato CSV explícito e modelo contendo somente dados fictícios.
- Landing page de apresentação em `web/public/comercial/index.html`, com CTA ao Instagram oficial Buno. Página protegida pelo ambiente atual; **não é marketing público nem capta leads diretamente**.
- Suíte Playwright expandida para validar navegação e arquivo CSV.
- Identidade visual oficial Flow Commerce herdada da branch `feat/flow-commerce-official-branding`.
- Sem novas migrations, sem alterações de banco, sem pagamentos ou novas integrações.

## Estado da infraestrutura observado
- Railway (API Java) e Vercel (SPA protegida) foram implantados na etapa 0.4; segredos de gateway permanecem fora do frontend.
- Supabase PostgreSQL exclusivo `buno-flow-commerce`, schema privado `flow_commerce`.
- Consulta agregada em 10/11/2026: 2 lojas, 2 usuários, 1 pedido e 5 eventos de auditoria. **Não confundir isso com E2E remoto completo ou aprovação de segurança**.
- A existência de dados de teste não autoriza manipular, apagar ou migrar registros.

## BLOQUEIOS OBRIGATÓRIOS — antes de operar com dados de clientes
- [ ] E2E autenticado na infraestrutura real: Vercel SSO → gateway → Railway → Supabase. Testar login, criação/edição de pedido, importação, exportação e isolamento de duas lojas, com contas e dados fictícios autorizados.
- [ ] Executar backup e **restauração** de cópia do banco em destino isolado, conferindo contagens e integridade; registrar evidência e RPO/RTO. Não restaurar sobre o banco atual.
- [ ] SMTP seguro, verificação de endereço e recuperação de senha: teste de token único, expiração, anti-enumeração e sessões revogadas. A existência de código não significa operação pronta.
- [ ] Monitoramento e alertas: health API, disponibilidade do gateway, falhas de login, erros 5xx, conexões ao banco e aviso ao responsável. Não logar CPF, senhas, tokens ou dados de pedidos.
- [ ] Revisão de autenticação e autorização tenant (IDOR/BOLA), armazenamento de sessões, gateway/ingress, antiabuso distribuído, limites CSV e patches de dependências.
- [ ] Termos de uso, política de privacidade, contrato/termo de tratamento de dados (DPA), retenção, exclusão/portabilidade, suporte e política de incidentes revisados e aprovados; comprovar formalização legal da empresa.
- [ ] Definir preços, escopo de planos, SLA, limites, cancelamento/reembolso e faturamento. Cobrança de teste somente após revisão, sem armazenar cartão no Flow Commerce.
- [ ] Validar com contador qual regime tributário e a emissão de NFS-e aplicável à empresa. Não prometer que o aplicativo emite notas fiscais.
- [ ] Aceite do proprietário registrado para convite de lojistas reais e liberação comercial.

## Quem faz o quê
- Engenharia Buno: infraestrutura, backup, segurança, e-mail, testes, UX e dashboard.
- Fundadores/comercial Buno: entrevistas, demonstrações, critérios de preço, atendimento, contratos e aprovações.
- Contador/consultoria jurídica: enquadramento, notas, termos e responsabilidades de dados.

## Fontes oficiais
- ANPD guia e checklist para pequenos agentes: https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes/guia-orientativo-sobre-seguranca-da-informacao-para-agentes-de-tratamento-de-pequeno-porte
- ANPD direitos dos titulares: https://www.gov.br/anpd/pt-br/assuntos/titular-de-dados-1/direito-dos-titulares
- Ministério da Fazenda, NFS-e para Simples Nacional (2026): https://www.gov.br/fazenda/pt-br/assuntos/noticias/2026/abril/nota-fiscal-de-servico-eletronica-de-padrao-nacional-sera-obrigatoria-para-optantes-do-simples-nacional
