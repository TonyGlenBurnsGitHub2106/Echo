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
# Every folder BESIDE a Java it finds is looked at too (Story 5653: at work JAVA_HOME named a Zulu 17
# in C:\dev\SNF\java and the Temurin 25 unzipped next to it was never seen). Echo is built from
# source, so it must be a JDK (bin\javac.exe), not a JRE.
function Find-EchoJava([string]$JavaHome) {
    if ($JavaHome) {
        $JavaHome = $JavaHome.TrimEnd('\')
        $major = Get-EchoJavaMajor $JavaHome
        if ($major -lt 21) { throw "-JavaHome $JavaHome is not a Java 21 or newer (found version $major). Point it at the folder that holds bin\java.exe." }
        if (-not (Test-Path (Join-Path $JavaHome 'bin\javac.exe'))) { throw "-JavaHome $JavaHome is a Java $major runtime (JRE) without bin\javac.exe; Echo is built from source and needs a JDK." }
        return [pscustomobject]@{ Home = $JavaHome; Exe = (Join-Path $JavaHome 'bin\java.exe'); Major = $major }
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
    # The neighbors of every Java found so far: JDKs tend to be unzipped side by side.
    $parents = @($candidates | Where-Object { $_ } | ForEach-Object { Split-Path -Parent $_.TrimEnd('\') } | Where-Object { $_ } | Select-Object -Unique)
    foreach ($parent in $parents) {
        if (Test-Path $parent) { Get-ChildItem $parent -Directory -ErrorAction SilentlyContinue | ForEach-Object { $candidates += $_.FullName } }
    }
    $seen = @()
    $best = $null
    foreach ($jh in ($candidates | Where-Object { $_ } | ForEach-Object { $_.TrimEnd('\') } | Sort-Object -Unique)) {
        $major = Get-EchoJavaMajor $jh
        if ($major -eq 0) { continue }
        $isJdk = Test-Path (Join-Path $jh 'bin\javac.exe')
        $seen += "$major$(if (-not $isJdk) { ' runtime only, no javac' }) ($jh)"
        if ($major -ge 21 -and $isJdk -and (-not $best -or $major -gt $best.Major)) {
            $best = [pscustomobject]@{ Home = $jh; Exe = (Join-Path $jh 'bin\java.exe'); Major = $major }
        }
    }
    if ($best) { return $best }
    $found = if ($seen) { ' Found only: ' + ($seen -join '; ') + '.' } else { '' }
    # Story 5653: when every search comes up empty, ask for the folder rather than stop, as long as
    # someone is there to answer. Enter on its own cancels.
    if ([Environment]::UserInteractive -and -not [Console]::IsInputRedirected) {
        Write-Host ''
        Write-Host '  No Java 21 or newer JDK was found on this computer.' -ForegroundColor Yellow
        foreach ($s in $seen) { Write-Host "    found: $s" }
        while ($true) {
            $typed = Read-Host '  Type the folder of a Java 21+ JDK (the one that holds bin\javac.exe), or press Enter to cancel'
            $typed = ([string]$typed).Trim().Trim('"').TrimEnd('\')
            if (-not $typed) { break }
            if (-not (Test-Path (Join-Path $typed 'bin\java.exe'))) { Write-Host "  $typed has no bin\java.exe. Try again." -ForegroundColor Yellow; continue }
            $major = Get-EchoJavaMajor $typed
            if ($major -lt 21) { Write-Host "  $typed is Java $major; Echo needs 21 or newer. Try again." -ForegroundColor Yellow; continue }
            if (-not (Test-Path (Join-Path $typed 'bin\javac.exe'))) { Write-Host "  $typed is a Java $major runtime without bin\javac.exe; Echo needs a JDK. Try again." -ForegroundColor Yellow; continue }
            return [pscustomobject]@{ Home = $typed; Exe = (Join-Path $typed 'bin\java.exe'); Major = $major }
        }
    }
    throw "Echo needs a Java 21 or newer JDK and none was found.$found Install one (for example Eclipse Temurin 21 JDK), or pass -JavaHome C:\path\to\jdk (the folder that holds bin\javac.exe)."
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
    } elseif ($SqlServer.Contains('\')) {
        # Story 5653: Java reaches SQL Server only over TCP/IP. With no port to name, it needs the SQL
        # Browser service -- say so plainly here rather than let Echo fail to start later.
        # The installer has already switched TCP/IP on (Enable-EchoSqlTcp); this is the backstop.
        $local = Get-EchoLocalInstanceFor $SqlServer
        if ($local -and $local.Tcp -eq $false) {
            Enable-EchoSqlTcp $SqlServer $SqlUser $SqlPassword
            return Resolve-EchoJdbcServer $SqlServer $SqlUser $SqlPassword
        }
        $browser = Get-Service -Name 'SQLBrowser' -ErrorAction SilentlyContinue
        if (-not $browser -or $browser.Status -ne 'Running') {
            Write-Warning "Could not learn the TCP port of $SqlServer and the SQL Browser service is not running; Echo may not be able to connect. If it fails to start, give the instance a fixed TCP port in SQL Server Configuration Manager and run install.cmd -Reconfigure."
        }
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
    Invoke-EchoMaven -Root $Root -JavaHome $JavaHome -MavenArgs @('-q', 'package', '-Dmaven.test.skip=true')   # Story 5653: the Spock specs are neither compiled nor run on install or update
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

# The SQL Server instances installed on THIS computer (registry), each with its service state and
# whether TCP/IP is switched on. Name MSSQLSERVER is the default instance (Server = localhost).
function Get-EchoLocalSqlInstances {
    $list = @()
    foreach ($key in 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\Instance Names\SQL',
                     'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Microsoft SQL Server\Instance Names\SQL') {
        if (-not (Test-Path $key)) { continue }
        $p = Get-ItemProperty $key
        foreach ($prop in $p.PSObject.Properties) {
            if ($prop.Name -like 'PS*') { continue }
            $name = $prop.Name
            if ($list | Where-Object { $_.Name -eq $name }) { continue }
            $svcName = if ($name -eq 'MSSQLSERVER') { 'MSSQLSERVER' } else { "MSSQL`$$name" }
            $svc = Get-Service -Name $svcName -ErrorAction SilentlyContinue
            $tcp = $null; $tcpKey = $null
            foreach ($base in 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server', 'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Microsoft SQL Server') {
                $t = Join-Path $base "$($prop.Value)\MSSQLServer\SuperSocketNetLib\Tcp"
                if (Test-Path $t) { $tcpKey = $t; $tcp = ((Get-ItemProperty $t -ErrorAction SilentlyContinue).Enabled -eq 1); break }
            }
            $list += [pscustomobject]@{
                Name    = $name
                Id      = $prop.Value
                Server  = $(if ($name -eq 'MSSQLSERVER') { 'localhost' } else { "$env:COMPUTERNAME\$name" })
                Service = $svcName
                Running = [bool]($svc -and $svc.Status -eq 'Running')
                Tcp     = $tcp
                TcpKey  = $tcpKey
            }
        }
    }
    return $list
}

# Story 5653: the installer runs as administrator (it may start SQL Server, switch its TCP/IP on and
# restart it). True when this PowerShell has administrator rights.
function Test-EchoAdmin {
    $p = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    return $p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

# Stops the script (exit 1) with what to do when this PowerShell is not running as administrator.
function Assert-EchoAdmin([string]$What = 'install.cmd',
                          [string]$Why = 'It may need to switch SQL Server''s TCP/IP on and restart SQL Server, which only an administrator can do.') {
    if (Test-EchoAdmin) { return }
    Write-Host ''
    Write-Host "$What must be run as administrator." -ForegroundColor Red
    Write-Host "  $Why"
    Write-Host '  Nothing was changed. Do one of these, then run it again:'
    Write-Host "    - right-click $What and choose 'Run as administrator', or"
    Write-Host "    - Start menu -> type cmd -> right-click Command Prompt -> 'Run as administrator', then cd to the Echo folder and run $What."
    Write-Host ''
    exit 1
}

# Story 5653: Echo's database is never a system database nor another program of the house's --
# Echo's procedures carry Leonardo's names, and an uninstall drops the database.
function Assert-EchoDatabaseName([string]$Database) {
    if ($Database -notmatch '^[A-Za-z][A-Za-z0-9_]{0,63}$') { throw "A database name is letters, digits and underscores." }
    if (@('master', 'model', 'msdb', 'tempdb', 'Leonardo', 'Hearth') -contains $Database) {
        throw "$Database is not an Echo database and can never be used as one. Nothing was changed."
    }
}

# Story 5653: is this database Echo's? 1 when it has Echo's sign-in table (AppUser, or EchoUser from the
# first build) or the EchoDatabase mark the installer puts on a database the moment it creates it --
# so a first install that failed halfway (tables but no AppUser yet) can still be finished or
# uninstalled. Leonardo and Hearth have neither. $conn is a connection to master.
function Test-EchoIsEchoDatabase($conn, [string]$Database) {
    return [int](Invoke-EchoScalar $conn ("SELECT CASE WHEN OBJECT_ID(N'[$Database].dbo.AppUser', 'U') IS NOT NULL " +
        "OR OBJECT_ID(N'[$Database].dbo.EchoUser', 'U') IS NOT NULL " +
        "OR EXISTS (SELECT 1 FROM [$Database].sys.extended_properties WHERE class = 0 AND name = N'EchoDatabase') THEN 1 ELSE 0 END")) -eq 1
}

# Story 5653: marks a database as Echo's (see Test-EchoIsEchoDatabase). Safe to call again.
function Set-EchoDatabaseMark($conn, [string]$Database) {
    Invoke-EchoNonQuery $conn ("IF NOT EXISTS (SELECT 1 FROM [$Database].sys.extended_properties WHERE class = 0 AND name = N'EchoDatabase') " +
        "EXEC [$Database].sys.sp_addextendedproperty @name = N'EchoDatabase', @value = N'Echo (Story 5653)'")
}

# Story 5653: the account signed in to this desktop (DOMAIN\name), which is who Echo runs as -- the
# installer starts it through explorer.exe. Not an elevating administrator's account. $null if unknown.
function Get-EchoDesktopAccount {
    try {
        $sessionId = (Get-Process -Id $PID).SessionId
        $shell = Get-CimInstance Win32_Process -Filter "Name = 'explorer.exe'" | Where-Object { $_.SessionId -eq $sessionId } | Select-Object -First 1
        if (-not $shell) { return [Security.Principal.WindowsIdentity]::GetCurrent().Name }
        $o = Invoke-CimMethod -InputObject $shell -MethodName GetOwner
        if ($o.User) { return "$($o.Domain)\$($o.User)" }
    } catch { }
    return $null
}

# The folder Windows runs things from at sign-in, for the person signed in to this desktop -- not for
# a separate administrator account the installer may have been elevated with (Story 5653).
function Get-EchoStartupFolder {
    $mine = [Environment]::GetFolderPath('Startup')
    try {
        $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        $sessionId = (Get-Process -Id $PID).SessionId
        $shell = Get-CimInstance Win32_Process -Filter "Name = 'explorer.exe'" | Where-Object { $_.SessionId -eq $sessionId } | Select-Object -First 1
        if (-not $shell) { return $mine }
        $owner = (Invoke-CimMethod -InputObject $shell -MethodName GetOwnerSid).Sid
        if (-not $owner -or $owner -eq $sid) { return $mine }
        $profilePath = (Get-CimInstance Win32_UserProfile -Filter "SID = '$owner'").LocalPath
        if (-not $profilePath) { return $mine }
        return (Join-Path $profilePath 'AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup')
    } catch { return $mine }
}

# The SQL Server instance on THIS computer that a server name means, or $null (another computer).
function Get-EchoLocalInstanceFor([string]$SqlServer) {
    $s = $SqlServer.Trim()
    $machine = $s; $inst = 'MSSQLSERVER'
    if ($s.Contains('\')) { $machine = $s.Substring(0, $s.IndexOf('\')); $inst = $s.Substring($s.IndexOf('\') + 1) }
    elseif ($s.Contains(',')) { return $null }
    if (@('.', '(local)', 'localhost', '127.0.0.1', $env:COMPUTERNAME) -notcontains $machine) { return $null }
    return Get-EchoLocalSqlInstances | Where-Object { $_.Name -eq $inst } | Select-Object -First 1
}

# Waits until SQL Server answers a sign-in (after a start or restart). Throws after $Seconds.
function Wait-EchoSqlServer([string]$SqlServer, [string]$SqlUser, [string]$SqlPassword, [int]$Seconds = 90) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    $err = $null
    while ((Get-Date) -lt $deadline) {
        $err = Test-EchoSqlServer $SqlServer $SqlUser $SqlPassword
        if (-not $err) { return }
        Start-Sleep -Seconds 2
    }
    throw "SQL Server $SqlServer did not answer within $Seconds seconds: $err"
}

# Story 5653: Java (Echo) reaches SQL Server only over TCP/IP, which SQL Server Express ships with
# switched OFF. On this computer, as administrator: switch it on (the same setting SQL Server
# Configuration Manager changes) and restart the instance so it takes effect. Another computer's
# SQL Server is left alone.
function Enable-EchoSqlTcp([string]$SqlServer, [string]$SqlUser, [string]$SqlPassword, [switch]$Confirmed) {
    $i = Get-EchoLocalInstanceFor $SqlServer
    if (-not $i -or $i.Tcp -ne $false) { return }
    if (-not $i.TcpKey) { throw "SQL Server $SqlServer has TCP/IP switched off and its setting could not be found. Switch it on in SQL Server Configuration Manager, restart it, and run install.cmd again." }
    Assert-EchoAdmin
    # Story 5653: a restart drops every program connected to this SQL Server (at work it may serve
    # others), so it is asked for first -- or -RestartSqlServer says yes ahead of time.
    if (-not $Confirmed) {
        $others = '?'
        try {
            $c = New-EchoConnection -SqlServer $SqlServer -Database 'master' -SqlUser $SqlUser -SqlPassword $SqlPassword
            try { $others = [int](Invoke-EchoScalar $c 'SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE is_user_process = 1 AND session_id <> @@SPID') } finally { $c.Close() }
        } catch { }
        Write-Host ''
        Write-Host "  SQL Server $SqlServer has TCP/IP switched off. Echo needs it on, and switching it on means" -ForegroundColor Yellow
        Write-Host "  restarting SQL Server: the $others other connection(s) to it now are dropped (their programs reconnect)." -ForegroundColor Yellow
        $answer = Read-Host '  Restart it now? Type Y for yes'
        if ($answer -notmatch '^(y|yes)$') {
            throw "SQL Server was not restarted, so Echo cannot reach it yet. Nothing was changed.`nRun install.cmd again when a restart is all right (add -RestartSqlServer to skip this question), or switch TCP/IP on yourself in SQL Server Configuration Manager and restart it."
        }
    }
    Write-Host "  SQL Server $SqlServer has TCP/IP switched off; Echo needs it -- switching it on"
    Set-ItemProperty -Path $i.TcpKey -Name Enabled -Value 1
    Write-Host "  restarting SQL Server $SqlServer so the change takes effect (programs using it reconnect)"
    # -Force stops the services that depend on it (SQL Server Agent, Full-Text) but never starts them
    # again -- so note which were running and start those afterwards.
    $dependents = @(Get-Service -Name $i.Service).DependentServices | Where-Object { $_.Status -eq 'Running' }
    Restart-Service -Name $i.Service -Force
    foreach ($d in $dependents) {
        Write-Host "  starting $($d.DisplayName) again"
        Start-Service -Name $d.Name
    }
    Wait-EchoSqlServer $SqlServer $SqlUser $SqlPassword
    Write-Host "  TCP/IP is on for $SqlServer"
}

# Can we sign in to this SQL Server? Returns $null when yes, else the error text.
function Test-EchoSqlServer([string]$SqlServer, [string]$SqlUser, [string]$SqlPassword) {
    try { $c = New-EchoConnection -SqlServer $SqlServer -Database 'master' -SqlUser $SqlUser -SqlPassword $SqlPassword; $c.Close(); return $null }
    catch { return $_.Exception.InnerException.Message + $(if (-not $_.Exception.InnerException) { $_.Exception.Message }) }
}

# Which SQL Server Echo uses (Story 5653: the first install at home failed because it assumed
# "localhost", and this computer has only named instances). In order: -SqlServer when given; the
# one the last install recorded; otherwise the RUNNING instances on this computer that answer -- one
# is used as is, several are offered as a numbered choice. A stopped instance is never chosen or
# started (Tony, 2026-10-22: a machine may have many instances; the database goes on a running one).
# Stopped instances and ones that refuse the sign-in are named in the error, with what to do.
function Select-EchoSqlServer([string]$SqlServer, [string]$Root, [string]$SqlUser, [string]$SqlPassword) {
    $instances = @(Get-EchoLocalSqlInstances)
    $describe = {
        if ($instances.Count -eq 0) { return '  No SQL Server is installed on this computer. Install SQL Server Express, or name a server on another computer with -SqlServer.' }
        ($instances | ForEach-Object {
            $state = if ($_.Running) { 'running' } else { "stopped -- not used (to use it, start the service '$($_.Service)' first)" }
            "  $($_.Server) -- $state"
        }) -join "`n"
    }
    if ($SqlServer) {
        $err = Test-EchoSqlServer $SqlServer $SqlUser $SqlPassword
        if ($err) { throw "Echo cannot reach SQL Server '$SqlServer': $err`nSQL Servers on this computer:`n$(& $describe)`nRun install.cmd again with -SqlServer naming one of them (or leave -SqlServer off to choose)." }
        return $SqlServer
    }
    $installJson = Join-Path $Root 'config\echo-install.json'
    if (Test-Path $installJson) {
        $prev = (Get-Content $installJson -Raw | ConvertFrom-Json).sqlServer
        $prevLocal = if ($prev) { Get-EchoLocalInstanceFor $prev } else { $null }
        if ($prev) {
            # Story 5653: Echo's database and its kept settings are on $prev. If it is down, never
            # move on to another server -- that would build a second, empty Echo there while the kept
            # settings still point at the first.
            if ($prevLocal -and -not $prevLocal.Running) {
                throw "The last install put Echo's database on $prev, and that SQL Server is stopped. Nothing was changed.`nStart its service '$($prevLocal.Service)' (Services, or: net start `"$($prevLocal.Service)`") and run install.cmd again.`nTo move Echo to another SQL Server instead, run uninstall-echo.cmd first, then install.cmd."
            }
            $err = Test-EchoSqlServer $prev $SqlUser $SqlPassword
            if ($err) {
                throw "The last install put Echo's database on $prev, and it cannot be reached now: $err`nNothing was changed. Make sure that SQL Server is running and run install.cmd again.`nTo move Echo to another SQL Server instead, run uninstall-echo.cmd first, then install.cmd."
            }
            Write-Host "  using $prev (from the last install)"
            return $prev
        }
    }
    $ok = @(); $failed = @()
    foreach ($i in ($instances | Where-Object { $_.Running })) {
        $err = Test-EchoSqlServer $i.Server $SqlUser $SqlPassword
        if ($err) { $failed += "  $($i.Server) -- would not let you in: $err" } else { $ok += $i }
    }
    if ($ok.Count -eq 1) {
        Write-Host "  found SQL Server $($ok[0].Server) on this computer"
        return $ok[0].Server
    }
    if ($ok.Count -gt 1) {
        Write-Host '  More than one SQL Server is running on this computer. Which one should hold the Echo database?'
        for ($n = 0; $n -lt $ok.Count; $n++) { Write-Host ("    {0}. {1}" -f ($n + 1), $ok[$n].Server) }
        # A few tries only: with no keyboard (a scripted run) Read-Host answers empty forever.
        for ($try = 0; $try -lt 5; $try++) {
            $answer = Read-Host "  Type a number (1-$($ok.Count))"
            $k = 0
            if ([int]::TryParse($answer, [ref]$k) -and $k -ge 1 -and $k -le $ok.Count) { return $ok[$k - 1].Server }
        }
        throw "No SQL Server was chosen. Run it again and type a number, or name one with -SqlServer (for example -SqlServer $($ok[0].Server))."
    }
    $why = if ($failed) { "`n" + ($failed -join "`n") } else { '' }
    throw "Echo found no running SQL Server it can use on this computer.`n$(& $describe)$why`nStart one, or name a server with -SqlServer (and -AdminSqlUser / -AdminSqlPassword if Windows sign-in is not allowed)."
}
