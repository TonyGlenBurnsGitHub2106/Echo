<#
.SYNOPSIS
    Takes Echo off this computer again -- the reverse of install.cmd. Story 5653.

.DESCRIPTION
    Puts the computer back the way it was before install.cmd ran, so an install can be tried again
    from scratch. In order:

      1. Stops Echo.
      2. Backs the database up first (scripts\Backup-EchoDatabase.ps1) unless -NoBackup. The .bak
         goes to the SQL Server's backup folder (or -BackupFolder) and is kept.
      3. Drops the Echo database and the "echo_app" SQL login the installer made.
      4. Removes the "start with Windows" shortcut, if -AutoStart made one.
      5. Moves what the install wrote into this folder to the Recycle Bin (nothing is deleted
         outright): config\, logs\, target\, lib\ and -- unless -KeepFiles -- EchoFiles\ (the
         files attached to solutions). backups\ and the code itself are left alone.

    Run it as administrator, like install.cmd. It asks twice before it changes anything: type YES,
    then type the database's name (the second question says how many solutions go with it).
    -Yes skips both questions, for scripts. It drops only a database that holds Echo's own
    tables. Safe to run when Echo is half installed: each step skips what is not there.

.EXAMPLE
    uninstall-echo.cmd
.EXAMPLE
    uninstall-echo.cmd -NoBackup -Yes
