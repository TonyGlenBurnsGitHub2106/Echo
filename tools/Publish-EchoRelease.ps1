<#
.SYNOPSIS
    Builds Echo from the pushed commit and publishes the program as a GitHub release. Story 5653.

.DESCRIPTION
    Run on the development computer after the work is committed and pushed. Other computers then
    get it with Settings -> Update Echo (or update-echo.cmd): they download the program instead of
    compiling it.

      1. Refuses to go on with uncommitted changes, or a commit GitHub does not have yet.
      2. Exports exactly that commit (git archive) to C:\temp\claude\echo-release\<date>\ and builds
         it there, so the program is the pushed code and nothing else, and the running Echo's
         target\echo.jar is never touched.
      3. Creates a draft release tagged build-<date>, attaches echo.jar, then publishes it. The notes
         carry "commit: <sha>" and "sha256: <hash>", which the updater checks the download against.

    Every update has a version of its own (Tony, 2026-11-04: "increment version number with every
    update"). The version is <version> in pom.xml (Settings -> About Echo shows it). Before anything is
    built, this refuses a version an earlier release already carries: raise it in pom.xml (1.2.0 ->
    1.3.0 for new features, 1.2.0 -> 1.2.1 for fixes only), commit and push, then publish. The release is
    named "Echo <version>" and its notes carry "version: <version>".

    The GitHub sign-in comes from Git Credential Manager (git credential fill); it is never printed.

    Every release is SIGNED (Story 5653 / Story 5765, audit fence-4): its tag, version, commit and sha256 are
    signed with the release signing key, whose private half lives only in this Windows user's profile
    (%USERPROFILE%\.echo-signing\release-signing-key.dat, DPAPI; made once by tools\New-EchoReleaseKey.ps1),
    and the notes carry "signature: <base64>". An installed Echo refuses a release that is unsigned or signed
    by any other key, and installs only the scripts of the signed commit. This refuses to publish when the key
    is missing or does not match the committed scripts\release-public-key.xml.
#>
[CmdletBinding()]
param([string]$JavaHome)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'scripts\EchoCommon.ps1')

