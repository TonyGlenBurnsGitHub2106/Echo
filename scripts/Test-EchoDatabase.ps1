<#
.SYNOPSIS
    Runs Echo's database integration specs (*ITSpec) against a throwaway EchoTest_ database. Story 5653.

.DESCRIPTION
    1. Creates the test database, or brings it up to date, with Install-EchoDatabase.ps1 (every db\
       script is idempotent; an existing EchoTest_ database keeps its rows).
    2. Runs  mvnw -Pdb-it test  with ECHO_IT_URL pointing at that database. The db-it profile runs
       ONLY the *ITSpec specs in src\test\groovy\com\echo; a plain  mvnw test  never does.

    Every feature of every spec runs inside one transaction that is rolled back when the feature
    ends, so the test database is left as it was found. The database is never dropped.

    SAFETY: the database name must start with EchoTest_ -- this script refuses anything else before
    it touches a server, and the specs themselves refuse to run against any other name. Echo's own
    database (Echo), Leonardo and Hearth can never be the target.

.PARAMETER SqlServer
    The SQL Server instance for the install step (Windows sign-in), e.g. RENAISSANCE\SQL2012EXPRESS.

.PARAMETER Database
    The test database. Must start with EchoTest_. Default EchoTest_Spec.

.PARAMETER JdbcHost / Port
    Where the specs connect. JDBC needs a TCP port when the SQL Browser service is not running.
    Default: the host part of -SqlServer, port 1433.

.PARAMETER SqlUser / SqlPassword
    The SQL login the specs connect with (it must be able to read and write the test database).

.PARAMETER InstallSqlUser / InstallSqlPassword
    A SQL login for the install step. Left out: Windows sign-in (you).

.PARAMETER Test
    Optional -Dtest filter, e.g. 'JdbcSolutionTermRepositoryITSpec' or 'JdbcSolution*ITSpec'.

.PARAMETER SkipInstall
    Run the specs against the database as it is.

.PARAMETER JavaHome
    A Java 21+ JDK. Left out: found the way Install-Echo.ps1 finds it.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\Test-EchoDatabase.ps1 -SqlServer RENAISSANCE\SQL2012EXPRESS -Port 49993 -SqlUser <login> -SqlPassword <password>
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$SqlServer,
    [string]$Database = 'EchoTest_Spec',
    [string]$JdbcHost,
    [int]$Port = 1433,
    [Parameter(Mandatory = $true)][string]$SqlUser,
    [Parameter(Mandatory = $true)][string]$SqlPassword,
    [string]$InstallSqlUser,
    [string]$InstallSqlPassword,
    [string]$Test,
    [switch]$SkipInstall,
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')

# Story 5653: the one rule that matters -- a test run only ever touches a database named EchoTest_*.
if ($Database -cnotlike 'EchoTest_*' -or $Database -notmatch '^[A-Za-z][A-Za-z0-9_]{0,63}$') {
    throw "Refusing: -Database $Database does not start with EchoTest_. The database integration specs only ever run against a throwaway EchoTest_ database. Nothing was changed."
}
foreach ($v in @($SqlUser, $SqlPassword)) {
    if ($v -match '[;{}]') { throw 'A SQL login or password with ; { or } cannot be put in a JDBC URL here. Nothing was changed.' }
}

if (-not $JdbcHost) { $JdbcHost = ($SqlServer -split '\\')[0]; if ($JdbcHost -eq '.' -or $JdbcHost -eq '(local)') { $JdbcHost = 'localhost' } }

if (-not $SkipInstall) {
    Write-Host "Test database: $Database on $SqlServer"
    $installArgs = @{ SqlServer = $SqlServer; Database = $Database }
    if ($InstallSqlUser) { $installArgs.SqlUser = $InstallSqlUser; $installArgs.SqlPassword = $InstallSqlPassword }
    & (Join-Path $PSScriptRoot 'Install-EchoDatabase.ps1') @installArgs
}

$java = Find-EchoJava -JavaHome $JavaHome
$url = "jdbc:sqlserver://${JdbcHost}:$Port;databaseName=$Database;integratedSecurity=false;user=$SqlUser;password=$SqlPassword;encrypt=false"

# Passed through the environment, not -D: cmd.exe (mvnw.cmd) splits an argument at ; and =.
$oldUrl = $env:ECHO_IT_URL
$env:ECHO_IT_URL = $url
try {
    $mavenArgs = @('-q', 'test', '-Pdb-it')
    if ($Test) { $mavenArgs += "-Dtest=$Test"; $mavenArgs += '-Dsurefire.failIfNoSpecifiedTests=false' }
    Write-Host "Running the database integration specs against $Database ($JdbcHost`:$Port)"
    Invoke-EchoMaven -Root $root -JavaHome $java.Home -MavenArgs $mavenArgs
    Write-Host 'Database integration specs passed. Reports: target\surefire-reports'
} finally {
    $env:ECHO_IT_URL = $oldUrl
}
