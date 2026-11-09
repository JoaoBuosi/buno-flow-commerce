# Buno Flow Commerce — Laboratório ML (pesquisa, não produção)

Objetivo: estudar previsão de demanda semanal por SKU usando transações históricas reais com licença compatível, **sem introduzir transações antigas como pedidos de lojistas**.

**Dataset canônico:** Chen, D. (2015). *Online Retail*. UCI Machine Learning Repository. DOI https://doi.org/10.24432/C5BW33. Licença CC BY 4.0 (atribuição obrigatória).
Página de origem: https://archive.ics.uci.edu/dataset/352/online-retail
Versão semelhante no Kaggle: https://www.kaggle.com/sanjeet41/online-retail

**Por que não usamos Olist como base comercial:** https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce é CC BY-NC-SA 4.0; bloqueado para treinamento destinado ao produto comercial sem permissão expressa.

## Processo reproduzível

1. Baixar o ZIP original da UCI de https://archive.ics.uci.edu/static/public/352/online+retail.zip e extrair `Online Retail.xlsx` no diretório local ignorado `ml/data/`.
2. `python -m pip install -r ml/requirements.txt`
3. `python ml/train_demand.py --input ml/data/Online Retail.xlsx --out ml/artifacts`
4. `python -m unittest discover -s ml -p "test_*.py"`

O workflow `.github/workflows/flow-ml.yml` baixa o arquivo direto da origem e executa o processo. Dados brutos e identificadores de clientes **não são versionados**. Os artefatos gerados são apenas coeficientes e métricas agregadas, guardados no GitHub Actions.

## Modelo e critérios

- Previsão de demanda **da semana** de cada SKU, com lags 1 e 2, média e desvio padrão das 4 semanas passadas e sazonalidade semanal.
- `Ridge` sobre `log1p(unidades)`. Divisão treino/validação por tempo; sem dados futuros nas features.
- Baseline: média das 4 semanas. O modelo só é **candidato ao piloto interno** se melhorar MAE em pelo menos 5% e tiver WAPE menor que 100%.
- Isso **não equivale a validação para o Brasil nem é garantia de demanda ou receita**. Requer dados consentidos de lojas piloto, validação temporal em cada loja, monitoramento de drift e revisão de performance antes de habilitar por padrão.
- Dataset antigo: varejo do Reino Unido em 2010–2011. Não é adequado para inferir atrasos de entrega ou fraude.
- O processo não exporta dados de clientes, valores de pedidos, nem linhas do dataset.

Não confundir MVP operacional (Java/Spring Boot) com laboratório ML opcional.
