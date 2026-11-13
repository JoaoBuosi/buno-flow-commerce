# Flow Commerce 0.5.11 — Aceitação humana do piloto privado

**Status: PENDENTE — requer uma pessoa testando a interface.** Este documento prepara a revisão; não equivale a aprovação, disponibilidade pública ou homologação operacional.

**Referência:** PR #18, empilhado sobre PR #17; último commit do CI aprovado antes deste documento: `cb46387b554b42be227381f20a7966dd7be3476e` (6/6 workflows concluídos com sucesso). O CI do próprio commit deste arquivo precisa ser conferido separadamente.

## Fase A — Revisão visual sem deploy (já possível)

Acessar o workflow de navegador do commit aprovado:
`https://github.com/JoaoBuosi/buno-flow-commerce/actions/runs/43353953446`
→ **Artifacts** → `flow-commerce-0511-synthetic-visual-review`. O pacote contém seis PNGs de QA descartável: visão geral, pedidos e configurações em 1440×900 e 390×844; a retenção de artefatos é curta (3 dias).

Avaliador deve abrir as capturas no celular **e** em tela maior e preencher:

- [ ] V1 — Conteúdo e títulos são legíveis sem ampliar.
- [ ] V2 — Botões do painel e navegação são facilmente distinguíveis.
- [ ] V3 — No mobile, a dica de rolagem do menu é clara; não há cortes de conteúdo.
- [ ] V4 — O status **Não liberado comercialmente** é visível e compreensível.
- [ ] V5 — Campos e mensagens de segurança são claros; não expõem senhas nem tokens.
- [ ] V6 — Contraste e hierarquia visual parecem adequados (observação humana inicial; não é auditoria WCAG).

Registrar resultados por ID como `APROVADO`, `FALHOU` ou `NÃO TESTADO`, com breve explicação. Não anexar credenciais, dados reais, capturas contendo tokens/cookies ou arquivos de banco.

## Fase B — Teste funcional em celular físico (BLOQUEADO até haver ambiente autorizado)

**Pré-requisitos:** uma URL HTTPS privada **da revisão exata** com controle de acesso e backend isolado; conta QA autorizada e dados totalmente sintéticos; aprovação explícita para criar ou usar o preview. A existência da Vercel ou aprovação do CI **não** autorizam automaticamente o deploy, o acesso à produção ou o uso de clientes reais. Não substituir por um ambiente de staging que contenha dados sensíveis.

Checklist prático após os pré-requisitos serem atendidos:

- [ ] F1 — Entrar na conta QA, confirmar loja e tela inicial; não usar cadastro de lojista real.
- [ ] F2 — Abrir **Pedidos**, retornar ao painel e abrir **Configurações** no celular.
- [ ] F3 — Abrir **Novo pedido**, conferir que o campo Cliente recebe foco e que o diálogo não fica cortado.
- [ ] F4 — Criar pedido fictício; verificar apenas dentro da conta QA, com aprovação para escrita no banco descartável.
- [ ] F5 — Atualizar status; conferir histórico e exportação CSV com dados fictícios.
- [ ] F6 — Importar arquivo CSV **sintético** válido; conferir resultado e erro amigável em arquivo inválido.
- [ ] F7 — Navegar por teclado no desktop: `Tab`, `Shift+Tab`, `Escape` no diálogo e retorno do foco ao botão de origem.
- [ ] F8 — Simular indisponibilidade **somente no QA**: erro deve ser visível e não afirmar que o piloto está liberado.
- [ ] F9 — Verificar isolamento com duas contas QA independentes, nunca usando identificadores de outra loja real.
- [ ] F10 — Confirmar tamanho de toque, rolagem, legibilidade e ausência de rolagem horizontal no navegador mobile.

## Regra de aceitação

**Aceitação visual condicional:** V1–V6 aprovados pelo avaliador. Isso **não** autoriza o piloto comercial.

**Aceitação funcional condicional:** F1–F10 aprovados em ambiente isolado e dispositivo físico, com falhas críticas resolvidas.

**Pronto para piloto real:** somente após aceite visual/funcional, restauração **real** de backup no PostgreSQL autorizado, recuperação de acesso por canal verificado ou alternativa formalmente aprovada, chave MFA protegida com migração/rotação supervisionada, entrega de alerta testada, revisão independente de segurança e decisão comercial humana explícita.

## Registro do avaliador (não preencher automaticamente)

- **Dispositivo / navegador / tamanho aproximado da tela:** pendente
- **Referência de commit / preview:** pendente
- **Data da revisão:** pendente
- **Resultados V1–V6:** pendentes
- **Resultados F1–F10:** pendentes
- **Defeitos encontrados e severidade:** pendentes
- **Decisão:** NÃO APROVADO (revisão humana pendente)
- **Avaliador responsável:** pendente

## Restrições

Nenhum merge, deploy, cobrança, SMTP externo, Supabase real, alteração de chave MFA ou mudança de permissões integra esta etapa. A implementação de funcionalidades futuras como estoque não é parte da aceitação da 0.5.11.
