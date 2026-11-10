# Flow Commerce — checklist para política de privacidade, termos e DPA
**RASCUNHO INTERNO — NÃO PUBLICAR COMO POLÍTICA VIGENTE / NÃO ASSINAR SEM REVISÃO LEGAL.**

Esta estrutura serve para coletar decisões concretas; não constitui orientação jurídica final. A Buno ainda precisa confirmar dados jurídicos, responsável pelo canal LGPD e subprocessadores.

## Política de privacidade — pontos a preencher
1. Identificação do fornecedor: [RAZÃO SOCIAL], [CNPJ], [ENDEREÇO COMERCIAL], [CANAL DE CONTATO PRIVACIDADE].
2. Categorias efetivamente tratadas: contas de lojistas (nome/e-mail/sessões), clientes informados pelo lojista (nome, pedidos e itens), registros de importação/exportação, eventos de auditoria e logs operacionais necessários.
3. Finalidades: execução do serviço SaaS, segurança/autenticação, suporte, faturamento quando habilitado e atendimento a solicitações legais.
4. Base legal por fluxo: **a identificar**, não presumir que todos exigem consentimento. Diferenciar dados da conta Buno e dados que a loja controla sobre seus consumidores.
5. Hospedagem e transferência internacional: Supabase dedicado na região informada `us-east-1` e Railway/Vercel conforme configuração real. Validar regiões, fornecedores, mecanismos contratuais e transferências.
6. Retenção, backups e descarte: definir prazos de usuários, pedidos, auditoria, backups, tickets, logs e contas canceladas, com exceções legais documentadas.
7. Direitos dos titulares: canal para acesso, correção, exclusão e outros direitos; procedimento para pedidos feitos por consumidores finais, que podem exigir encaminhamento à loja controladora.
8. Medidas de segurança: segregação tenant, autenticação, TLS, monitoração e resposta a incidente. Descrever somente controles comprovados.
9. Contatos para incidentes e solicitações, prazo de resposta, data de versão e histórico de alterações.

## Termos comerciais — pontos a preencher
- Descrição precisa dos recursos disponíveis e limitações; não prometer integração automática WhatsApp/marketplaces, emissão fiscal ou ML avançado.
- Política de elegibilidade, cadastro, permissões, uso aceitável, horários e canal de suporte.
- Serviços, preços, reajustes, trial, renovação, cancelamento e reembolso — a aprovar antes de qualquer cobrança.
- Propriedade dos dados, exportação, eliminação e consequências de encerramento da conta.
- Disponibilidade/SLA **somente se medidos e formalmente aprovados**.
- Responsabilidades de consumidor/lojista e fornecedor, tratamento de incidentes, legislação e foro após revisão.

## DPA entre a Buno e a loja
- Identificar por operação quem decide finalidades/meios (controlador) e quem trata por instruções (operador); não fixar o papel de forma genérica.
- Definir instruções documentadas, subprocessadores, locais de hospedagem, confidencialidade, medidas de segurança, auditoria, comunicação de incidente, apoio a direitos e descarte/devolução.
- Registrar transferência de dados e salvaguardas aplicáveis.

## Referências
https://www.gov.br/anpd/pt-br/assuntos/titular-de-dados-1/direito-dos-titulares
https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes/guia-orientativo-para-definicoes-dos-agentes-de-tratamento-de-dados-pessoais-e-do-encarregado
