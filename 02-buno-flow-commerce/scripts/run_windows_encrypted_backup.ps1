#Requires -Version 5.1
<#
.SYNOPSIS
  Buno Flow Commerce encrypted Windows backup + isolated restore.
.DESCRIPTION
  Check mode is OFFLINE and does not touch a database.
  -Execute requires an unlocked BitLocker volume, configured PostgreSQL 17,
  an approved export-only role and a local empty restore database.
  Passwords are prompted from the operator's independent vault, never printed,
  passed as CLI arguments, stored in scripts or committed to Git.
  This script must run on the AUTHORIZED WINDOWS WORKSTATION, not GitHub Actions.
.EXAMPLE
  .\run_windows_encrypted_backup.ps1 -OutputDirectory "X:\FlowBackupsPrivados"
.EXAMPLE
  .\run_windows_encrypted_backup.ps1 -OutputDirectory "X:\FlowBackupsPrivados" -Execute
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$OutputDirectory,
  [switch]$Execute
)
$ErrorActionPreference = "Stop"
$sourcePtr = [IntPtr]::Zero
$restorePtr = [IntPtr]::Zero
$oldSourcePassword = [Environment]::GetEnvironmentVariable('FLOW_BACKUP_SOURCE_PGPASSWORD','Process')
$oldRestorePassword = [Environment]::GetEnvironmentVariable('FLOW_RESTORE_PGPASSWORD','Process')
$oldPgPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD','Process')
function Fail([string]$Reason) { throw "BLOQUEADO: $Reason" }
function RunPython([string[]]$Arguments) {
  $launcher = Get-Command 'py.exe' -ErrorAction Stop
  & $launcher.Source -3 @Arguments
  if ($LASTEXITCODE -ne 0) { Fail "Verificacao PostgreSQL interrompida. Nenhuma aprovacao concedida." }
}
try {
  if ($oldSourcePassword -or $oldRestorePassword -or $oldPgPassword) {
    Fail "Nao mantenha senhas PostgreSQL anteriores no ambiente do PowerShell. Abra sessao limpa."
  }
  $preflight = Join-Path $PSScriptRoot 'check_windows_backup_station.ps1'
  & $preflight -OutputDirectory $OutputDirectory
  if ($LASTEXITCODE -ne 0) { Fail "Estacao Windows, PostgreSQL 17 ou BitLocker nao foi aprovado." }

  $directory = (Resolve-Path -LiteralPath $OutputDirectory -ErrorAction Stop).ProviderPath
  $acl = Get-Acl -LiteralPath $directory
  $publicSids = @('S-1-1-0','S-1-5-11','S-1-5-32-545','S-1-5-32-546')
  foreach ($rule in $acl.Access) {
    try {
      $sid = $rule.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value
      if ($rule.AccessControlType -eq 'Allow' -and $publicSids -contains $sid) {
        Fail "A pasta cifrada concede acesso a grupo generico. Ajuste ACL antes do backup."
      }
    } catch [System.Security.Principal.IdentityNotMappedException] {
      Fail "Nao foi possivel verificar todas as ACLs da pasta."
    }
  }
  if (([Environment]::GetEnvironmentVariable('FLOW_BACKUP_SOURCE_PGUSER','Process') -notmatch '^flow_backup_export(?:\.[a-z0-9_]+)?$')) {
    Fail "A origem deve usar exclusivamente a role flow_backup_export aprovada."
  }
  $required = @(
    'FLOW_BACKUP_SOURCE_PGHOST','FLOW_BACKUP_SOURCE_PGPORT',
    'FLOW_BACKUP_SOURCE_PGDATABASE','FLOW_BACKUP_SOURCE_PGSSLMODE',
    'FLOW_RESTORE_PGHOST','FLOW_RESTORE_PGPORT',
    'FLOW_RESTORE_PGDATABASE','FLOW_RESTORE_PGUSER'
  )
  foreach ($key in $required) {
    if (-not [Environment]::GetEnvironmentVariable($key,'Process')) {
      Fail "Variavel exigida ausente: $key"
    }
  }
  foreach ($key in @('PGHOSTADDR','PGSERVICE','PGSERVICEFILE','PGPASSFILE','PGOPTIONS','PGPASSWORD')) {
    if ([Environment]::GetEnvironmentVariable($key,'Process')) {
      Fail "Substituicao de conexao PostgreSQL nao permitida: $key"
    }
  }
  $name = "flow-commerce-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + "-" + [guid]::NewGuid().ToString('N').Substring(0,8) + ".dump"
  $backup = Join-Path $directory $name
  if (Test-Path -LiteralPath $backup) { Fail "Arquivo ja existe; nunca sobrescrever." }

  $preflightPy = Join-Path $PSScriptRoot 'real_restore_preflight.py'
  RunPython @($preflightPy,'--output',$backup)

  if (-not $Execute) {
    Write-Output "PRECHECK OK: infraestrutura local e configuracao examinadas. Nenhum backup iniciado."
    Write-Output "A execucao exige -Execute, login ativo da role e credenciais no cofre."
    return
  }

  Write-Output "O backup pode conter pedidos, contas e dados MFA. Confirme que o volume e a base local sao autorizados."
  $confirmation = Read-Host 'Digite BACKUP-ISOLADO para confirmar origem remota e destino LOCAL sem tunel'
  if ($confirmation -cne 'BACKUP-ISOLADO') { Fail "Operador nao confirmou o destino isolado." }

  $sourceSecret = Read-Host 'Senha da role flow_backup_export (consulte o cofre independente)' -AsSecureString
  $restoreSecret = Read-Host 'Senha do PostgreSQL 17 LOCAL de restauracao' -AsSecureString
  if ($sourceSecret.Length -lt 1 -or $restoreSecret.Length -lt 1) {
    Fail "Credenciais de origem ou destino nao fornecidas."
  }

  # libpq consumes passwords from a child process environment. They exist only
  # during this one local invocation; no console, GitHub or artifact receives them.
  $sourcePtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sourceSecret)
  $restorePtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($restoreSecret)
  [Environment]::SetEnvironmentVariable('FLOW_BACKUP_SOURCE_PGPASSWORD',
    [Runtime.InteropServices.Marshal]::PtrToStringBSTR($sourcePtr),'Process')
  [Environment]::SetEnvironmentVariable('FLOW_RESTORE_PGPASSWORD',
    [Runtime.InteropServices.Marshal]::PtrToStringBSTR($restorePtr),'Process')

  $restorePy = Join-Path $PSScriptRoot 'verify_isolated_restore.py'
  RunPython @($restorePy,'--output',$backup,'--execute')
  if (-not (Test-Path -LiteralPath $backup -PathType Leaf)) {
    Fail "Nenhum arquivo de backup foi produzido."
  }
  $file = Get-Item -LiteralPath $backup
  if ($file.Length -le 0) { Fail "Arquivo de backup vazio." }
  $hash = (Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash.ToLowerInvariant()
  $manifest = $backup + '.sha256'
  [IO.File]::WriteAllText($manifest,($hash + '  ' + $file.Name + [Environment]::NewLine),
    [Text.Encoding]::ASCII)
  Write-Output "BACKUP E RESTAURACAO LOCAL CONCLUIDOS; SHA-256 armazenado ao lado do dump cifrado."
  Write-Output "Revisao humana de integridade, restauracao da aplicacao, retencao e RPO/RTO ainda obrigatoria."
} catch {
  [Console]::Error.WriteLine("BLOQUEADO: nao foi possivel concluir e homologar o backup. Se algum dump parcial existe, proteja-o no volume cifrado.")
  exit 1
} finally {
  [Environment]::SetEnvironmentVariable('FLOW_BACKUP_SOURCE_PGPASSWORD',$null,'Process')
  [Environment]::SetEnvironmentVariable('FLOW_RESTORE_PGPASSWORD',$null,'Process')
  if ($sourcePtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($sourcePtr) }
  if ($restorePtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($restorePtr) }
}
