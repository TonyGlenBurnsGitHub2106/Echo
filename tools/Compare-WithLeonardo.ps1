<#
.SYNOPSIS
    Says where Echo's copy of Leonardo has drifted. Run at home, where both databases live. Story 5653.

.DESCRIPTION
    Echo echoes Leonardo: its procedures, views, functions and triggers are copies, file for file,
    and its tables match Leonardo's column for column. This reports, without changing anything:

      CHANGED IN LEONARDO  a module whose Leonardo text differs from Echo's file (Echo's own marked
                           edits show here too -- read the difference before copying anything)
      NEW IN LEONARDO      a Solutions-family module Leonardo has and Echo does not
      COLUMNS              a shared table whose columns differ between the two databases

    To carry a change across: copy Leonardo's text into the Echo file (keeping the IF OBJECT_ID ...
    DROP header and Echo's own edits), then run update-echo.cmd. The Java and the pages are compared
    the same way by hand: the class and file names are the same in both repositories.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\Compare-WithLeonardo.ps1 -SqlServer RENAISSANCE\SQL2012EXPRESS
#>
[CmdletBinding()]
param(
    [string]$SqlServer = 'localhost',
    [string]$LeonardoDatabase = 'Leonardo',
    [string]$EchoDatabase = 'Echo',
    [string]$SqlUser,
    [string]$SqlPassword
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $root 'scripts\EchoCommon.ps1')

# Leonardo modules Echo leaves out on purpose: they serve Leonardo Files, which Echo does not have.
$notEchoed = @('stp_SolutionFile_ListIDs', 'stp_SolutionFile_SetLeonardoFile')

# Echo's renames (Echo name -> Leonardo name).
$renames = @{ 'QuickLaunch' = 'LWQuickLaunch' }
function To-Leonardo([string]$name) { foreach ($k in $renames.Keys) { $name = $name -replace $k, $renames[$k] }; return $name }
function Normalize([string]$text) {
    $t = $text -replace "(?is)^\s*SET ANSI_NULLS ON\s*GO\s*SET QUOTED_IDENTIFIER ON\s*GO\s*", ''
    $t = $t -replace "(?is)^\s*IF OBJECT_ID\('[^']+'\) IS NOT NULL DROP \w+ [\w.]+\s*GO\s*", ''
    $t = $t -replace '(?im)^\s*GO\s*$', ''
    foreach ($k in $renames.Keys) { $t = $t -replace $renames[$k], $k }
    return ($t -replace '\s+', ' ').Trim()
}

$lc = New-EchoConnection -SqlServer $SqlServer -Database $LeonardoDatabase -SqlUser $SqlUser -SqlPassword $SqlPassword
$ec = New-EchoConnection -SqlServer $SqlServer -Database $EchoDatabase -SqlUser $SqlUser -SqlPassword $SqlPassword
try {
    $leo = @{}
    $cmd = $lc.CreateCommand()
    $cmd.CommandText = "SELECT o.name, m.definition FROM sys.objects o JOIN sys.sql_modules m ON m.object_id = o.object_id"
    $r = $cmd.ExecuteReader(); while ($r.Read()) { $leo[$r.GetString(0)] = $r.GetString(1) }; $r.Close()

    Write-Host '== Modules'
    $echoNames = @{}
    foreach ($folder in 'functions', 'views', 'procs', 'triggers') {
        foreach ($f in Get-ChildItem (Join-Path $root "db\$folder") -Filter *.sql) {
            $name = $f.BaseName; $echoNames[$name] = 1
            $lname = To-Leonardo $name
            if (-not $leo.ContainsKey($lname)) { continue }    # Echo's own (stp_AppUser_* ...)
            if ((Normalize ([IO.File]::ReadAllText($f.FullName))) -ne (Normalize $leo[$lname])) { Write-Host "  CHANGED IN LEONARDO  $folder\$name" }
        }
    }
    foreach ($n in ($leo.Keys | Sort-Object)) {
        if ($n -match '^(stp_Solution|trg_Solution|vw_Solution|stp_ApiClient|trg_ApiClient|stp_FileCategory|stp_LWQuickLaunch|stp_UserViewState)' -and -not $echoNames.ContainsKey(($n -replace 'LWQuickLaunch', 'QuickLaunch')) -and $notEchoed -notcontains $n) {
            Write-Host "  NEW IN LEONARDO      $n"
        }
    }

    Write-Host '== Columns of shared tables'
    $q = "SELECT t.name + '.' + c.name + ' ' + ty.name + '(' + CONVERT(VARCHAR(10), c.max_length) + ')' + CASE WHEN c.is_nullable = 1 THEN ' null' ELSE '' END FROM sys.columns c JOIN sys.tables t ON t.object_id = c.object_id JOIN sys.types ty ON ty.user_type_id = c.user_type_id"
    $read = { param($conn) $cmd = $conn.CreateCommand(); $cmd.CommandText = $q; $r = $cmd.ExecuteReader(); $s = New-Object 'System.Collections.Generic.HashSet[string]'; while ($r.Read()) { [void]$s.Add($r.GetString(0)) }; $r.Close(); , $s }
    $lcols = & $read $lc; $ecols = & $read $ec
    $etables = @{}; foreach ($x in $ecols) { $etables[(To-Leonardo $x.Split('.')[0])] = 1 }
    foreach ($x in ($lcols | Sort-Object)) {
        $t = $x.Split('.')[0]
        if ($etables.ContainsKey($t) -and -not $ecols.Contains(($x -replace '^LWQuickLaunch', 'QuickLaunch'))) { Write-Host "  only in Leonardo: $x" }
    }
    foreach ($x in ($ecols | Sort-Object)) {
        $lt = To-Leonardo $x.Split('.')[0]
        $lx = $lt + $x.Substring($x.IndexOf('.'))
        $lhas = $false; foreach ($y in $lcols) { if ($y.StartsWith($lt + '.')) { $lhas = $true; break } }
        if ($lhas -and -not $lcols.Contains($lx)) { Write-Host "  only in Echo:     $x" }
    }
} finally { $lc.Close(); $ec.Close() }
Write-Host 'Done.'
