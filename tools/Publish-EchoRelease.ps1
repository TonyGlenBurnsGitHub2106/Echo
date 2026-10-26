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

    The GitHub sign-in comes from Git Credential Manager (git credential fill); it is never printed.
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

# ---- 2. build that commit in a folder of its own ---------------------------------------------------
$stamp = Get-Date -Format 'yyyyMMdd-HHmm'
$work = Join-Path 'C:\temp\claude\echo-release' $stamp
if (Test-Path $work) { $work = "$work-" + (Get-Date -Format 'ss') }
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
$cred = "protocol=https`nhost=github.com`n`n" | & git credential fill
$token = (@($cred | Where-Object { $_ -like 'password=*' }) | Select-Object -First 1) -replace '^password=', ''
if (-not $token) { throw 'Git Credential Manager has no GitHub sign-in for github.com.' }
Set-EchoWebDefaults
$headers = @{ 'Authorization' = "token $token"; 'User-Agent' = 'Echo-Publish'; 'Accept' = 'application/vnd.github+json' }

$tag = "build-$stamp"
$body = @{
    tag_name         = $tag
    target_commitish = $head
    name             = 'Echo ' + (Get-Date -Format 'yyyy-MM-dd HH:mm')
    body             = "commit: $head`nsha256: $sha"
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
Write-Host "Published release $tag. Settings -> Update Echo will now find it." -ForegroundColor Green
