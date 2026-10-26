<#
.SYNOPSIS
    Brings Echo up to date: pull, download the newest release, database, restart. Story 5653.

.DESCRIPTION
      1. git pull --ff-only (skipped with -NoPull, or when this folder is not a git clone).
         A pull that cannot fast-forward stops here and changes nothing.
      2. Finds the newest Echo release on GitHub and downloads its echo.jar into target\download,
         checking its sha256. Nothing is compiled, so this computer needs no JDK or Maven for it.
         If this Echo already runs that program and the pull brought nothing new, it says
         "already up to date" and stops (-Force goes on anyway).
      3. Backs the database up first (scripts\Backup-EchoDatabase.ps1), unless -NoBackup.
      4. scripts\Install-EchoDatabase.ps1 -- new tables, procedures and triggers. Rows are kept.
      5. Stops Echo, moves the old target\echo.jar to target\previous\, puts the new one in its
         place and starts Echo. If the new one does not answer, the old one is put back and started.

    -Build compiles from source instead of downloading (the old way; needs a JDK).
    Every run writes logs\update-<date>.log and logs\update-state.json, which Settings -> Update Echo
    reads to show how the update went.

    Uses the SQL Server and Java the installer recorded in config\echo-install.json.
#>
[CmdletBinding()]
param(
    [switch]$NoPull,
    [switch]$NoBackup,
    [switch]$Build,
    [switch]$Force,
    [string]$AdminSqlUser,
    [string]$AdminSqlPassword
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')
$info = Read-EchoInstall $root

# ---- progress for Settings -> Update Echo (Story 5653) ------------------------------------------
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force $logDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$statePath = Join-Path $logDir 'update-state.json'
$startedAt = (Get-Date).ToString('s')
$logName = "update-$stamp.log"
$releaseTag = ''

function Write-UpdateState([string]$State, [string]$Message) {
    $s = [ordered]@{
        state    = $State
        message  = $Message
        started  = $startedAt
        finished = $(if ($State -eq 'running') { '' } else { (Get-Date).ToString('s') })
        pid      = [string]$PID
        log      = $logName
        release  = $releaseTag
    }
    [IO.File]::WriteAllText($statePath, ($s | ConvertTo-Json))
}

# One update at a time.
if (Test-Path $statePath) {
    try {
        $prev = Get-Content $statePath -Raw | ConvertFrom-Json
        if ($prev.state -eq 'running' -and $prev.pid -and [int]$prev.pid -ne $PID -and (Get-Process -Id ([int]$prev.pid) -ErrorAction SilentlyContinue)) {
            throw "An update is already running (process $($prev.pid)). Let it finish first."
        }
    } catch [System.ArgumentException] { }
}

# Windows can hold a jar for a moment after its Java has gone; try a move for a few seconds.
function Move-EchoFile([string]$From, [string]$To) {
    for ($try = 1; ; $try++) {
        try { Move-Item -Path $From -Destination $To -ErrorAction Stop; return }
        catch { if ($try -ge 20) { throw "Could not move $From to $To -- $($_.Exception.Message)" }; Start-Sleep -Milliseconds 500 }
    }
}

$transcribing = $false
$echoStopped = $false
$previous = $null
if ($AdminSqlPassword) { $logName = '' }   # never write a password into a log
else { Start-Transcript -Path (Join-Path $logDir $logName) | Out-Null; $transcribing = $true }
Write-UpdateState 'running' 'Updating Echo.'

$exitCode = 0
try {
    $isClone = Test-Path (Join-Path $root '.git')
    $before = if ($isClone) { [string](& git -C $root rev-parse HEAD) } else { '' }
    if (-not $NoPull -and $isClone) {
        Write-Host '== git pull'
        $out = & cmd /c "git -C `"$root`" pull --ff-only 2>&1"
        $out | ForEach-Object { Write-Host "  $_" }
        if ($LASTEXITCODE -ne 0) { throw 'git pull could not fast-forward. Nothing was changed; sort out the local changes first.' }
    }
    $after = if ($isClone) { [string](& git -C $root rev-parse HEAD) } else { '' }
    $pulledSomething = $before -ne $after

    $sql = @{ SqlServer = $info.sqlServer; Database = $info.database }
    if ($AdminSqlUser) { $sql.SqlUser = $AdminSqlUser; $sql.SqlPassword = $AdminSqlPassword }
    $jar = Join-Path $root 'target\echo.jar'

    if ($Build) {
        if (-not $NoBackup) { Write-Host '== backup'; & (Join-Path $PSScriptRoot 'Backup-EchoDatabase.ps1') @sql }
        Write-Host '== database'
        & (Join-Path $PSScriptRoot 'Install-EchoDatabase.ps1') @sql
        Write-Host '== build and restart'
        & (Join-Path $PSScriptRoot 'Stop-Echo.ps1')
        $echoStopped = $true
        Invoke-EchoBuild -Root $root -JavaHome $info.javaHome
        & (Join-Path $PSScriptRoot 'Start-Echo.ps1')
        Write-UpdateState 'done' 'Echo was built from source and restarted.'
    } else {
        Write-Host '== newest release'
        $release = Get-EchoLatestRelease (Get-EchoGitHubRepo $root)
        $releaseTag = $release.Tag
        Write-Host "  release $($release.Tag)"
        if ($isClone -and $release.Commit -and $release.Commit -ne $after) {
            Write-Host '  note: that release was built from a different commit than the scripts just pulled.' -ForegroundColor Yellow
        }
        $sameProgram = (Get-EchoFileSha256 $jar) -eq $release.Sha256
        if ($sameProgram -and -not $pulledSomething -and -not $Force) {
            if (-not (Read-EchoRelease $root)) { Write-EchoRelease $root $release }
            Write-Host "Echo is already up to date (release $($release.Tag))." -ForegroundColor Green
            Write-UpdateState 'uptodate' "Echo is already up to date (release $($release.Tag)). Nothing was changed."
        } else {
            $download = if ($sameProgram) { $null } else { Get-EchoReleaseJar -Root $root -Release $release }

            if (-not $NoBackup) { Write-Host '== backup'; & (Join-Path $PSScriptRoot 'Backup-EchoDatabase.ps1') @sql }
            Write-Host '== database'
            & (Join-Path $PSScriptRoot 'Install-EchoDatabase.ps1') @sql

            Write-Host '== restart'
            & (Join-Path $PSScriptRoot 'Stop-Echo.ps1')
            $echoStopped = $true
            if ($download) {
                if (Test-Path $jar) {
                    $prevDir = Join-Path $root 'target\previous'
                    New-Item -ItemType Directory -Force $prevDir | Out-Null
                    $previous = Join-Path $prevDir "echo-$stamp.jar"
                    Move-EchoFile $jar $previous
                    Write-Host "  the old program is kept as $previous"
                }
                Move-EchoFile $download $jar
            }
            $started = $true
            try { & (Join-Path $PSScriptRoot 'Start-Echo.ps1') } catch { Write-Host "  $($_.Exception.Message)" -ForegroundColor Red; $started = $false }
            if ($started) {
                try { $h = Invoke-RestMethod -Uri "http://127.0.0.1:$($info.port)/api/system/health" -TimeoutSec 5; $started = [bool]$h.ok }
                catch { $started = $false }
            }
            if (-not $started -and $previous) {
                Write-Host '== the new Echo did not start; putting the old one back' -ForegroundColor Yellow
                & (Join-Path $PSScriptRoot 'Stop-Echo.ps1')
                Move-EchoFile $jar (Join-Path (Split-Path $previous) "echo-$($release.Tag)-did-not-start-$stamp.jar")
                Move-EchoFile $previous $jar
                $previous = $null
                & (Join-Path $PSScriptRoot 'Start-Echo.ps1')
                throw "Release $($release.Tag) did not start, so the program you had is back and running. See logs\echo.log."
            }
            if (-not $started) { throw 'Echo did not answer after the restart. See logs\echo.log.' }
            Write-EchoRelease $root $release
            Write-Host "Echo is up to date (release $($release.Tag))." -ForegroundColor Green
            Write-UpdateState 'done' "Echo is updated to release $($release.Tag) and running."
        }
    }
} catch {
    Write-Host "Update failed: $($_.Exception.Message)" -ForegroundColor Red
    $why = $_.Exception.Message
    # Never leave Echo down: if this run stopped it and it is not running, start what is there
    # (the old program, put back first if it was already moved aside).
    if ($echoStopped -and @(Get-EchoProcess $root).Count -eq 0) {
        try {
            if (-not (Test-Path $jar) -and $previous -and (Test-Path $previous)) { Move-EchoFile $previous $jar }
            Write-Host '== starting Echo again' -ForegroundColor Yellow
            & (Join-Path $PSScriptRoot 'Start-Echo.ps1')
            $why += ' Echo was started again.'
        } catch { $why += " Echo could not be started again ($($_.Exception.Message)) -- run start-echo.cmd." }
    }
    Write-UpdateState 'failed' $why
    $exitCode = 1
} finally {
    if ($transcribing) { Stop-Transcript | Out-Null }
}
exit $exitCode
