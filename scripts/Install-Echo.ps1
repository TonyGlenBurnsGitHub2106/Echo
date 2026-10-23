<#
.SYNOPSIS
    Installs Echo on this computer, start to finish. Story 5653.

.DESCRIPTION
    Run it from the folder git cloned Echo into (install.cmd does that for you), AS ADMINISTRATOR
    (right-click install.cmd -> Run as administrator). It is safe to run again: every step checks
    what is already there.

      0. Check    stops at once, changing nothing, when not run as administrator.
      1. Java     finds the newest Java 21 or later (-JavaHome, else JAVA_HOME, the PATH, the
                 registry and the usual JDK folders, unzipped ones included).
      2. Database -SqlServer, else the one the last install used, else the SQL Server running on
                  this computer (several: it asks which; a stopped one is never used). When that
                  SQL Server's TCP/IP is off (Express ships with it off; Java connects only over
                  TCP/IP), it asks before switching it on and restarting SQL Server
                  (-RestartSqlServer answers yes ahead of time). Then creates or updates the Echo
                  database (scripts\Install-EchoDatabase.ps1).
      3. Login    -Auth SqlLogin (default): a SQL login "echo_app" that can only read, write and run
                  Echo's procedures, with a long random password nobody types.
                  -Auth Windows: Echo signs in to SQL Server as the Windows account it runs under
                  (needs lib\mssql-jdbc_auth-*.dll, which this step downloads); that account, the
                  one signed in to this desktop, is given the same rights in the Echo database.
      4. Config   config\application-local.properties -- the connection, a fresh random token
                  secret and service key. Written once; -Reconfigure writes it again.
      5. Build    mvnw package (downloads what Maven needs the first time). -SkipBuild skips it.
      6. Sign-in  asks for your Echo password (scripts\Set-EchoPassword.ps1). -SkipUser skips it.
      7. Start    starts Echo in the background and waits for it to answer. -NoStart skips it.
                  -AutoStart also starts Echo every time you sign in to Windows.

    Run as administrator (switching TCP/IP on and restarting SQL Server need it). If you do not
    administer the SQL Server itself, pass -AdminSqlUser / -AdminSqlPassword for a SQL account that
    may create a database and a login. Echo answers only on this computer (127.0.0.1) unless you say -Bind 0.0.0.0.

.EXAMPLE
    install.cmd
.EXAMPLE
    install.cmd -SqlServer .\SQLEXPRESS
.EXAMPLE
    install.cmd -SqlServer MYPC\SQL2019 -Auth Windows -AutoStart
#>
[CmdletBinding()]
param(
    # Story 5653: no default -- left out, the installer finds the SQL Server on this computer.
    [string]$SqlServer,
    [string]$Database  = 'Echo',
    [ValidateSet('SqlLogin', 'Windows')]
    [string]$Auth = 'SqlLogin',
    [string]$AdminSqlUser,
    [string]$AdminSqlPassword,
    [int]$Port = 8770,
    [string]$Bind = '127.0.0.1',
    [string]$JavaHome,
    [string]$Username = 'tony',
    # Story 5653: for a scripted install; left out, the installer asks (the usual way).
    [SecureString]$Password,
    [switch]$SkipUser,
    [switch]$SkipBuild,
    [switch]$Reconfigure,
    [switch]$NoStart,
    [switch]$AutoStart,
    # Story 5653: yes, ahead of time, to restarting SQL Server if its TCP/IP must be switched on.
    [switch]$RestartSqlServer
)

$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$config  = Join-Path $root 'config'
$local   = Join-Path $config 'application-local.properties'
$install = Join-Path $config 'echo-install.json'
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')

function Step([string]$text) { Write-Host ''; Write-Host "== $text" -ForegroundColor Cyan }

# ---- 0. Preflight -----------------------------------------------------------------------------
# Story 5653: an installer may change the machine (SQL Server's TCP/IP, a restart), so it runs as
# administrator -- checked before anything is touched.
Step 'Administrator'
Assert-EchoAdmin
Write-Host '  running as administrator'
Assert-EchoDatabaseName $Database

