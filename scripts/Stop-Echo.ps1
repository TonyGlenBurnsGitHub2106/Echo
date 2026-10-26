<#
.SYNOPSIS
    Stops the Echo that runs from this folder. Story 5653.

.DESCRIPTION
    Finds the Java process running THIS folder's target\echo.jar and ends it. No other Java
    program is touched.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')

$procs = @(Get-EchoProcess $root)
if ($procs.Count -eq 0) { Write-Host 'Echo is not running.'; return }
foreach ($p in $procs) {
    try { Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop }
    catch {
        # Story 5653: an Echo started as administrator can only be stopped as administrator.
        throw "Echo (process $($p.ProcessId)) is running as administrator, so this window cannot stop it. Right-click the .cmd and choose Run as administrator, or end javaw.exe in Task Manager, then try again."
    }
    # Story 5653: wait until it has really gone -- until then Windows keeps target\echo.jar locked
    # and Update-Echo.ps1 cannot swap it.
    try { Wait-Process -Id $p.ProcessId -Timeout 30 -ErrorAction Stop } catch { }
    Write-Host "Stopped Echo (process $($p.ProcessId))."
}