function Invoke-Git([string]$GitArgs) {
    $out = & cmd /c "git -C `"$root`" $GitArgs 2>&1"
    if ($LASTEXITCODE -ne 0) { throw "git $GitArgs failed: $($out -join ' ')" }
    return $out
}

# ---- 1. committed and pushed ------------------------------------------------------------------
if (Invoke-Git 'status --porcelain --untracked-files=no') { throw 'There are uncommitted changes. Commit and push first; a release is built from the pushed commit only.' }
$branch = [string](Invoke-Git 'rev-parse --abbrev-ref HEAD')
Invoke-Git 'fetch --quiet origin' | Out-Null
$head = ([string](Invoke-Git 'rev-parse HEAD')).Trim()
$remote = ([string](Invoke-Git "rev-parse origin/$branch")).Trim()
if ($head -ne $remote) { throw "This commit is not on GitHub yet (origin/$branch is elsewhere). Push first." }
$repo = Get-EchoGitHubRepo $root
Write-Host "== $repo, $branch at $($head.Substring(0, 10))"

# ---- 1a. the signing key (Story 5653 / Story 5765, audit fence-4) --------------------------------------
$signingKey = Get-EchoSigningKeyXml
Invoke-Git 'ls-files --error-unmatch scripts/release-public-key.xml' | Out-Null   # committed, not just on disk
$repoPublic = Read-EchoPublicKey (Get-EchoRepoPublicKeyPath $root)
if (-not $repoPublic -or (Get-EchoKeyFingerprint (Get-EchoPublicKeyOf $signingKey)) -ne (Get-EchoKeyFingerprint $repoPublic)) {
    throw 'The signing key on this computer is not the one in scripts\release-public-key.xml, so installed Echos would refuse this release. Nothing was published.'
}
Write-Host "== signing key $(Get-EchoKeyFingerprint $repoPublic)"

# ---- 1b. a version of its own -----------------------------------------------------------------
[xml]$pom = Get-Content (Join-Path $root 'pom.xml') -Raw
$version = [string]$pom.project.version
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw "pom.xml's <version> is '$version'; it must look like 1.2.0." }
Set-EchoWebDefaults
# Windows PowerShell hands a JSON array on as ONE object: unroll it, so each release is tested on its own
$published = @((Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases?per_page=100" -Headers @{ 'User-Agent' = 'Echo-Publish' }) | ForEach-Object { $_ })
$taken = $published | Where-Object { [string]$_.body -match "(?m)^version: $([regex]::Escape($version))\s*$" } | Select-Object -First 1
if ($taken) {
    throw "Echo $version is already published ($($taken.tag_name)). Every update needs a version of its own: raise <version> in pom.xml (new features: the middle number, fixes only: the last), commit and push, then publish again."
}
Write-Host "== version $version"

# ---- 2. build that commit in a folder of its own ---------------------------------------------------
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$work = Join-Path 'C:\temp\claude\echo-release' $stamp
New-Item -ItemType Directory -Force $work | Out-Null
$zip = Join-Path $work 'source.zip'
Invoke-Git "archive --format=zip -o `"$zip`" HEAD" | Out-Null
$src = Join-Path $work 'src'
Expand-Archive -Path $zip -DestinationPath $src
if (-not $JavaHome) {
    $installed = Join-Path $root 'config\echo-install.json'
    $JavaHome = if (Test-Path $installed) { (Get-Content $installed -Raw | ConvertFrom-Json).javaHome } else { (Find-EchoJava).Home }
}
Write-Host "== build ($work)"
Invoke-EchoBuild -Root $src -JavaHome $JavaHome
$jar = Join-Path $src 'target\echo.jar'
$sha = Get-EchoFileSha256 $jar
Write-Host ("  echo.jar {0:N1} MB, sha256 {1}" -f ((Get-Item $jar).Length / 1MB), $sha)

# ---- 3. the release ---------------------------------------------------------------------------
# Windows PowerShell's pipe to a native program garbles the request; feed it from a file instead.
$ask = Join-Path $work 'credential-request.txt'
[IO.File]::WriteAllText($ask, "protocol=https`nhost=github.com`n`n")
$cred = & cmd /c "git credential fill < `"$ask`""
$token = (@($cred | Where-Object { $_ -like 'password=*' }) | Select-Object -First 1) -replace '^password=', ''
if (-not $token) { throw 'Git Credential Manager has no GitHub sign-in for github.com.' }
Set-EchoWebDefaults
$headers = @{ 'Authorization' = "token $token"; 'User-Agent' = 'Echo-Publish'; 'Accept' = 'application/vnd.github+json' }

$tag = "build-$stamp"
# Story 5653 / Story 5765 (audit fence-4): sign exactly the fields the updater reads back.
$signature = New-EchoReleaseSignature ([pscustomobject]@{ Tag = $tag; Version = $version; Commit = $head; Sha256 = $sha }) $signingKey
$body = @{
    tag_name         = $tag
    target_commitish = $head
    name             = "Echo $version (" + (Get-Date -Format 'yyyy-MM-dd HH:mm') + ')'
    body             = "version: $version`ncommit: $head`nsha256: $sha`nsignature: $signature"   # Story 5653 / Story 5765 (audit fence-4)
    draft            = $true
} | ConvertTo-Json
Write-Host "== release $tag"
$rel = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers -Body $body -ContentType 'application/json'
$upload = "https://uploads.github.com/repos/$repo/releases/$($rel.id)/assets?name=echo.jar"
$asset = Invoke-RestMethod -Method Post -Uri $upload -Headers $headers -InFile $jar -ContentType 'application/java-archive' -TimeoutSec 900
Write-Host ("  attached echo.jar ({0:N1} MB)" -f ($asset.size / 1MB))
$null = Invoke-RestMethod -Method Patch -Uri "https://api.github.com/repos/$repo/releases/$($rel.id)" -Headers $headers `
            -Body (@{ draft = $false; make_latest = 'true' } | ConvertTo-Json) -ContentType 'application/json'

# ---- check it the way the updater will see it -------------------------------------------------
$latest = Get-EchoLatestRelease $repo
if ($latest.Tag -ne $tag -or $latest.Sha256 -ne $sha) { throw "Published, but the newest release reads back as $($latest.Tag) / $($latest.Sha256)." }
# Story 5653 / Story 5765 (audit fence-4): and its signature checks out the way an installed Echo will check it.
if (-not (Test-EchoReleaseSignature $latest $repoPublic)) { throw "Published, but release $tag does not read back with a good signature. Installed Echos will refuse it." }
Write-Host "Published Echo $version (release $tag). Settings -> Update Echo will now find it." -ForegroundColor Green
