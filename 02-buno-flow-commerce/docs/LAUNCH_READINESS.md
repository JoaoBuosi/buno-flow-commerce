# Flow Commerce — Go/No-Go para MVP comercial (09/11/2026)

Estado: **NÃO LIBERAR AO PÚBLICO OU VENDER COMO SERVIÇO PRONTO AINDA**.

## Escopo de um MVP vendável
Pequeno lojista no Brasil: criar conta e loja; registrar pedidos com itens e cálculo do total; importar/exportar CSV; consultar, pesquisar, atualizar status e cancelar com justificativa; histórico de auditoria e separação de lojas. Painel mobile-first. Recomendação determinística de risco opcional, previsão de demanda só depois de validação local.

Não bloquear o lançamento esperando WhatsApp, marketplace, agentes LLM, conciliação de pagamentos, ERP ou modelo ML que não passou no teste. Essas são integrações futuras, não pré-requisito do produto mínimo **desde que não sejam prometidas como disponíveis**.

## Implementado e com evidência
- Java 21 / Spring Boot 3.4.5, PostgreSQL + Flyway. `OrderStore` multiempresa com cálculos em BigDecimal e histórico.
- Sessões aleatórias com token armazenado como hash, troca de senha com revogação, limitação básica de login (por instância), cabeçalhos de segurança, dono da loja.
- CSV com transação atômica, proteção de duplicatas por chave externa por loja; exportação autenticada por loja e neutralização de fórmulas.
- Frontend React/TypeScript que consome a API; comportamento de escassez de dados para insights.
- GitHub CI: 7 testes integrados no PostgreSQL, validação de pedido após reinício Spring Boot, dump/restore PostgreSQL em banco separado e build frontend. Evidência: https://github.com/JoaoBuosi/buno-flow-commerce/actions/runs/42624970498
- Dataset real de pesquisa: UCI Online Retail, CC BY 4.0, 541.909 linhas históricas. **Não** tratado como pedidos reais atuais de qualquer loja.

## Bloqueios P0 antes de cobrar e aceitar dados pessoais reais
- [ ] Hospedar Java em plataforma compatível, URL HTTPS, health endpoint, CORS restrito ao domínio da interface.
- [ ] Provisionar banco PostgreSQL dedicado com credenciais e backups automáticos fora do mesmo servidor; **validar restauração na hospedagem real**.
- [ ] Configurar e testar secrets, migrations, alterações de schema entre versões, rollback e monitoramento com alertas.
- [ ] Implementar recuperação de senha e verificação de e-mail via provedor autorizado; proteção contra abuso centralizada (Redis/API gateway/WAF) para mais de uma instância.
- [ ] Fazer revisão de segurança por pessoa independente: autenticação, BOLA/IDOR, CSV, SQL, CORS, limites, HTTPS, logs/PII, dependências.
- [ ] Implementar teste navegador E2E em staging com backend implantado: cadastro, login, loja A x loja B, pedido, CSV, cancelamento, exportação, sessão expirada e teste mobile.
- [ ] Página pública distinta para o produto, termos e política de privacidade, DPA para clientes, canal LGPD, retenção e exclusão/portabilidade aplicáveis.
- [ ] Formalização para faturamento e emissão de nota, contratos, suporte, cancelamento, cobrança e reembolso.
- [ ] Definir cobrança comercial: plano, preço, limites, trial, integração de pagamento ou faturamento manual supervisionado. Nenhum processamento de cartão dentro da Buno.
- [ ] Criar ambiente de staging restrito; fazer aceite com ao menos uma loja piloto real, com consentimento/contrato e segregação comprovada.

## Bloqueios de produto P1 para ampliar escala
- [ ] Equipe/convites e perfis de acesso testados; autenticação mais forte quando aplicável.
- [ ] Importador com preview, mapeamento de colunas, relatório de erros por linha e UX acessível.
- [ ] Paginação e contagens completas além dos primeiros 100 pedidos (frontend atualmente limitado a 100).
- [ ] Idempotência também na criação manual de pedidos e tratamento de concorrência ampliado.
- [ ] Processamento assíncrono confiável para integrações com marketplaces, fila/outbox, retries e observabilidade.
- [ ] Portal de billing e webhook idempotente com assinatura válida; configurações de plano.
- [ ] Painel de métricas confiáveis (pedidos pagos, devoluções, data de pagamento, receita reconhecida definida); não chamar valor de pedido de faturamento.
- [ ] Testes de carga, segurança, acessibilidade WCAG, dispositivos móveis, restore e rollback.
- [ ] Política formal de dataset e model governance: licença, histórico, calibração, drift e métricas por cliente.

## Política de dados
- **Dado real de pesquisa:** UCI, 2010–2011, apenas laboratório, com atribuição. Nunca usar clientes, pedidos ou valores da pesquisa para compor indicadores de lojas reais.
- **Dado real operacional:** importado por lojista autenticado e autorizado ou pela futura integração com marketplace; protegido por loja.
- **Modelo ML:** estudo de regressão com split temporal. Resultado inicial e segunda tentativa abaixo do baseline, portanto `eligible_for_internal_pilot=false`. Não habilitar modelo, manter heurística ou estado de dados insuficientes.

## Go/No-Go
Somente aprovar publicação comercial após todos os bloqueios P0 com evidências anexadas e 1 piloto end-to-end. Aplicação de pesquisa/demo pode continuar protegida, mas não deve passar a pública automaticamente.

Fontes:
- https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes/guia-orientativo-sobre-seguranca-da-informacao-para-agentes-de-tratamento-de-pequeno-porte
- https://www.gov.br/anpd/pt-br/assuntos/titular-de-dados/direito-dos-titulares
- https://www.gov.br/pt-br/servicos/emitir-nota-fiscal-de-servico-eletronica
