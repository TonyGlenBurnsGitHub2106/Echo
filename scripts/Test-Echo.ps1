<#
.SYNOPSIS
    Runs Echo's Spock specifications. Story 5653.

.DESCRIPTION
    With no switches: the unit and web specs (src\test\groovy\com\echo, *Spec) -- no database, no
    browser, safe anywhere. The first run downloads what the tests need.

    The two other suites are opt-in and have their own way in:
      Database specs (*ITSpec)  scripts\Test-EchoDatabase.ps1 -- a throwaway EchoTest_ database only.
      Browser specs (*E2ESpec)  mvnw -Pe2e test -Decho.e2e.user=NAME with ECHO_E2E_PASSWORD set,
                                against a running Echo (the first run downloads Chromium).

.PARAMETER Test
    Optional filter, e.g. 'SolutionServiceSpec' or 'Solution*Spec'.

.PARAMETER JavaHome
    A Java 21+ JDK. Left out: found the way install.cmd finds it.

.EXAMPLE
    test-echo.cmd
.EXAMPLE
    test-echo.cmd -Test ApiClientPathFenceSpec
#>
[CmdletBinding()]
param(
    [string]$Test,
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')

$java = Find-EchoJava -JavaHome $JavaHome
Write-Host "Running Echo's specs with $($java.Home) (Java $($java.Major))"
$mavenArgs = @('-q', 'test')
if ($Test) { $mavenArgs += "-Dtest=$Test"; $mavenArgs += '-Dsurefire.failIfNoSpecifiedTests=false' }
$started = Get-Date
Invoke-EchoMaven -Root $root -JavaHome $java.Home -MavenArgs $mavenArgs

# The totals, read from the reports this run wrote (older reports from other suites stay in the folder).
$run = 0; $failed = 0; $skipped = 0
Get-ChildItem (Join-Path $root 'target\surefire-reports') -Filter '*.txt' -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -ge $started } | ForEach-Object {
    $t = Get-Content $_.FullName -Raw
    if ($t -match 'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)') {
        $run += [int]$Matches[1]; $failed += [int]$Matches[2] + [int]$Matches[3]; $skipped += [int]$Matches[4]
    }
}
Write-Host ''
Write-Host "Echo's specs passed: $run run, $failed failed, $skipped known bugs parked (@PendingFeature)." -ForegroundColor Green
