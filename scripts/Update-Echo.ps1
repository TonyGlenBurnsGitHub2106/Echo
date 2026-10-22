<#
.SYNOPSIS
    Brings Echo up to date: pull, database, build, restart. Story 5653.

.DESCRIPTION
      1. git pull --ff-only (skipped with -NoPull, or when this folder is not a git clone).
         A pull that cannot fast-forward stops here and changes nothing.
      2. Backs the database up first (scripts\Backup-EchoDatabase.ps1), unless -NoBackup.
      3. scripts\Install-EchoDatabase.ps1 -- new tables, procedures and triggers. Rows are kept.
      4. Stops Echo, builds it, starts it again.

    Uses the SQL Server and Java the installer recorded in config\echo-install.json.
#>
[CmdletBinding()]
param(
    [switch]$NoPull,
    [switch]$NoBackup,
    [string]$AdminSqlUser,
    [string]$AdminSqlPassword
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')
$info = Read-EchoInstall $root

if (-not $NoPull -and (Test-Path (Join-Path $root '.git'))) {
    Write-Host '== git pull'
    Push-Location $root
    try {
        & git pull --ff-only
        if ($LASTEXITCODE -ne 0) { throw 'git pull could not fast-forward. Nothing was changed; sort out the local changes first.' }
    } finally { Pop-Location }
}

$sql = @{ SqlServer = $info.sqlServer; Database = $info.database }
if ($AdminSqlUser) { $sql.SqlUser = $AdminSqlUser; $sql.SqlPassword = $AdminSqlPassword }

if (-not $NoBackup) {
    Write-Host '== backup'
    & (Join-Path $PSScriptRoot 'Backup-EchoDatabase.ps1') @sql
}

Write-Host '== database'
& (Join-Path $PSScriptRoot 'Install-EchoDatabase.ps1') @sql

Write-Host '== build and restart'
& (Join-Path $PSScriptRoot 'Stop-Echo.ps1')
Invoke-EchoBuild -Root $root -JavaHome $info.javaHome
& (Join-Path $PSScriptRoot 'Start-Echo.ps1')
