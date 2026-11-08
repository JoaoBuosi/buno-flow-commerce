# Buno Flow Commerce — Escopo do MVP

Versão: 0.1  
Público inicial: pequenos e médios e-commerces  
Prioridade: gestão e acompanhamento de pedidos

## 1. Problema
Pequenos e médios e-commerces acompanham pedidos em ferramentas diferentes, têm pouca visibilidade sobre pedidos parados ou com problemas e gastam tempo respondendo manualmente perguntas como "meu pedido foi aprovado?", "já foi enviado?" e "qual é a previsão?".

## 2. Proposta de valor
Centralizar o ciclo operacional dos pedidos em uma API e painel simples, com histórico rastreável e alertas para pedidos que exigem atenção. A IA será uma camada assistiva: poderá consultar dados reais e resumir situações, mas não será a fonte oficial de status, valores ou pagamentos.

## 3. Usuário inicial
Operadores e proprietários de pequenos e médios e-commerces que precisam acompanhar pedidos diariamente, mesmo que usem inicialmente uma integração simples ou cadastro manual.

## 4. Escopo funcional do MVP

### P0 — obrigatório
- Criar pedido com identificador externo opcional, cliente, valor, moeda e itens.
- Consultar pedido por ID e listar pedidos com paginação.
- Filtrar por status, período e identificador do cliente.
- Atualizar status por transições permitidas.
- Registrar histórico imutável de mudanças de status (data, status anterior, novo status e origem).
- Expor health check e documentação OpenAPI.
- Validar entradas e retornar erros HTTP consistentes.
- Persistir em PostgreSQL.

### P1 — após o fluxo principal estar estável
- Busca por número/identificador externo.
- Indicadores simples: pedidos por status e pedidos atualizados recentemente.
- Webhook de entrada com autenticação por segredo e proteção contra duplicidade.
- Notificações de pedidos parados ou com falha.

### Fora do MVP
- Processamento ou captura de pagamentos.
- Gestão física de estoque.
- Cotação/compra de frete e rastreamento direto com transportadoras.
- Marketplace multi-canal complexo.
- Emissão fiscal.
- Agente autônomo que altera pedidos sem aprovação.
- Cobrança, planos e multi-tenancy comercial completo.

## 5. Ciclo inicial de status
RECEIVED -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED

Estados excepcionais:
- CANCELLED: pode ocorrer antes de SHIPPED.
- ON_HOLD: pausa operacional; exige motivo.
- PAYMENT_PENDING: estado inicial opcional quando a origem informar pagamento pendente.

As transições serão validadas no backend. Não permitir retrocessos ou saltos arbitrários sem regra explícita. A primeira implementação deve manter as regras simples e testáveis.

## 6. Modelo de dados inicial

### Order
- id: UUID interno.
- externalOrderId: identificador do pedido no sistema de origem, opcional.
- customerId: identificador do cliente no sistema de origem.
- customerName: nome para exibição, opcional.
- customerEmail: e-mail para exibição, opcional.
- totalAmount: decimal com precisão monetária.
- currency: código ISO 4217, inicialmente BRL por padrão.
- status: enum de status.
- source: origem do pedido (MANUAL, API ou integração identificada).
- createdAt, updatedAt.

### OrderItem
- id, orderId, sku, name, quantity, unitPrice.

### OrderStatusHistory
- id, orderId, fromStatus, toStatus, reason, source, createdAt.

## 7. API proposta
Prefixo: /api/v1

- POST /orders — criar pedido.
- GET /orders — listar com paginação e filtros.
- GET /orders/{id} — detalhe do pedido.
- PATCH /orders/{id}/status — transicionar status.
- GET /orders/{id}/history — histórico de status.
- GET /dashboard/summary — contagens básicas por status (P1).

Respostas não devem expor entidades JPA diretamente. Usar DTOs. Valores monetários devem trafegar como decimal, nunca ponto flutuante binário.

## 8. Critérios de aceite do MVP
1. Um pedido válido pode ser criado e recuperado após reiniciar a aplicação.
2. Dados inválidos retornam 400 com mensagem estruturada.
3. Um pedido inexistente retorna 404.
4. Uma transição válida atualiza o pedido e cria exatamente um registro de histórico.
5. Uma transição inválida retorna 409 e não altera pedido nem histórico.
6. Listagem suporta paginação e filtros sem carregar toda a tabela.
7. A aplicação sobe com Docker Compose e health checks ficam disponíveis.
8. Testes automatizados cobrem regras de status, validação e endpoints principais.

## 9. Princípios de produto
- Resolver primeiro o acompanhamento confiável de pedidos; não tentar ser um ERP.
- Integrações e IA entram depois que o núcleo transacional estiver correto.
- O sistema deve explicar o que sabe com base em dados persistidos.
- Ações sensíveis devem ter autorização, trilha de auditoria e, quando necessário, aprovação humana.
