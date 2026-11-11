# Flow Commerce: verificar status de dominio Resend usando API oficial, sem mutacoes.
# API key solicitada em modo protegido e mantida somente em memoria.
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Domain)
$ErrorActionPreference = "Stop"
$Domain = $Domain.Trim().ToLowerInvariant()
if ($Domain -notmatch '^(?=.{4,253}$)[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$') {
  throw "Nome de dominio invalido."
}
$secureKey = Read-Host "Chave API Resend com permissao de leitura dos dominios" -AsSecureString
$key = [System.Net.NetworkCredential]::new("", $secureKey).Password
try {
  if ([string]::IsNullOrWhiteSpace($key)) { throw "Chave API nao informada." }
  try {
    $response = Invoke-RestMethod -Method Get -Uri "https://api.resend.com/domains" -Headers @{Authorization = "Bearer $key"} -TimeoutSec 20 -ErrorAction Stop
  } catch {
    throw "Falha ao consultar domínios. Confira a chave, permissoes e conectividade no seu painel Resend."
  }
  $target = @($response.data) | Where-Object { $_.name -eq $Domain } | Select-Object -First 1
  if ($null -eq $target) {
    throw "Domínio não encontrado nos resultados da conta. Verifique no painel Resend > Domains."
  }
  if ($target.status -ne "verified") {
    throw "PENDENTE: Resend nao marcou o dominio como verified; confira os registros DNS."
  }
  Write-Host "APROVADO: o dominio informado consta como VERIFIED no Resend."
  Write-Host "AVISO: isso ainda NAO comprova envio SMTP nem entrega real de e-mail."
} finally {
  $key = $null
  $secureKey = $null
}
