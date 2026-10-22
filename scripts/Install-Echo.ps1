<#
.SYNOPSIS
    Installs Echo on this computer, start to finish. Story 5653.

.DESCRIPTION
    Run it from the folder git cloned Echo into (install.cmd does that for you). It is safe to run
    again: every step checks what is already there.

      1. Java     finds the newest Java 21 or later (-JavaHome, else JAVA_HOME, the PATH, the
                 registry and the usual JDK folders, unzipped ones included).
      2. Database creates or updates the Echo database (scripts\Install-EchoDatabase.ps1).
      3. Login    -Auth SqlLogin (default): a SQL login "echo_app" that can only read, write and run
                  Echo's procedures, with a long random password nobody types.
                  -Auth Windows: Echo signs in to SQL Server as the Windows account it runs under
                  (needs lib\mssql-jdbc_auth-*.dll, which this step downloads).
      4. Config   config\application-local.properties -- the connection, a fresh random token
                  secret and service key. Written once; -Reconfigure writes it again.
      5. Build    mvnw package (downloads what Maven needs the first time). -SkipBuild skips it.
      6. Sign-in  asks for your Echo password (scripts\Set-EchoPassword.ps1). -SkipUser skips it.
      7. Start    starts Echo in the background and waits for it to answer. -NoStart skips it.
                  -AutoStart also starts Echo every time you sign in to Windows.

    Nothing here needs administrator rights except creating the database and the login on a SQL
    Server you do not administer -- then pass -AdminSqlUser / -AdminSqlPassword for an account that
    may. Echo answers only on this computer (127.0.0.1) unless you say -Bind 0.0.0.0.

.EXAMPLE
    install.cmd -SqlServer .\SQLEXPRESS
.EXAMPLE
    install.cmd -SqlServer MYPC\SQL2019 -Auth Windows -AutoStart
#>
[CmdletBinding()]
param(
    [string]$SqlServer = 'localhost',
    [string]$Database  = 'Echo',
    [ValidateSet('SqlLogin', 'Windows')]
    [string]$Auth = 'SqlLogin',
    [string]$AdminSqlUser,
    [string]$AdminSqlPassword,
    [int]$Port = 8770,
    [string]$Bind = '127.0.0.1',
    [string]$JavaHome,
    [string]$Username = 'tony',
    [switch]$SkipUser,
    [switch]$SkipBuild,
    [switch]$Reconfigure,
    [switch]$NoStart,
    [switch]$AutoStart
)

$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$config  = Join-Path $root 'config'
$local   = Join-Path $config 'application-local.properties'
$install = Join-Path $config 'echo-install.json'
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')

function Step([string]$text) { Write-Host ''; Write-Host "== $text" -ForegroundColor Cyan }

# ---- 1. Java ----------------------------------------------------------------------------------
Step 'Java'
$java = Find-EchoJava -JavaHome $JavaHome
Write-Host "  using $($java.Home) (Java $($java.Major))"

# ---- 2. Database ------------------------------------------------------------------------------
Step 'Database'
$dbArgs = @{ SqlServer = $SqlServer; Database = $Database }
if ($AdminSqlUser) { $dbArgs.SqlUser = $AdminSqlUser; $dbArgs.SqlPassword = $AdminSqlPassword }
& (Join-Path $PSScriptRoot 'Install-EchoDatabase.ps1') @dbArgs

# ---- 3 + 4. Login and config ------------------------------------------------------------------
Step 'Connection and keys'
New-Item -ItemType Directory -Force $config | Out-Null
if ((Test-Path $local) -and -not $Reconfigure) {
    Write-Host "  config\application-local.properties already exists -- kept (use -Reconfigure to write it again)"
} else {
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
        Write-Host "  Windows sign-in: Echo connects as the Windows account that runs it ($env:USERDOMAIN\$env:USERNAME when you start it)"
    }
    $lines += "echo.jwt.secret=$(New-EchoSecret 48)"
    $lines += "echo.service.key=$(New-EchoSecret 32)"
    $lines += "echo.port=$Port"
    $lines += "echo.bind=$Bind"
    [IO.File]::WriteAllLines($local, $lines)
    Write-Host "  wrote config\application-local.properties (new token secret and service key)"
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
($installInfo | ConvertTo-Json) | Set-Content -Path $install -Encoding ASCII

# ---- 5. Build ---------------------------------------------------------------------------------
if (-not $SkipBuild) {
    Step 'Build'
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

# ---- 6. Your sign-in --------------------------------------------------------------------------
if (-not $SkipUser) {
    Step 'Your Echo sign-in'
    $c = New-EchoConnection -SqlServer $SqlServer -Database $Database -SqlUser $AdminSqlUser -SqlPassword $AdminSqlPassword
    try { $have = [int](Invoke-EchoScalar $c "SELECT COUNT(*) FROM dbo.AppUser WHERE Username = N'$($Username -replace "'", "''")'") }
    finally { $c.Close() }
    if ($have -gt 0) {
        Write-Host "  '$Username' already signs in -- kept (scripts\Set-EchoPassword.ps1 changes the password)"
    } else {
        $pw = @{ Username = $Username; SqlServer = $SqlServer; Database = $Database }
        if ($AdminSqlUser) { $pw.SqlUser = $AdminSqlUser; $pw.SqlPassword = $AdminSqlPassword }
        & (Join-Path $PSScriptRoot 'Set-EchoPassword.ps1') @pw
    }
}

# ---- 7. Start ---------------------------------------------------------------------------------
if ($AutoStart) {
    Step 'Start with Windows'
    $startup = [Environment]::GetFolderPath('Startup')
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
    & (Join-Path $PSScriptRoot 'Start-Echo.ps1')
}

Write-Host ''
Write-Host "Echo is installed. Open http://localhost:$Port in a browser." -ForegroundColor Green
Write-Host 'Stop it with stop-echo.cmd; update it later with update-echo.cmd.'
