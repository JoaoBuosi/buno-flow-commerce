# Buno Flow Commerce 0.5.1 — CASE26 · Runbook de demonstração
**Evento:** CASE26, 26 e 27/11/2026 · **Data de preparação:** 11/11/2026

**Status:** roteiro, gerador de dados e testes automatizados preparados; homologação E2E remota sob Vercel Authentication depende de uma sessão autorizada. Não confundir CI local verde com deploy remoto validado.

## Escopo permitido
Mostrar somente funcionalidades realmente existentes: Dashboard, cadastro de pedidos, mudança de status, histórico por pedido, importação CSV, exportação e configurações da loja. A estimativa de demanda é experimental: somente indicadores suficientes do próprio lojista podem gerar a média móvel; modelo de ML avançado está reprovado/desativado. **Não demonstrar como pronto** marketplace, WhatsApp, TikTok Shop, chatbot Aegis 2 ou emissão de NFS-e. Não ativar pagamentos.

## Preparação — na véspera e antes de cada apresentação
1. Fazer login como um usuário de demonstração autorizado em ambiente privado protegido (não criar conta em público no evento). Verificar que a loja é somente de **dados fictícios**, sem pedidos reais. Não gravar ou compartilhar código de convite, senhas ou token.
2. Confirmar no navegador que Dashboard e página de Pedidos carregam, sem mensagens 403/404/502. Vercel SSO, gateway, Railway e Supabase devem funcionar juntos. Concluir **um** pedido sintético de teste e verificar que permanece após atualizar a página.
3. Gerar novo CSV sintético no computador (Windows PowerShell no diretório `02-buno-flow-commerce`):
   ```powershell
   py -3 scripts/make_case26_csv.py --day 26 --output-dir .\case26-output
   py -3 scripts/make_case26_csv.py --day 27 --output-dir .\case26-output
   ```
   Repetir o comando para cada nova apresentação, pois cada execução gera um identificador aleatório. Nunca reutilizar arquivos já importados na mesma loja. Nenhum CSV possui pessoas reais; os nomes são somente exemplos.
4. Deixar uma aba já autenticada em **Visão geral**, sem o modal aberto. Fechar notificações antigas, ocultar qualquer informação sensível e aumentar zoom para leitura.
5. Testar em tela móvel e notebook, preferindo notebook e internet estável. Evitar depender de recuperação de senha (SMTP ainda não homologado).
6. Preparar uma **gravação local de até 120 segundos feita com o aplicativo real** e salvar no computador antes de sair; nenhuma gravação está criada por este repositório. O vídeo é a alternativa se internet/SSO indisponível.

## Roteiro de 120 segundos (não exceder)
| Cronômetro | Mostrar | Narrar |
|---|---|---|
| 00:00–00:15 | Logo + Visão geral | “Lojistas ainda acompanham pedidos em planilhas. O Flow Commerce centraliza as etapas.” |
| 00:15–00:40 | “+ Novo pedido”, cliente fictício, SKU, quantidade e preço | “Cadastramos um pedido e o sistema calcula o valor.” |
| 00:40–01:00 | Pedidos → selecionar → “Avançar: Pago” | “Atualizamos o andamento sem perder o histórico.” |
| 01:00–01:30 | Arquivos e CSV → importar arquivo único do dia | “Podemos trazer registros de planilhas para a loja.” |
| 01:30–01:50 | Histórico por pedido + Exportar CSV | “Cada alteração é auditada, e os dados podem ser exportados.” |
| 01:50–02:00 | Voltar ao painel | “Marketplaces, WhatsApp e IA avançada estão no roadmap, ainda não são integrações disponíveis.” |

**Evitar no palco:** cadastro de nova loja, digitação demorada, chamadas a serviços terceiros, análise de IA sem histórico, download de plugins, ajustes de variáveis e painel do banco.

## Homologação do staging com navegador autorizado
A suíte automatizada de CI usa PostgreSQL efêmero e não prova o fluxo remoto. Para testar a Vercel protegida, entrar com a conta autorizada e repetir na mesma loja sintética:
- [ ] Login e Dashboard por HTTPS, sem expor senhas, URLs JDBC ou tokens.
- [ ] Pedido criado (HTTP 201), total correto e persistência após recarregar.
- [ ] Alteração de status, histórico exibindo evento e cancelamento opcional.
- [ ] Importação do CSV único; quantidade exata e nenhum dado duplicado.
- [ ] Exportação dos pedidos da loja; conferir 2–3 linhas localmente.
- [ ] Acesso de outra loja não consegue ler pedido/contagem da loja de origem (executar somente com duas contas sintéticas aprovadas, sem usar registros de usuários existentes).
- [ ] Voltar ao login e confirmar relogin; API permanece protegida sem sessão.
- [ ] Repetir 3 ensaios cronometrados sem intervenção técnica, todos abaixo de 2 minutos.
- [ ] Gravação de fallback testada **offline**, sem dados sensíveis.
- [ ] Responsável aprova a versão final para uso no CASE26.

## Contingência offline Windows
Somente com Docker, Java build, Node e dependências pré-instaladas. O banco local é separado do Supabase.
```powershell
Set-Location "CAMINHO_LOCAL\buno-flow-commerce\02-buno-flow-commerce"
$env:DB_PASSWORD = Read-Host "Senha local do PostgreSQL"
docker compose up -d --build
Invoke-RestMethod "http://localhost:8102/actuator/health"
Set-Location ".\web"
npm install
$env:VITE_API_URL = "http://localhost:8102/api/v1"
npm run dev -- --host 127.0.0.1
```
Criar conta local antes do evento, popular com dados fictícios e testar sem internet. **Não usar credenciais do Supabase ou Railway neste cenário**. Para fallback imediato e mais robusto, utilizar o vídeo gravado antes do evento.

## Pequenos patches aplicados
- `scripts/make_case26_csv.py`: gerador local de CSV fictício com chave única.
- `scripts/test_case26_csv.py`: testes offline para esquema, duplicatas e sobrescrita.
- `web/tests/case26.e2e.spec.ts`: percorre o demo flow via React + Spring Boot + PostgreSQL de CI.
- `web/src/main.tsx`: avisos do piloto e experiência de importação devem permanecer honestos.
- Workflows do GitHub Actions para rodar a suíte nesta branch de demonstração.

## Congelamento, rollback e não objetivos
- Preparar PR de rascunho com a branch `feat/flow-commerce-0.5.1-case26` baseada na 0.5; **não** mesclar automaticamente em `main`.
- Registrar SHA e URL da prévia que passou no ensaio. Não mudar backend, Flyway, schema, segredos, Railway, Supabase ou URL pública para apresentar o CASE26.
- Se falhar: reabrir o deploy `READY` anterior do projeto Vercel (verificar autenticação `all`) ou usar vídeo; as mudanças desta fase são de frontend, testes e documentação. Não fazer rollback destrutivo de banco.
- O staging exige Vercel Authentication. Liberar acesso ao público implicaria revisão de proteção, rate limit, termos e autorização expressa do proprietário — fora de escopo.
