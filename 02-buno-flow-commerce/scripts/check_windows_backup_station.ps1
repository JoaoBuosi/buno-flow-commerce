#Requires -Version 5.1
<#
Read-only station preflight for real Flow Commerce backup.
No network, database connection, role change or credential access.
BitLocker only: a non-BitLocker encrypted volume requires independent review.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [string] $OutputDirectory
)
$ErrorActionPreference = "Stop"
function Block([string] $Reason) {
    [Console]::Error.WriteLine("BLOCKED: $Reason")
    exit 2
}
try {
    if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) {
        Block "A pasta de saída ainda não existe"
    }
    $resolved = (Resolve-Path -LiteralPath $OutputDirectory).ProviderPath
    if ($resolved -notmatch '^[A-Za-z]:\\') {
        Block "Destino deve ser um volume local (não UNC ou compartilhamento remoto)"
    }
    $repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\')
    if ($resolved.Equals($repository,[StringComparison]::OrdinalIgnoreCase) -or
        $resolved.StartsWith($repository + '\',[StringComparison]::OrdinalIgnoreCase)) {
        Block "Backup deve ser fora do repositório"
    }
    $mount = [IO.Path]::GetPathRoot($resolved).TrimEnd('\')
    $volume = Get-BitLockerVolume -MountPoint $mount -ErrorAction Stop
    if (-not $volume -or
        $volume.ProtectionStatus.ToString() -ne 'On' -or
        $volume.VolumeStatus.ToString() -ne 'FullyEncrypted') {
        Block "Volume sem prova de proteção BitLocker completa"
    }
    foreach ($name in @('psql.exe','pg_dump.exe','pg_restore.exe')) {
        $command = Get-Command $name -ErrorAction Stop
        $version = (& $command.Source --version 2>&1 | Select-Object -First 1).ToString()
        if ($LASTEXITCODE -ne 0 -or $version -notmatch 'PostgreSQL\)\s+17(?:\.|$)') {
            Block "Ferramenta $name deve ser versão PostgreSQL 17"
        }
    }
    Write-Output "PRECHECK PASS: volume BitLocker e ferramentas PostgreSQL 17 presentes; nenhuma conexão executada"
    Write-Output "MANUAL OBRIGATÓRIO: permissões da pasta, ausência de túnel local, role remota somente leitura, retenção e descarte"
    exit 0
} catch {
    Block "Falha ao comprovar BitLocker, caminho privado ou ferramentas PostgreSQL 17"
}