# Story 5653: every install leaves a written record (logs\install-<stamp>.log; passwords typed at the
# prompt are never in it), so "it never asked me for a password" can be settled from the log.
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force $logDir | Out-Null
$installLog = Join-Path $logDir ('install-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
# The log's header records the whole command line, so a run given -AdminSqlPassword writes no log --
# that password must never land in a file.
$logging = -not $AdminSqlPassword
if ($logging) {
    Start-Transcript -Path $installLog | Out-Null
    Write-Host "  this run is written to $installLog"
} else {
    Write-Host '  no log is written for this run (-AdminSqlPassword is on the command line, and a log would record it)'
}
function Stop-EchoInstallLog { if ($logging) { Stop-Transcript | Out-Null } }

# Story 5653: a re-run over a finished install keeps the settings and the sign-in, so it asks for no
# password -- say so up front, so a re-run is never mistaken for a fresh install.
if (Test-Path $local) {
    $since = (Get-Item $local).LastWriteTime.ToString('yyyy-MM-dd HH:mm')
    Write-Host ''
    Write-Host "  Echo is already installed here (settings from $since). This run UPDATES it:" -ForegroundColor Yellow
    if ($Reconfigure) {
        # Story 5653: -Reconfigure rewrites the settings -- say what that does, not that they are kept.
        Write-Host '  -Reconfigure writes new settings: a new token secret (every browser is signed out),' -ForegroundColor Yellow
        Write-Host '  a new service key (agents using the old key stop working) and a new echo_app password.' -ForegroundColor Yellow
        Write-Host '  Your sign-in is kept, so it will not ask for a password.' -ForegroundColor Yellow
    } else {
        Write-Host '  your settings and sign-in are kept, so it will not ask for a password.' -ForegroundColor Yellow
    }
    Write-Host '  To install from scratch, run uninstall-echo.cmd first.' -ForegroundColor Yellow
}

# ---- 1. Java ----------------------------------------------------------------------------------
Step 'Java'
$java = Find-EchoJava -JavaHome $JavaHome
Write-Host "  using $($java.Home) (Java $($java.Major))"

# ---- 2. Database ------------------------------------------------------------------------------
Step 'SQL Server'
# Story 5653: found, not assumed -- the first install at home failed on a "localhost" default.
$SqlServer = Select-EchoSqlServer -SqlServer $SqlServer -Root $root -SqlUser $AdminSqlUser -SqlPassword $AdminSqlPassword
Write-Host "  Echo's database will live on $SqlServer"
Enable-EchoSqlTcp $SqlServer $AdminSqlUser $AdminSqlPassword -Confirmed:$RestartSqlServer

Step 'Database'
$dbArgs = @{ SqlServer = $SqlServer; Database = $Database }
if ($AdminSqlUser) { $dbArgs.SqlUser = $AdminSqlUser; $dbArgs.SqlPassword = $AdminSqlPassword }
& (Join-Path $PSScriptRoot 'Install-EchoDatabase.ps1') @dbArgs

# ---- 3 + 4. Login and config ------------------------------------------------------------------
Step 'Connection and keys'
New-Item -ItemType Directory -Force $config | Out-Null
if ((Test-Path $local) -and -not $Reconfigure) {
    Write-Host "  config\application-local.properties already exists -- kept (use -Reconfigure to write it again)"
    # Story 5653: the kept file is what Echo runs with, so its port, address and sign-in are what
    # echo-install.json records and what the start step waits on -- not this run's defaults.
    $kept = Get-Content $local
    foreach ($l in $kept) {
        if ($l -match '^echo\.port=(\d+)') { $Port = [int]$Matches[1] }
        if ($l -match '^echo\.bind=(.+)$') { $Bind = $Matches[1].Trim() }
        if ($l -match '^spring\.datasource\.url=') {
            $Auth = if ($l -match 'integratedSecurity=true') { 'Windows' } else { 'SqlLogin' }
            if ($l -match 'databaseName=([^;]+)' -and $Matches[1] -ne $Database) {
                Write-Warning "The kept settings point Echo at database $($Matches[1]), not $Database. Run install.cmd -Reconfigure to point it at $Database."
            }
        }
    }
} else {
    # Story 5653: a running Echo holds the old settings (and, after the ALTER LOGIN below, a stale
    # echo_app password), so it is stopped before they change -- with or without -SkipBuild. The
    # Start step starts it again with the new ones.
    if (@(Get-EchoProcess $root).Count -gt 0) { & (Join-Path $PSScriptRoot 'Stop-Echo.ps1') }
    $adminConn = New-EchoConnection -SqlServer $SqlServer -Database 'master' -SqlUser $AdminSqlUser -SqlPassword $AdminSqlPassword
    try {
        $integratedOnly = [int](Invoke-EchoScalar $adminConn "SELECT CONVERT(INT, SERVERPROPERTY('IsIntegratedSecurityOnly'))")
    } finally { $adminConn.Close() }
    if ($Auth -eq 'SqlLogin' -and $integratedOnly -eq 1) {
        Write-Warning "This SQL Server accepts Windows sign-ins only, so Echo will use -Auth Windows."
        $Auth = 'Windows'
    }
    $where = Resolve-EchoJdbcServer -SqlServer $SqlServer -SqlUser $AdminSqlUser -SqlPassword $AdminSqlPassword
    Write-Host "  Echo will reach SQL Server at $($where.Host) (SQL Server $($where.Major); encryption $(if ($where.Encrypt) { 'on' } else { 'off -- this version cannot do the TLS Java 21 needs' }))"
    $jdbcBase = "jdbc:sqlserver://$($where.Host);databaseName=$Database;encrypt=$($where.Encrypt.ToString().ToLower());trustServerCertificate=true"
    $lines = @(
        '# Echo -- this computer''s settings. Written by scripts\Install-Echo.ps1. NEVER commit this file.',
        "# Written $(Get-Date -Format 'yyyy-MM-dd HH:mm') for $SqlServer / $Database."
    )
    if ($Auth -eq 'SqlLogin') {
        $appPassword = New-EchoSecret 24
        $c = New-EchoConnection -SqlServer $SqlServer -Database $Database -SqlUser $AdminSqlUser -SqlPassword $AdminSqlPassword
        try {
            $sql = @"
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'echo_app')
    EXEC ('CREATE LOGIN echo_app WITH PASSWORD = ''$appPassword'', DEFAULT_DATABASE = [$Database], CHECK_EXPIRATION = OFF')
ELSE
    EXEC ('ALTER LOGIN echo_app WITH PASSWORD = ''$appPassword''')
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'echo_app')
    CREATE USER echo_app FOR LOGIN echo_app
EXEC sp_addrolemember 'db_datareader', 'echo_app'
EXEC sp_addrolemember 'db_datawriter', 'echo_app'
GRANT EXECUTE TO echo_app
"@
            Invoke-EchoNonQuery $c $sql
        } finally { $c.Close() }
        Write-Host "  SQL login echo_app is ready (it can read, write and run Echo's procedures, nothing else)"
        $lines += "spring.datasource.url=$jdbcBase"
        $lines += 'spring.datasource.username=echo_app'
        $lines += "spring.datasource.password=$appPassword"
    } else {
        $lines += "spring.datasource.url=$jdbcBase;integratedSecurity=true"
        Write-Host "  Windows sign-in: Echo connects as the Windows account that runs it (the one signed in to this desktop)"
    }
    $lines += "echo.jwt.secret=$(New-EchoSecret 48)"
    $lines += "echo.service.key=$(New-EchoSecret 32)"
    $lines += "echo.port=$Port"
    $lines += "echo.bind=$Bind"
    [IO.File]::WriteAllLines($local, $lines)
    Write-Host "  wrote config\application-local.properties (new token secret and service key)"
}
if ($Auth -eq 'Windows') {
    # Story 5653: Echo runs as the person signed in to this desktop WITHOUT administrator rights (the
    # start step goes through explorer.exe), so a sign-in that works only through the local
    # Administrators group would fail. Give that account its own login and the same rights echo_app gets.
    $acct = Get-EchoDesktopAccount
    if (-not $acct) {
        Write-Warning "Could not tell which Windows account Echo will run as; if Echo cannot reach its database, give that account access to $Database."
    } else {
        $q = $acct -replace "'", "''"
        $b = $acct -replace '\]', ']]'
        $c = New-EchoConnection -SqlServer $SqlServer -Database $Database -SqlUser $AdminSqlUser -SqlPassword $AdminSqlPassword
        try {
            # A login this install makes is recorded, so uninstall-echo.cmd takes it off again; one that
            # was already there is left alone.
            $hadLogin = [int](Invoke-EchoScalar $c "SELECT COUNT(*) FROM sys.server_principals WHERE sid = SUSER_SID(N'$q')") -gt 0
            if (-not $hadLogin) { $windowsLogin = $acct }
            Invoke-EchoNonQuery $c @"
IF SUSER_SID(N'$q') IS NULL OR NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE sid = SUSER_SID(N'$q'))
    CREATE LOGIN [$b] FROM WINDOWS WITH DEFAULT_DATABASE = [$Database]
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE sid = SUSER_SID(N'$q'))
BEGIN
    CREATE USER [$b] FOR LOGIN [$b]
    EXEC sp_addrolemember 'db_datareader', N'$q'
    EXEC sp_addrolemember 'db_datawriter', N'$q'
    GRANT EXECUTE TO [$b]
END
"@
        } finally { $c.Close() }
        Write-Host "  Windows sign-in: $acct can read, write and run Echo's procedures in $Database"
    }
}

