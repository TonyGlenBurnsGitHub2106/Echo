# The helpers every Echo script shares (Story 5653). Dot-source it: . "$PSScriptRoot\EchoCommon.ps1"

# The Java major version of one JDK / JRE folder, or 0 when it is not one.
function Get-EchoJavaMajor([string]$JavaHome) {
    $exe = Join-Path $JavaHome 'bin\java.exe'
    if (-not (Test-Path $exe)) { return 0 }
    $out = & cmd /c "`"$exe`" -version 2>&1"
    $line = ($out | Select-Object -First 1) -as [string]
    if ($line -notmatch 'version "(\d+)(\.(\d+))?') { return 0 }
    $major = [int]$Matches[1]
    if ($major -eq 1) { $major = [int]$Matches[3] }
    return $major
}

# Finds the NEWEST Java 21 or later on the machine. -JavaHome, when given, is used as is (or refused).
# Otherwise it looks at JAVA_HOME, every java.exe on the PATH, the registry keys the JDK installers
# write, and the usual folders -- including unzipped JDKs in C:\Java, C:\JDK, C:\Tony\Dev\JDK
# (Story 5653: the first run on RENAISSANCE missed its JDK 21, which is unzipped there, not installed).
function Find-EchoJava([string]$JavaHome) {
    if ($JavaHome) {
        $major = Get-EchoJavaMajor $JavaHome
        if ($major -ge 21) { return [pscustomobject]@{ Home = $JavaHome; Exe = (Join-Path $JavaHome 'bin\java.exe'); Major = $major } }
        throw "-JavaHome $JavaHome is not a Java 21 or newer (found version $major). Point it at the folder that holds bin\java.exe."
    }
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    foreach ($exe in @(& where.exe java.exe 2>$null)) { if ($exe) { $candidates += (Split-Path -Parent (Split-Path -Parent $exe)) } }
    foreach ($key in @('HKLM:\SOFTWARE\JavaSoft\JDK', 'HKLM:\SOFTWARE\JavaSoft\Java Development Kit', 'HKLM:\SOFTWARE\JavaSoft\JRE',
                       'HKLM:\SOFTWARE\Eclipse Adoptium\JDK', 'HKLM:\SOFTWARE\Microsoft\JDK', 'HKLM:\SOFTWARE\Azul Systems\Zulu')) {
        if (-not (Test-Path $key)) { continue }
        Get-ChildItem $key -Recurse -ErrorAction SilentlyContinue | ForEach-Object {
            $p = Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue
            foreach ($n in 'JavaHome', 'Path', 'InstallationPath') { if ($p -and $p.$n) { $candidates += $p.$n.TrimEnd('\') } }
        }
    }
    $bases = @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Microsoft",
               "$env:ProgramFiles\Amazon Corretto", "$env:ProgramFiles\Zulu", "$env:ProgramFiles\BellSoft",
               "$env:ProgramFiles\OpenJDK", "$env:LOCALAPPDATA\Programs", "$env:USERPROFILE\.jdks",
               "$env:SystemDrive\Java", "$env:SystemDrive\JDK", "$env:SystemDrive\Tony\Dev\JDK")
    foreach ($base in $bases) {
        if ($base -and (Test-Path $base)) { Get-ChildItem $base -Directory -ErrorAction SilentlyContinue | ForEach-Object { $candidates += $_.FullName } }
    }
    $seen = @()
    $best = $null
    foreach ($jh in ($candidates | Where-Object { $_ } | Select-Object -Unique)) {
        $major = Get-EchoJavaMajor $jh
        if ($major -eq 0) { continue }
        $seen += "$major ($jh)"
        if ($major -ge 21 -and (-not $best -or $major -gt $best.Major)) {
            $best = [pscustomobject]@{ Home = $jh; Exe = (Join-Path $jh 'bin\java.exe'); Major = $major }
        }
    }
    if ($best) { return $best }
    $found = if ($seen) { ' Found only: ' + ($seen -join '; ') + '.' } else { '' }
    throw "Echo needs Java 21 or newer and none was found.$found Install one (for example Eclipse Temurin 21), or pass -JavaHome C:\path\to\jdk."
}

function New-EchoConnection([string]$SqlServer, [string]$Database, [string]$SqlUser, [string]$SqlPassword) {
    $b = New-Object System.Data.SqlClient.SqlConnectionStringBuilder
    $b['Data Source'] = $SqlServer; $b['Initial Catalog'] = $Database; $b['Connect Timeout'] = 15
    if ($SqlUser) { $b['User ID'] = $SqlUser; $b['Password'] = $SqlPassword } else { $b['Integrated Security'] = $true }
    $c = New-Object System.Data.SqlClient.SqlConnection $b.ConnectionString
    $c.Open()
    return $c
}

function Invoke-EchoScalar($conn, [string]$sql) {
    $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.CommandTimeout = 120
    return $cmd.ExecuteScalar()
}

function Invoke-EchoNonQuery($conn, [string]$sql) {
    $cmd = $conn.CreateCommand(); $cmd.CommandText = $sql; $cmd.CommandTimeout = 600
    [void]$cmd.ExecuteNonQuery()
}

# Letters and digits only, so a secret can sit in a properties file or a T-SQL string untouched.
function New-EchoSecret([int]$length) {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] $length
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
}

# .\SQLEXPRESS / MYPC\SQL2019 / localhost,1433  ->  the host part of a JDBC URL.
function ConvertTo-EchoJdbcHost([string]$server) {
    $s = $server.Trim()
    if ($s.StartsWith('.\') -or $s.StartsWith('(local)\')) { $s = 'localhost\' + $s.Substring($s.IndexOf('\') + 1) }
    if ($s -eq '.' -or $s -eq '(local)') { $s = 'localhost' }
    if ($s -match '^(.+)\\(.+)$') { return "$($Matches[1]);instanceName=$($Matches[2])" }
    if ($s -match '^(.+),(\d+)$') { return "$($Matches[1]):$($Matches[2])" }
    return $s
}

# How Java should reach this SQL Server. A named instance is turned into its TCP port when the
# server will say (so the SQL Browser service need not be running): the port its own TCP connections
# use, or else the "Server is listening on" line of its error log. Encryption is on for SQL Server
# 2016 and newer and off for older ones, whose TLS Java 21 refuses.
function Resolve-EchoJdbcServer([string]$SqlServer, [string]$SqlUser, [string]$SqlPassword) {
    $hostPart = ConvertTo-EchoJdbcHost $SqlServer
    $port = $null
    $c = New-EchoConnection -SqlServer $SqlServer -Database 'master' -SqlUser $SqlUser -SqlPassword $SqlPassword
    try {
        $major = [int](([string](Invoke-EchoScalar $c "SELECT CONVERT(VARCHAR(40), SERVERPROPERTY('ProductVersion'))")).Split('.')[0])
        if ($SqlServer.Contains('\')) {
            try {
                $port = Invoke-EchoScalar $c "SELECT TOP 1 local_tcp_port FROM sys.dm_exec_connections WHERE net_transport = 'TCP' AND local_tcp_port IS NOT NULL GROUP BY local_tcp_port ORDER BY COUNT(*) DESC"
            } catch { }
            if (-not $port -or $port -is [DBNull]) {
                try {
                    $cmd = $c.CreateCommand()
                    $cmd.CommandText = "EXEC master.dbo.xp_readerrorlog 0, 1, N'Server is listening on'"
                    $r = $cmd.ExecuteReader()
                    while ($r.Read()) {
                        $text = [string]$r[2]
                        if ($text -match "'(ipv4|any)'\s*<ipv4>\s*(\d+)" -or $text -match '\]:(\d+)\]') { $port = $Matches[$Matches.Count - 1]; break }
                    }
                    $r.Close()
                } catch { }
            }
        }
    } finally { $c.Close() }
    if ($port -and -not ($port -is [DBNull]) -and $SqlServer.Contains('\')) {
        $machine = $SqlServer.Substring(0, $SqlServer.IndexOf('\'))
        if ($machine -eq '.' -or $machine -eq '(local)') { $machine = 'localhost' }
        $hostPart = "${machine}:$port"
    }
    return [pscustomobject]@{ Host = $hostPart; Major = $major; Encrypt = ($major -ge 13) }
}

function Invoke-EchoMaven([string]$Root, [string]$JavaHome, [string[]]$MavenArgs) {
    $old = $env:JAVA_HOME
    $env:JAVA_HOME = $JavaHome
    Push-Location $Root
    try {
        & (Join-Path $Root 'mvnw.cmd') @MavenArgs
        if ($LASTEXITCODE -ne 0) { throw "Maven failed (exit $LASTEXITCODE): mvnw $($MavenArgs -join ' ')" }
    } finally { Pop-Location; $env:JAVA_HOME = $old }
}

function Invoke-EchoBuild([string]$Root, [string]$JavaHome) {
    Write-Host '  building Echo (the first build downloads what it needs; give it a few minutes)'
    Invoke-EchoMaven -Root $Root -JavaHome $JavaHome -MavenArgs @('-q', 'package', '-DskipTests')
    $jar = Join-Path $Root 'target\echo.jar'
    if (-not (Test-Path $jar)) { throw "The build finished but target\echo.jar is not there." }
    Write-Host "  built target\echo.jar"
}

# The mssql-jdbc version inside the built jar, for the matching Windows sign-in DLL.
function Get-EchoMssqlVersion([string]$Root) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead((Join-Path $Root 'target\echo.jar'))
    try {
        $e = $zip.Entries | Where-Object { $_.Name -match '^mssql-jdbc-(\d+\.\d+\.\d+)\.jre\d+\.jar$' } | Select-Object -First 1
        if (-not $e) { throw 'mssql-jdbc is not in target\echo.jar.' }
        return ($e.Name -replace '^mssql-jdbc-(\d+\.\d+\.\d+)\..*$', '$1')
    } finally { $zip.Dispose() }
}

function Read-EchoInstall([string]$Root) {
    $p = Join-Path $Root 'config\echo-install.json'
    if (-not (Test-Path $p)) { throw "Echo is not installed here yet -- run install.cmd first." }
    return Get-Content $p -Raw | ConvertFrom-Json
}

# The java.exe processes running this folder's echo.jar.
function Get-EchoProcess([string]$Root) {
    $jar = (Join-Path $Root 'target\echo.jar').ToLower()
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.ToLower().Contains($jar) }
}
