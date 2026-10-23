<#
.SYNOPSIS
    Creates an Echo sign-in, or sets a new password on one that exists. Story 5653.

.DESCRIPTION
    Asks for the password twice (it is never shown, never written to a file, never put on a
    command line); one that is too short, or typed differently the second time, is asked for again,
    and pressing Enter with nothing typed cancels (exit code 2, nothing saved). It turns it into the same PBKDF2 hash Echo itself makes (SHA-256, 210,000 rounds,
    a fresh random salt -- see PasswordHasher.java), and saves it through stp_AppUser_Save.
    Setting a password also unlocks the account and signs that person out of every browser.

    Echo does not need to be running. This is also how a forgotten password is reset.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\Set-EchoPassword.ps1 -Username tony -DisplayName Tony
#>
[CmdletBinding()]
param(
    [string]$Username = 'tony',
    [string]$DisplayName,
    [string]$SqlServer,
    [string]$Database,
    [string]$SqlUser,
    [string]$SqlPassword,
    # Story 5653: for a scripted install; left out, it asks twice (the usual way).
    [SecureString]$Password
)

$ErrorActionPreference = 'Stop'
# Without -SqlServer / -Database: the ones the installer recorded (config\echo-install.json), else the
# SQL Server found on this computer / Echo.
$installJson = Join-Path (Split-Path -Parent $PSScriptRoot) 'config\echo-install.json'
if ((-not $SqlServer -or -not $Database) -and (Test-Path $installJson)) {
    $info = Get-Content $installJson -Raw | ConvertFrom-Json
    if (-not $SqlServer) { $SqlServer = $info.sqlServer }
    if (-not $Database)  { $Database  = $info.database }
}
if (-not $SqlServer) {
    . (Join-Path $PSScriptRoot 'EchoCommon.ps1')
    $SqlServer = Select-EchoSqlServer -Root (Split-Path -Parent $PSScriptRoot) -SqlUser $SqlUser -SqlPassword $SqlPassword
}
if (-not $Database)  { $Database  = 'Echo' }

function Read-Plain([string]$prompt) {
    $sec = Read-Host -Prompt $prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

function New-EchoPasswordHash([string]$password) {
    $iterations = 210000
    $salt = New-Object byte[] 16
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($salt)
    $kdf = New-Object Security.Cryptography.Rfc2898DeriveBytes($password, $salt, $iterations, [Security.Cryptography.HashAlgorithmName]::SHA256)
    $hash = $kdf.GetBytes(32)
    return 'pbkdf2$' + $iterations + '$' + [Convert]::ToBase64String($salt) + '$' + [Convert]::ToBase64String($hash)
}

if ($Username -notmatch '^[A-Za-z0-9._-]{1,100}$') { throw "A user name is letters, digits, dots, hyphens and underscores." }
if (-not $DisplayName) { $DisplayName = $Username.Substring(0, 1).ToUpper() + $Username.Substring(1) }

if ($Password) {
    # Scripted: no one to ask again, so a bad password stops here.
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password)
    try { $p1 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    if ($p1.Length -lt 8) { throw "A password needs at least 8 characters. Nothing was saved." }
} else {
    # Story 5653: a password that is too short or typed differently the second time is asked for
    # again, not a dead end. Pressing Enter with nothing typed cancels.
    Write-Host "Choose the password '$Username' will sign in to Echo with (at least 8 characters)."
    Write-Host 'To cancel, press Enter without typing anything.'
    while ($true) {
        $p1 = Read-Plain "New Echo password for $Username"
        if ($p1.Length -eq 0) {
            Write-Host 'Canceled -- no password was saved.' -ForegroundColor Yellow
            exit 2
        }
        if ($p1.Length -lt 8) {
            Write-Host "That is $($p1.Length) characters; a password needs at least 8. Try again." -ForegroundColor Yellow
            continue
        }
        $p2 = Read-Plain 'The same password again'
        if ($p1 -ceq $p2) { break }
        Write-Host 'The two passwords are not the same. Try again.' -ForegroundColor Yellow
    }
}
$hash = New-EchoPasswordHash $p1
$p1 = $null; $p2 = $null

$b = New-Object System.Data.SqlClient.SqlConnectionStringBuilder
$b['Data Source'] = $SqlServer; $b['Initial Catalog'] = $Database; $b['Connect Timeout'] = 15
if ($SqlUser) { $b['User ID'] = $SqlUser; $b['Password'] = $SqlPassword } else { $b['Integrated Security'] = $true }
$conn = New-Object System.Data.SqlClient.SqlConnection $b.ConnectionString
$conn.Open()
try {
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = "EXEC dbo.stp_AppUser_Save @prmUsername = @u, @prmDisplayName = @d, @prmPasswordHash = @h"
    [void]$cmd.Parameters.AddWithValue('@u', $Username)
    [void]$cmd.Parameters.AddWithValue('@d', $DisplayName)
    [void]$cmd.Parameters.AddWithValue('@h', $hash)
    $id = $cmd.ExecuteScalar()
    Write-Host "Echo sign-in '$Username' is ready (user $id)."
} finally { $conn.Close() }