$installInfo = [ordered]@{
    javaHome  = $java.Home
    sqlServer = $SqlServer
    database  = $Database
    auth      = $Auth
    port      = $Port
    bind      = $Bind
    installed = (Get-Date -Format 'yyyy-MM-dd HH:mm')
}
# Story 5653: the Windows login an earlier run made stays recorded across re-runs.
if (-not $windowsLogin -and (Test-Path $install)) { $windowsLogin = (Get-Content $install -Raw | ConvertFrom-Json).windowsLogin }
if ($windowsLogin) { $installInfo.windowsLogin = $windowsLogin }
($installInfo | ConvertTo-Json) | Set-Content -Path $install -Encoding ASCII

# ---- 5. Build ---------------------------------------------------------------------------------
if (-not $SkipBuild) {
    Step 'Build'
    # Story 5653: a running Echo holds target\echo.jar open and the build cannot replace it; the Start
    # step below starts it again.
    if (@(Get-EchoProcess $root).Count -gt 0) { & (Join-Path $PSScriptRoot 'Stop-Echo.ps1') }
    Invoke-EchoBuild -Root $root -JavaHome $java.Home
    if ($Auth -eq 'Windows') {
        $lib = Join-Path $root 'lib'
        if (-not (Get-ChildItem $lib -Filter 'mssql-jdbc_auth-*.dll' -ErrorAction SilentlyContinue)) {
            Write-Host '  fetching the SQL Server Windows sign-in library (mssql-jdbc_auth, x64)'
            Invoke-EchoMaven -Root $root -JavaHome $java.Home -MavenArgs @('-q', 'dependency:copy',
                "-Dartifact=com.microsoft.sqlserver:mssql-jdbc_auth:$(Get-EchoMssqlVersion $root).x64:dll",
                "-DoutputDirectory=$lib", '-Dmdep.stripVersion=false')
        }
    }
}

