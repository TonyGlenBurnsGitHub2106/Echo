<#
.SYNOPSIS
    Starts Echo in the background and waits until it answers. Story 5653.

.DESCRIPTION
    Runs target\echo.jar with the Java the installer chose (config\echo-install.json), from this
    folder, with no console window (javaw). What Echo writes goes to logs\echo.log. If Echo is
    already running, says so and does nothing.
#>
[CmdletBinding()]
param([int]$WaitSeconds = 90)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')

$info = Read-EchoInstall $root
$jar = Join-Path $root 'target\echo.jar'
if (-not (Test-Path $jar)) { throw "target\echo.jar is missing -- run install.cmd (or update-echo.cmd) to build it." }

$running = @(Get-EchoProcess $root)
if ($running.Count -gt 0) {
    Write-Host "Echo is already running (process $($running[0].ProcessId)) on http://localhost:$($info.port)"
    return
}

$javaw = Join-Path $info.javaHome 'bin\javaw.exe'
if (-not (Test-Path $javaw)) { $javaw = Join-Path $info.javaHome 'bin\java.exe' }
$jargs = @('-Xmx512m')
$lib = Join-Path $root 'lib'
if (Get-ChildItem $lib -Filter 'mssql-jdbc_auth-*.dll' -ErrorAction SilentlyContinue) { $jargs += "-Djava.library.path=$lib" }
$jargs += @('-jar', "`"$jar`"")
New-Item -ItemType Directory -Force (Join-Path $root 'logs') | Out-Null
$p = Start-Process -FilePath $javaw -ArgumentList $jargs -WorkingDirectory $root -WindowStyle Hidden -PassThru
Write-Host "Starting Echo (process $($p.Id)) ..."

$url = "http://127.0.0.1:$($info.port)/api/system/health"
$deadline = (Get-Date).AddSeconds($WaitSeconds)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 1500
    if ($p.HasExited) { throw "Echo stopped while starting (exit $($p.ExitCode)). See logs\echo.log." }
    try {
        $r = Invoke-RestMethod -Uri $url -TimeoutSec 3
        if ($r.ok) { Write-Host "Echo is up: http://localhost:$($info.port)" -ForegroundColor Green; return }
        Write-Warning "Echo answered but its database did not. See logs\echo.log."
        return
    } catch { }
}
Write-Warning "Echo did not answer within $WaitSeconds seconds. It may still be starting; see logs\echo.log."
