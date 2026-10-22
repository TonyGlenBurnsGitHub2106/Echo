<#
.SYNOPSIS
    Writes one solution -- every row of it, and the bytes of its files -- to a single file that
    Import-Solution.ps1 can load into an Echo. Story 5653.

.DESCRIPTION
    Works against Leonardo's database or an Echo database: the Solutions tables are the same in
    both. Everything comes across exactly: projects and items at every depth, the log (with its
    numbers, authors and dates), notes with every version and step, terms, testing and QA
    checklists with their ticks, both Configuration tabs, file filing, conversations with every
    message, and the files themselves. Not copied: who the solution is shared with (an agent's
    key belongs to one Echo) and, from Leonardo, the records it hangs on.

    The file holds the solution's whole content. Keep it somewhere private and remove it once it
    has been imported.

.EXAMPLE
    # At home, from Leonardo:
    powershell -ExecutionPolicy Bypass -File scripts\Export-Solution.ps1 -SqlServer RENAISSANCE\SQL2012EXPRESS -Database Leonardo -Alias claims -OutFile C:\temp\claims.echo.json
#>
[CmdletBinding()]
param(
    [string]$SqlServer = 'localhost',
    [string]$Database = 'Leonardo',
    [string]$SqlUser,
    [string]$SqlPassword,
    [int]$SolutionId,
    [string]$Alias,
    [Parameter(Mandatory = $true)][string]$OutFile,
    # Where the bytes of older 'nook' files are (Leonardo) and of 'local' files (an Echo).
    [string]$NookRoot = 'C:\Tony\Dev\java\LeonardoWeb\reading-nook\solutions',
    [string]$FilesRoot
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')
. (Join-Path $PSScriptRoot 'EchoSolutionTables.ps1')
if (-not $FilesRoot) { $FilesRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'EchoFiles' }

function ConvertTo-JsonValue($v) {
    if ($v -is [DBNull] -or $null -eq $v) { return $null }
    if ($v -is [DateTime]) { return $v.ToString('yyyy-MM-ddTHH:mm:ss.fff') }
    if ($v -is [DateTimeOffset]) { return $v.ToString('o') }
    if ($v -is [byte[]]) { return [Convert]::ToBase64String($v) }
    return $v
}

$c = New-EchoConnection -SqlServer $SqlServer -Database $Database -SqlUser $SqlUser -SqlPassword $SqlPassword
try {
    if (-not $SolutionId) {
        if (-not $Alias) { throw 'Name the solution with -SolutionId or -Alias.' }
        $cmd = $c.CreateCommand(); $cmd.CommandText = 'SELECT SolutionID FROM dbo.Solution WHERE SolutionAlias = @a'
        [void]$cmd.Parameters.AddWithValue('@a', $Alias.ToLower())
        $SolutionId = [int]$cmd.ExecuteScalar()
        if (-not $SolutionId) { throw "No solution has the alias '$Alias'." }
    }
    $cmd = $c.CreateCommand(); $cmd.CommandText = 'SELECT SolutionName FROM dbo.Solution WHERE SolutionID = @s'
    [void]$cmd.Parameters.AddWithValue('@s', $SolutionId)
    $name = $cmd.ExecuteScalar()
    if (-not $name) { throw "There is no solution $SolutionId in $Database." }

    $tables = [ordered]@{}
    $total = 0
    foreach ($t in $EchoSolutionTables) {
        $cmd = $c.CreateCommand()
        $cmd.CommandText = "SELECT * FROM dbo.$($t.Name) WHERE $($t.Where)"
        $cmd.CommandTimeout = 300
        [void]$cmd.Parameters.AddWithValue('@sid', $SolutionId)
        $r = $cmd.ExecuteReader()
        $rows = New-Object System.Collections.ArrayList
        while ($r.Read()) {
            $row = [ordered]@{}
            for ($i = 0; $i -lt $r.FieldCount; $i++) { $row[$r.GetName($i)] = ConvertTo-JsonValue $r.GetValue($i) }
            [void]$rows.Add($row)
        }
        $r.Close()
        $tables[$t.Name] = $rows
        $total += $rows.Count
    }

    # The bytes of every file.
    $hasLeonardoFiles = [int](Invoke-EchoScalar $c "SELECT CASE WHEN OBJECT_ID('dbo.LeonardoFile') IS NULL THEN 0 ELSE 1 END")
    $files = [ordered]@{}
    $missing = @()
    foreach ($f in $tables['SolutionFile']) {
        $path = $null
        if ($f.StorageCode -eq 'repo' -and $f.LeonardoFileID -and $hasLeonardoFiles) {
            $cmd = $c.CreateCommand(); $cmd.CommandText = 'SELECT FilePath FROM dbo.LeonardoFile WHERE LeonardoFileID = @id'
            [void]$cmd.Parameters.AddWithValue('@id', [int]$f.LeonardoFileID)
            $path = $cmd.ExecuteScalar()
        } elseif ($f.StorageCode -eq 'nook' -and $f.StoredFileName) {
            $path = Join-Path $NookRoot $f.StoredFileName
        } elseif ($f.StorageCode -eq 'local' -and $f.StoredFileName) {
            $path = Join-Path $FilesRoot $f.StoredFileName
        }
        if ($path -and (Test-Path -LiteralPath $path)) {
            $files["$($f.SolutionFileID)"] = [Convert]::ToBase64String([IO.File]::ReadAllBytes($path))
        } else {
            $missing += "$($f.SolutionFileID) $($f.OriginalFileName)"
        }
    }
} finally { $c.Close() }

$doc = [ordered]@{
    format       = 'echo-solution/1'
    exportedFrom = "$SqlServer / $Database"
    exportedAt   = (Get-Date -Format 'yyyy-MM-ddTHH:mm:ss')
    solutionId   = $SolutionId
    solutionName = $name
    tables       = $tables
    files        = $files
}
$json = $doc | ConvertTo-Json -Depth 6 -Compress
[IO.File]::WriteAllText($OutFile, $json, (New-Object Text.UTF8Encoding($false)))
Write-Host ("Exported '{0}' (solution {1}): {2} rows, {3} files, {4:N0} KB -> {5}" -f $name, $SolutionId, $total, $files.Count, ((Get-Item $OutFile).Length / 1KB), $OutFile) -ForegroundColor Green
if ($missing) { Write-Warning ("These files' bytes were not found, so only their rows were exported: " + ($missing -join '; ')) }