$userKept = $false
# ---- 6. Your sign-in --------------------------------------------------------------------------
if (-not $SkipUser) {
    Step 'Your Echo sign-in'
    $c = New-EchoConnection -SqlServer $SqlServer -Database $Database -SqlUser $AdminSqlUser -SqlPassword $AdminSqlPassword
    try { $have = [int](Invoke-EchoScalar $c "SELECT COUNT(*) FROM dbo.AppUser WHERE Username = N'$($Username -replace "'", "''")'") }
    finally { $c.Close() }
    if ($have -gt 0) {
        # Story 5653: say plainly why no password is asked -- a re-run over a finished install looked
        # like a missed step ("it never asked me for a password").
        $userKept = $true
        Write-Host "  '$Username' already signs in to this Echo database -- kept, so no password is asked." -ForegroundColor Yellow
        Write-Host '  Your password is the one you chose at the first install; set-password.cmd changes it.' -ForegroundColor Yellow
    } else {
        $pw = @{ Username = $Username; SqlServer = $SqlServer; Database = $Database }
        if ($AdminSqlUser) { $pw.SqlUser = $AdminSqlUser; $pw.SqlPassword = $AdminSqlPassword }
        if ($Password) { $pw.Password = $Password }
        $global:LASTEXITCODE = 0
        & (Join-Path $PSScriptRoot 'Set-EchoPassword.ps1') @pw
        if ($LASTEXITCODE -eq 2) {
            # Story 5653: the password was canceled. Everything before it is in place and kept; Echo is
            # not started, since no one could sign in to it.
            Write-Host ''
            Write-Host 'The install stopped at your sign-in: no password was set, and Echo was not started.' -ForegroundColor Yellow
            Write-Host 'Everything before it (database, settings, build) is in place. To finish, run install.cmd again'
            Write-Host 'as administrator -- it keeps what is done and asks for the password again.'
            Stop-EchoInstallLog
            exit 2
        }
    }
}

# ---- 7. Start ---------------------------------------------------------------------------------
if ($AutoStart) {
    Step 'Start with Windows'
    $startup = Get-EchoStartupFolder   # Story 5653: the signed-in person's, not an elevating admin's
    $lnk = Join-Path $startup 'Echo.lnk'
    $sh = New-Object -ComObject WScript.Shell
    $s = $sh.CreateShortcut($lnk)
    $s.TargetPath = Join-Path $root 'start-echo.cmd'
    $s.WorkingDirectory = $root
    $s.WindowStyle = 7
    $s.Description = 'Start Echo'
    $s.Save()
    Write-Host "  Echo will start each time you sign in to Windows ($lnk)"
}
if (-not $NoStart) {
    Step 'Start'
    $explorer = Get-Process -Name explorer -ErrorAction SilentlyContinue
    if ((Test-EchoAdmin) -and $explorer) {
        # Story 5653: this script runs as administrator, and an Echo started from here would too --
        # then a plain stop-echo.cmd / update-echo.cmd could not stop it. explorer.exe runs as the
        # signed-in person without administrator rights, so start-echo.cmd goes through it.
        Write-Host '  starting Echo as you (not as administrator), so stop-echo.cmd and update-echo.cmd can stop it'
        Start-Process -FilePath explorer.exe -ArgumentList "`"$(Join-Path $root 'start-echo.cmd')`""
        $url = "http://127.0.0.1:$Port/api/system/health"
        # Story 5653: like Start-Echo.ps1 -- any answer ends the wait (ok=false means Echo is up but its
        # database is not), and an Echo that dies while starting is reported at once, not after 120 s.
        $started = Get-Date
        $deadline = $started.AddSeconds(120)
        $result = 'timeout'
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 2
            $answer = $null
            try { $answer = Invoke-RestMethod -Uri $url -TimeoutSec 3 } catch { }
            if ($answer) { $result = if ($answer.ok) { 'up' } else { 'nodb' }; break }
            # explorer.exe needs a moment to start it; after 20 s no Echo process means it stopped.
            if (((Get-Date) - $started).TotalSeconds -gt 20 -and @(Get-EchoProcess $root).Count -eq 0) { $result = 'died'; break }
        }
        switch ($result) {
            'up'      { Write-Host "  Echo is up: http://localhost:$Port" -ForegroundColor Green }
            'nodb'    { Write-Warning "Echo started, but it cannot reach its database $Database on $SqlServer. See logs\echo.log." }
            'died'    { Write-Warning "Echo stopped while starting. See logs\echo.log for why, then run start-echo.cmd." }
            'timeout' { Write-Warning "Echo did not answer within 120 seconds. It may still be starting; see logs\echo.log, or run start-echo.cmd." }
        }
    } else {
        & (Join-Path $PSScriptRoot 'Start-Echo.ps1')
    }
}

Write-Host ''
Write-Host "Echo is installed. Open http://localhost:$Port in a browser." -ForegroundColor Green
# Story 5653: say who to sign in as -- Tony's first finished install asked "where is the login?"
if ($userKept) { Write-Host "Sign in as '$Username' with the password you already had (set-password.cmd changes it)." -ForegroundColor Green }
elseif (-not $SkipUser) { Write-Host "Sign in as '$Username' with the password you chose above (set-password.cmd changes it)." -ForegroundColor Green }
Write-Host 'Stop it with stop-echo.cmd; update it later with update-echo.cmd.'
Stop-EchoInstallLog
