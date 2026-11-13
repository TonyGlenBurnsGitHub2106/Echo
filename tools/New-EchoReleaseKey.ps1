<#
.SYNOPSIS
    Makes the key that signs Echo's releases. Run ONCE, on the publishing computer. Story 5653 / Story 5765 (audit fence-4).

.DESCRIPTION
    tools\Publish-EchoRelease.ps1 signs every release, and every installed Echo refuses an update that is
    not signed by the key it trusts. This makes that key:

      * the PRIVATE half goes to %USERPROFILE%\.echo-signing\release-signing-key.dat, protected with this
        Windows user's DPAPI -- only this user on this computer can open it. It is never written into the
        Echo folder or the repository, and never printed.
      * the PUBLIC half goes to scripts\release-public-key.xml. Commit and push it before the next publish:
        an installed Echo takes it (once) as the key it trusts.

    It refuses when a key is already there: a new key means every installed Echo refuses updates until
    update-echo.cmd -TrustNewKey is run on it. -Replace makes a new one anyway (the old private key is
    moved aside, never deleted).

    Back up the private key file the way the house backs up its secrets: without it, the next release
    has to be signed by a new key, and every installed Echo must then be told to trust it by hand.
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\New-EchoReleaseKey.ps1
#>
[CmdletBinding()]
param([switch]$Replace)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'scripts\EchoCommon.ps1')
Add-Type -AssemblyName System.Security

$keyPath = Get-EchoSigningKeyPath
$pubPath = Get-EchoRepoPublicKeyPath $root
if (Test-Path $keyPath) {
    $have = Get-EchoKeyFingerprint (Get-EchoPublicKeyOf (Get-EchoSigningKeyXml))
    if (-not $Replace) {
        throw "A release signing key is already here ($keyPath, key $have). Every installed Echo trusts it; a new one would make them refuse updates until update-echo.cmd -TrustNewKey is run on each. Use -Replace only if that is what you want."
    }
    $aside = $keyPath + '.replaced-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    Move-Item -Path $keyPath -Destination $aside
    Write-Host "The old signing key ($have) is kept as $aside" -ForegroundColor Yellow
}

$rsa = New-Object System.Security.Cryptography.RSACryptoServiceProvider(3072)
$rsa.PersistKeyInCsp = $false
try {
    $private = $rsa.ToXmlString($true)
    $public = $rsa.ToXmlString($false)
} finally { $rsa.Dispose() }

# Prove the pair works before anything is written.
$probe = [pscustomobject]@{ Tag = 'build-selftest'; Version = '0.0.1'; Commit = ('0' * 40); Sha256 = ('0' * 64) }
$probe | Add-Member -NotePropertyName Signature -NotePropertyValue (New-EchoReleaseSignature $probe $private)
if (-not (Test-EchoReleaseSignature $probe $public)) { throw 'The new key failed its own signature test. Nothing was written.' }

New-Item -ItemType Directory -Force (Split-Path $keyPath) | Out-Null
$sealed = [Security.Cryptography.ProtectedData]::Protect([Text.Encoding]::UTF8.GetBytes($private), (Get-EchoSigningEntropy), 'CurrentUser')
[IO.File]::WriteAllBytes($keyPath, $sealed)
if (-not ((Get-EchoPublicKeyOf (Get-EchoSigningKeyXml)) -eq $public)) { throw "The private key was written to $keyPath but does not read back. Do not publish." }
[IO.File]::WriteAllText($pubPath, $public)

Write-Host ''
Write-Host "Release signing key $(Get-EchoKeyFingerprint $public) is ready." -ForegroundColor Green
Write-Host "  private: $keyPath (this Windows user only -- back it up with the house's secrets)"
Write-Host "  public:  scripts\release-public-key.xml -- commit and push it, then publish with tools\Publish-EchoRelease.ps1"
