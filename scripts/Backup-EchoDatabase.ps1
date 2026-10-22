<#
.SYNOPSIS
    Backs up the Echo database to a dated .bak file. Story 5653.

.DESCRIPTION
    SQL Server itself writes a backup, so the file must go somewhere the SQL Server service may
    write. By default this is the server's own backup folder (a bare file name goes there); pass
    -Folder to choose another the service can reach. COPY_ONLY, so it never disturbs any backup
    plan the server already has. Afterwards it says where the file is.

    -Keep N leaves the newest N Echo backups in that folder and names the older ones -- it never
    deletes a file.

.EXAMPLE
    backup-echo.cmd
.EXAMPLE
    backup-echo.cmd -Folder D:\Backups\Echo
#>
[CmdletBinding()]
param(
    [string]$SqlServer,
    [string]$Database,
    [string]$SqlUser,
    [string]$SqlPassword,
    [string]$Folder,
    [int]$Keep = 14
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')
if (-not $SqlServer -or -not $Database) {
    $info = Read-EchoInstall $root
    if (-not $SqlServer) { $SqlServer = $info.sqlServer }
    if (-not $Database)  { $Database  = $info.database }
}

$name = '{0}_{1}.bak' -f $Database, (Get-Date -Format 'yyyy_MM_dd__HH_mm_ss')
$target = if ($Folder) { Join-Path $Folder $name } else { $name }
$c = New-EchoConnection -SqlServer $SqlServer -Database 'master' -SqlUser $SqlUser -SqlPassword $SqlPassword
try {
    Invoke-EchoNonQuery $c "BACKUP DATABASE [$Database] TO DISK = N'$($target -replace "'", "''")' WITH COPY_ONLY, INIT, NAME = N'Echo backup'"
    $where = Invoke-EchoScalar $c @"
SELECT TOP 1 f.physical_device_name
FROM   msdb.dbo.backupset s JOIN msdb.dbo.backupmediafamily f ON f.media_set_id = s.media_set_id
WHERE  s.database_name = N'$Database' ORDER BY s.backup_finish_date DESC
"@
} finally { $c.Close() }
Write-Host "Echo database backed up to $where" -ForegroundColor Green

$dir = Split-Path -Parent $where
if ($Keep -gt 0 -and (Test-Path $dir)) {
    # SQL Server's own backup folder is often closed to everyone but the service: then just say nothing.
    $old = Get-ChildItem $dir -Filter "$($Database)_*.bak" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -Skip $Keep
    if ($old) {
        Write-Host "Older Echo backups you may want to move or remove yourself (kept $Keep newest):"
        $old | ForEach-Object { Write-Host "  $($_.FullName)" }
    }
}
