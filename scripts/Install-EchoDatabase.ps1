<#
.SYNOPSIS
    Creates (or brings up to date) Echo's SQL Server database. Story 5653.

.DESCRIPTION
    Safe to run as often as you like: every script under db\ is idempotent. A table that exists
    is left alone (and its rows with it); every view, function, stored procedure and trigger is
    dropped and created again from the file; seed rows are added only when missing.

    Order: the database itself (created if missing), db\tables.sql, db\tables-app.sql,
    db\functions, db\views, db\procs, db\triggers, db\seed.sql.

    Uses System.Data.SqlClient, which every Windows PowerShell has -- no sqlcmd, no SSMS, no
    module to install.

.PARAMETER SqlServer
    The SQL Server instance, e.g. localhost, .\SQLEXPRESS, MYPC\SQL2019. Left out: the one the last install
    recorded, else the SQL Server running on this computer (Story 5653).

.PARAMETER Database
    Default Echo.

.PARAMETER SqlUser / SqlPassword
    A SQL login allowed to create the database. Leave both out to sign in as you (Windows).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\Install-EchoDatabase.ps1 -SqlServer .\SQLEXPRESS
#>
[CmdletBinding()]
param(
    [string]$SqlServer,
    [string]$Database  = 'Echo',
    [string]$SqlUser,
    [string]$SqlPassword
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$db   = Join-Path $root 'db'
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')
Assert-EchoDatabaseName $Database
if (-not $SqlServer) {
    $SqlServer = Select-EchoSqlServer -Root $root -SqlUser $SqlUser -SqlPassword $SqlPassword
}

function New-EchoConnectionString([string]$catalog) {
    $b = New-Object System.Data.SqlClient.SqlConnectionStringBuilder
    $b['Data Source']     = $SqlServer
    $b['Initial Catalog'] = $catalog
    $b['Connect Timeout'] = 15
    if ($SqlUser) { $b['User ID'] = $SqlUser; $b['Password'] = $SqlPassword }
    else          { $b['Integrated Security'] = $true }
    return $b.ConnectionString
}

function Invoke-EchoBatches([System.Data.SqlClient.SqlConnection]$conn, [string]$path) {
    $text = [IO.File]::ReadAllText($path)
    $batches = [regex]::Split($text, '(?im)^\s*GO\s*$')
    $n = 0
    foreach ($batch in $batches) {
        if ($batch.Trim().Length -eq 0) { continue }
        $cmd = $conn.CreateCommand()
        $cmd.CommandText = $batch
        $cmd.CommandTimeout = 300
        try { [void]$cmd.ExecuteNonQuery() }
        catch { throw "Failed in $([IO.Path]::GetFileName($path)), batch $($n + 1): $($_.Exception.InnerException.Message)$($_.Exception.Message)" }
        $n++
    }
    return $n
}

Write-Host "Echo database: $Database on $SqlServer"

# 1. The database itself.
$master = New-Object System.Data.SqlClient.SqlConnection (New-EchoConnectionString 'master')
$master.Open()
try {
    $cmd = $master.CreateCommand()
    $cmd.CommandText = "SELECT DB_ID(@n)"
    [void]$cmd.Parameters.AddWithValue('@n', $Database)
    $exists = $cmd.ExecuteScalar()
    if ($exists -is [DBNull] -or $exists -eq $null) {
        if ($Database -notmatch '^[A-Za-z][A-Za-z0-9_]{0,63}$') { throw "A database name is letters, digits and underscores." }
        Write-Host "  creating database $Database"
        $cmd = $master.CreateCommand()
        # A fixed, case-insensitive collation: Echo's SQL is written for one, whatever the server's default.
        $cmd.CommandText = "CREATE DATABASE [$Database] COLLATE SQL_Latin1_General_CP1_CI_AS"
        [void]$cmd.ExecuteNonQuery()
        # Story 5653: marked as Echo's before any table goes in, so a run that fails halfway can be
        # re-run or uninstalled (both test for this mark or AppUser).
        Set-EchoDatabaseMark $master $Database
    } else {
        # Story 5653: bring up to date only an Echo database (or an empty one). Echo's procedures carry
        # Leonardo's names, so run into Leonardo -- or any other app's database -- they would replace its own.
        $tables = [int](Invoke-EchoScalar $master "SELECT COUNT(*) FROM [$Database].sys.tables WHERE is_ms_shipped = 0")
        if ($tables -gt 0 -and -not (Test-EchoIsEchoDatabase $master $Database)) {
            throw "The database $Database on $SqlServer already holds another program's tables, so it is not an Echo database. Nothing was changed. Choose another name with -Database."
        }
        Set-EchoDatabaseMark $master $Database   # an Echo database made before the mark existed gets it now
        Write-Host "  database exists -- bringing it up to date"
    }
} finally { $master.Close() }

# 2. Everything in it.
$conn = New-Object System.Data.SqlClient.SqlConnection (New-EchoConnectionString $Database)
$conn.Open()
try {
    $plan = @()
    $plan += Get-Item (Join-Path $db 'tables.sql')
    $plan += Get-Item (Join-Path $db 'tables-app.sql')
    foreach ($folder in 'functions', 'views', 'procs', 'triggers') {
        $plan += Get-ChildItem (Join-Path $db $folder) -Filter *.sql | Sort-Object Name
    }
    $plan += Get-Item (Join-Path $db 'seed.sql')
    $count = 0
    foreach ($f in $plan) {
        $count += Invoke-EchoBatches $conn $f.FullName
    }
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = "SELECT (SELECT COUNT(*) FROM sys.tables) AS T, (SELECT COUNT(*) FROM sys.procedures) AS P, (SELECT COUNT(*) FROM sys.triggers) AS R"
    $r = $cmd.ExecuteReader(); [void]$r.Read()
    Write-Host ("  ran {0} files ({1} batches): {2} tables, {3} procedures, {4} triggers" -f $plan.Count, $count, $r['T'], $r['P'], $r['R'])
    $r.Close()
} finally { $conn.Close() }

Write-Host "Echo database is ready."