#>
[CmdletBinding()]
param(
    [string]$SqlServer,
    [string]$Database,
    [string]$AdminSqlUser,
    [string]$AdminSqlPassword,
    [string]$BackupFolder,
    [switch]$NoBackup,
    [switch]$KeepFiles,
    [switch]$Yes
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')
# Story 5653: run as administrator like install.cmd -- dropping a SQL login and killing its sessions
# need it, and an Echo started elevated (an older install, or start-echo.cmd run as administrator)
# can only be stopped by one.
Assert-EchoAdmin 'uninstall-echo.cmd' 'It removes a SQL login and may need to stop an Echo started as administrator, which only an administrator can do.'

$installJson = Join-Path $root 'config\echo-install.json'
if (Test-Path $installJson) {
    $info = Get-Content $installJson -Raw | ConvertFrom-Json
    if (-not $SqlServer) { $SqlServer = $info.sqlServer }
    if (-not $Database)  { $Database  = $info.database }
}
if (-not $Database) { $Database = 'Echo' }
if (-not $SqlServer) { $SqlServer = Select-EchoSqlServer -Root $root -SqlUser $AdminSqlUser -SqlPassword $AdminSqlPassword }
Assert-EchoDatabaseName $Database

# Story 5653: decided BEFORE anything is stopped or asked -- only ever an Echo database is dropped,
# never one without Echo's own tables, whatever -Database or the config says.
$c = New-EchoConnection -SqlServer $SqlServer -Database 'master' -SqlUser $AdminSqlUser -SqlPassword $AdminSqlPassword
try {
    $dbId = Invoke-EchoScalar $c "SELECT DB_ID(N'$Database')"
    $dbExists = -not ($dbId -is [DBNull] -or $null -eq $dbId)
    if ($dbExists) {
        # The same test the installer uses, so a half-finished first install can be taken off too.
        if (-not (Test-EchoIsEchoDatabase $c $Database)) { throw "$Database on $SqlServer has neither Echo's sign-in table (AppUser) nor the installer's Echo mark, so it is not an Echo database. Nothing was changed." }
    }
} finally { $c.Close() }

$folders = @('config', 'logs', 'target', 'lib')
if (-not $KeepFiles) { $folders += 'EchoFiles' }

Write-Host ''
Write-Host "This takes Echo off this computer:" -ForegroundColor Yellow
Write-Host "  - the database $Database on $SqlServer (and every row in it)$(if (-not $NoBackup) { ', backed up first' })"
Write-Host "  - the SQL login echo_app$(if ($info -and $info.windowsLogin) { " and the Windows login $($info.windowsLogin) the install made" }) (kept if another database still uses it)"
Write-Host "  - these folders, to the Recycle Bin: $($folders -join ', ')"
if (-not $Yes) {
    $answer = Read-Host 'Type YES to go ahead'
    if ($answer -cne 'YES') { Write-Host 'Nothing was changed.'; return }

    # Story 5653 (Tony, 2026-10-28): "Uninstall echo cmd should confirm twice before it just does it!"
    # The second question is different from the first on purpose -- it says what is about to be lost
    # and asks for the database's name, so a YES typed twice out of habit cannot get past it.
    $solutions = $null
    if ($dbExists) {
        $c = New-EchoConnection -SqlServer $SqlServer -Database $Database -SqlUser $AdminSqlUser -SqlPassword $AdminSqlPassword
        try { $solutions = Invoke-EchoScalar $c "IF OBJECT_ID('dbo.Solution') IS NULL SELECT -1 ELSE SELECT COUNT(*) FROM dbo.Solution" }
        catch { $solutions = $null }
        finally { $c.Close() }
    }
    Write-Host ''
    Write-Host 'Are you sure? This is the last question.' -ForegroundColor Yellow
    if ($null -ne $solutions -and [int]$solutions -ge 0) {
        Write-Host "  $Database holds $solutions solution(s); they go with it."
    }
    if ($NoBackup) { Write-Host '  -NoBackup was given: there will be NO backup to bring them back from.' -ForegroundColor Red }
    else { Write-Host '  A backup is made first.' }
    $answer = Read-Host "Type the database name ($Database) to uninstall Echo"
    if ($answer -cne $Database) { Write-Host 'Nothing was changed.'; return }
}

function Step([string]$text) { Write-Host ''; Write-Host "== $text" -ForegroundColor Cyan }

# ---- 1. Stop ----------------------------------------------------------------------------------
Step 'Stop'
& (Join-Path $PSScriptRoot 'Stop-Echo.ps1')

# ---- 2 + 3. Database and login ----------------------------------------------------------------
Step 'Database'
$c = New-EchoConnection -SqlServer $SqlServer -Database 'master' -SqlUser $AdminSqlUser -SqlPassword $AdminSqlPassword
try {
    if (-not $dbExists) {
        Write-Host "  no database $Database -- nothing to drop"
    } else {
        if (-not $NoBackup) {
            $b = @{ SqlServer = $SqlServer; Database = $Database }
            if ($AdminSqlUser) { $b.SqlUser = $AdminSqlUser; $b.SqlPassword = $AdminSqlPassword }
            if ($BackupFolder) { $b.Folder = $BackupFolder }
            & (Join-Path $PSScriptRoot 'Backup-EchoDatabase.ps1') @b
        }
        Invoke-EchoNonQuery $c "ALTER DATABASE [$Database] SET SINGLE_USER WITH ROLLBACK IMMEDIATE"
        Invoke-EchoNonQuery $c "DROP DATABASE [$Database]"
        Write-Host "  dropped database $Database"
    }
    # Story 5653: a login belongs to the whole SQL Server; another Echo database here (made with
    # -Database) may still use it. Then it stays, and so do its sessions.
    function Remove-EchoLoginIfUnused([string]$login, [switch]$KillSessions) {
        $q = $login -replace "'", "''"
        if ([int](Invoke-EchoScalar $c "SELECT COUNT(*) FROM sys.server_principals WHERE name = N'$q'") -eq 0) {
            Write-Host "  no SQL login $login -- nothing to drop"; return
        }
        $r = $c.CreateCommand(); $r.CommandText = "SELECT name FROM sys.databases WHERE state = 0 AND HAS_DBACCESS(name) = 1"
        $dbs = @(); $rd = $r.ExecuteReader(); while ($rd.Read()) { $dbs += [string]$rd[0] }; $rd.Close()
        $usedBy = @()
        foreach ($d in $dbs) {
            $sql = "SELECT COUNT(*) FROM [" + ($d -replace '\]', ']]') + "].sys.database_principals WHERE sid = SUSER_SID(N'$q') AND name <> 'dbo'"
            if ([int](Invoke-EchoScalar $c $sql) -gt 0) { $usedBy += $d }
        }
        if ($usedBy.Count -gt 0) { Write-Host "  kept SQL login $login -- still used by $($usedBy -join ', ')"; return }
        if ($KillSessions) {
            # Its open sessions would block the drop; Echo is stopped, so these are only stragglers.
            $r = $c.CreateCommand(); $r.CommandText = "SELECT session_id FROM sys.dm_exec_sessions WHERE login_name = N'$q'"
            $ids = @(); $rd = $r.ExecuteReader(); while ($rd.Read()) { $ids += [int]$rd[0] }; $rd.Close()
            foreach ($id in $ids) { Invoke-EchoNonQuery $c "KILL $id" }
        }
        try {
            Invoke-EchoNonQuery $c ("DROP LOGIN [" + ($login -replace '\]', ']]') + "]")
            Write-Host "  dropped SQL login $login"
        } catch {
            Write-Warning "Could not drop the SQL login $login (it may be signed in right now): $($_.Exception.InnerException.Message)"
        }
    }
    Remove-EchoLoginIfUnused 'echo_app' -KillSessions
    # The Windows login an -Auth Windows install made for the desktop account (never one that was
    # already there). Its sessions are that person's own programs, so they are never killed.
    if ($info -and $info.windowsLogin) { Remove-EchoLoginIfUnused ([string]$info.windowsLogin) }
} finally { $c.Close() }

# ---- 4. Start-with-Windows shortcut -----------------------------------------------------------
Step 'Start with Windows'
$lnk = Join-Path (Get-EchoStartupFolder) 'Echo.lnk'   # Story 5653: where install.cmd put it
if (Test-Path $lnk) {
    Add-Type -AssemblyName Microsoft.VisualBasic
    [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($lnk, 'OnlyErrorDialogs', 'SendToRecycleBin')
    Write-Host "  moved $lnk to the Recycle Bin"
} else {
    Write-Host '  none'
}

# ---- 5. What the install wrote here -----------------------------------------------------------
Step 'Files'
Add-Type -AssemblyName Microsoft.VisualBasic
foreach ($f in $folders) {
    $p = Join-Path $root $f
    if (Test-Path $p) {
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($p, 'OnlyErrorDialogs', 'SendToRecycleBin')
        Write-Host "  moved $f\ to the Recycle Bin"
    }
}

Write-Host ''
Write-Host 'Echo is off this computer. install.cmd puts it back.' -ForegroundColor Green
