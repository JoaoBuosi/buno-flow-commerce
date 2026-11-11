# Flow Commerce 0.5.3 — execução local de backup REAL (não executa sem parâmetros e confirmação).
# Segurança: dump e restauração acontecem somente na máquina do operador, NUNCA no GitHub.
# Exige PostgreSQL 17 client tools, Python 3, destino PostgreSQL 17 LOCAL e VAZIO,
# credencial de origem somente leitura, e pasta de backup protegida por criptografia.
[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$SourceHost,
  [Parameter(Mandatory=$true)][string]$SourceUser,
  [Parameter(Mandatory=$true)][string]$BackupDirectory,
  [string]$LocalDatabase = "flow_restore_qa",
  [ValidateRange(1,65535)][int]$LocalPort = 5432,
  [switch]$ConfirmEncryptedStorage
)
$ErrorActionPreference = "Stop"
$projectRef = "pqdwxihinbmeiorkqdtq"

if (-not $ConfirmEncryptedStorage) {
  throw "Operação bloqueada: confirme que BackupDirectory está em volume PRIVADO e CRIPTOGRAFADO com -ConfirmEncryptedStorage."
}
if ($SourceHost -ne "db.$projectRef.supabase.co" -and
   -not ($SourceHost -match '^[a-z0-9-]+\.pooler\.supabase\.com$' -and
         $SourceUser.EndsWith(".$projectRef"))) {
  throw "A origem precisa ser o host direto do projeto Flow Commerce ou seu Session Pooler com usuário vinculado ao mesmo projeto."
}
if ($LocalDatabase -notmatch '^flow_restore_[a-z0-9_]{1,48}$') {
  throw "O banco local deve ser descartável, vazio e ter nome flow_restore_*."
}
if (-not (Test-Path -LiteralPath $BackupDirectory -PathType Container)) {
  throw "A pasta criptografada de backup precisa existir antes da execução."
}
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$outPath = [IO.Path]::GetFullPath($BackupDirectory)
if ($outPath.TrimEnd('\') -eq $repoRoot.TrimEnd('\') -or
    $outPath.StartsWith($repoRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
  throw "Backup não pode ficar dentro do repositório."
}
foreach ($tool in @("pg_dump","pg_restore","psql","py")) {
  if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
    throw "Ferramenta ausente no Windows: $tool. Instale clientes PostgreSQL 17 e Python 3."
  }
}
foreach ($tool in @("pg_dump","pg_restore","psql")) {
  $version = (& $tool --version | Out-String).Trim()
  if ($version -notmatch '\b17\.\d+\b') {
    throw "O cliente $tool deve ser PostgreSQL 17, compatível com o servidor Supabase Flow Commerce 17.11."
  }
}
$script = Join-Path $PSScriptRoot "verify_isolated_restore.py"
if (-not (Test-Path -LiteralPath $script -PathType Leaf)) {
  throw "Script de verificação ausente: verify_isolated_restore.py"
}
$backupName = "flow-commerce-real-$(Get-Date -Format yyyyMMdd-HHmmss)-$([guid]::NewGuid().ToString('N').Substring(0,8)).dump"
$backup = Join-Path $outPath $backupName
Write-Host "Origem aprovada: projeto Supabase flow-commerce (PostgreSQL 17)."
Write-Host "Destino: 127.0.0.1:$LocalPort / $LocalDatabase"
Write-Host "Verifique que o banco de destino está VAZIO e isolado; o script bloqueia tabelas preexistentes."
Write-Warning "O backup conterá dados de contas/pedidos. Não envie dump ou senha ao ChatGPT/GitHub. Use criptografia em repouso."

# Credenciais nunca são incluídas na linha de comando nem gravadas no repositório.
$originSecret = Read-Host "Senha do usuário Supabase SOMENTE LEITURA" -AsSecureString
$localSecret = Read-Host "Senha do PostgreSQL local isolado" -AsSecureString
$variables = @(
  "FLOW_BACKUP_SOURCE_PGHOST","FLOW_BACKUP_SOURCE_PGPORT","FLOW_BACKUP_SOURCE_PGDATABASE",
  "FLOW_BACKUP_SOURCE_PGUSER","FLOW_BACKUP_SOURCE_PGPASSWORD","FLOW_BACKUP_SOURCE_PGSSLMODE",
  "FLOW_RESTORE_PGHOST","FLOW_RESTORE_PGPORT","FLOW_RESTORE_PGDATABASE",
  "FLOW_RESTORE_PGUSER","FLOW_RESTORE_PGPASSWORD","FLOW_RESTORE_PGSSLMODE"
)
try {
  $env:FLOW_BACKUP_SOURCE_PGHOST = $SourceHost
  $env:FLOW_BACKUP_SOURCE_PGPORT = "5432"
  $env:FLOW_BACKUP_SOURCE_PGDATABASE = "postgres"
  $env:FLOW_BACKUP_SOURCE_PGUSER = $SourceUser
  $env:FLOW_BACKUP_SOURCE_PGSSLMODE = "require"
  $env:FLOW_BACKUP_SOURCE_PGPASSWORD = [System.Net.NetworkCredential]::new("", $originSecret).Password
  $env:FLOW_RESTORE_PGHOST = "127.0.0.1"
  $env:FLOW_RESTORE_PGPORT = "$LocalPort"
  $env:FLOW_RESTORE_PGDATABASE = $LocalDatabase
  $env:FLOW_RESTORE_PGUSER = "postgres"
  $env:FLOW_RESTORE_PGSSLMODE = "disable"
  $env:FLOW_RESTORE_PGPASSWORD = [System.Net.NetworkCredential]::new("", $localSecret).Password

  $roleSQL = @'
SELECT NOT COALESCE(bool_or(
  has_table_privilege(current_user,format('%I.%I',schemaname,tablename),
                      'INSERT,UPDATE,DELETE,TRUNCATE')
),false)
FROM pg_tables WHERE schemaname='flow_commerce';
'@
  $readOnly = (& psql -X -q -t -A -v ON_ERROR_STOP=1 -c $roleSQL | Out-String).Trim()
  if ($LASTEXITCODE -ne 0 -or $readOnly -ne 't') {
    throw "Origem BLOQUEADA: a credencial precisa ter somente leitura nas tabelas do schema flow_commerce."
  }
  & py -3 $script --output $backup --execute
  if ($LASTEXITCODE -ne 0) { throw "Homologação NÃO APROVADA; confira os detalhes locais e proteja o dump." }
  Write-Host "Backup e restauração local aprovados. Arquivo confidencial preservado na pasta escolhida."
} finally {
  foreach ($name in $variables) {
    Remove-Item -Path "Env:\$name" -ErrorAction SilentlyContinue
  }
  $originSecret = $null
  $localSecret = $null
}
