<#
.SYNOPSIS
    Writes one solution -- every row of it, and the bytes of its files -- to a single file that
    Import-Solution.ps1 can load into an Echo. Story 5653.

.DESCRIPTION
    Works against Leonardo's database or an Echo database: the Solutions tables are the same in
    both. Everything comes across exactly: projects and items at every depth, the log (with its
    numbers, authors and dates), notes with every version and step, terms, testing and QA
    checklists with their ticks, both Configuration tabs, file filing, conversations with every
    message, the files themselves, and the Web tab's web addresses (with their types, Important
    marks and order). Not copied: who the solution is shared with (an agent's key belongs to one
    Echo), its logins, and, from Leonardo, the records it hangs on.

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

    # Story 5653: the solution's Web tab -- its web addresses, hung off its EntityID -- by value (name,
    # address, types by name, notes, Important, order), since entity ids mean nothing in another database.
    # Import-Solution.ps1 makes them again through Echo's own procedures. Logins are not exported.
    $webAddresses = New-Object System.Collections.ArrayList
    if ([int](Invoke-EchoScalar $c "SELECT CASE WHEN OBJECT_ID('dbo.tblEntityWebAddressXRef') IS NULL THEN 0 ELSE 1 END")) {
        $cmd = $c.CreateCommand()
        $cmd.CommandText = @'
SELECT w.WebAddressName, w.WebAddress, at.WebAddressType, st.WebSiteType, w.Notes,
       CAST(ISNULL(f.IsImportant, 0) AS BIT) AS IsImportant, q.SortOrder
FROM   dbo.Solution s
JOIN   dbo.tblEntityWebAddressXRef x ON x.EntityID = s.EntityID
JOIN   dbo.tblWebAddress w            ON w.WebAddressID = x.WebAddressID
LEFT JOIN dbo.tblWebAddressType at    ON at.WebAddressTypeID = w.WebAddressTypeID
LEFT JOIN dbo.WebSiteType st          ON st.WebSiteTypeID = w.WebSiteTypeID
LEFT JOIN dbo.EntityWebAddressFlag f  ON f.EntityWebAddressXRefID = x.EntityWebAddressXRefID
LEFT JOIN dbo.EntityWebAddressSequence q ON q.EntityWebAddressXRefID = x.EntityWebAddressXRefID
WHERE  s.SolutionID = @s
ORDER BY CASE WHEN q.SortOrder IS NULL THEN 1 ELSE 0 END, q.SortOrder, x.EntityWebAddressXRefID
'@
        [void]$cmd.Parameters.AddWithValue('@s', $SolutionId)
        $r = $cmd.ExecuteReader()
        while ($r.Read()) {
            $row = [ordered]@{}
            for ($i = 0; $i -lt $r.FieldCount; $i++) { $row[$r.GetName($i)] = ConvertTo-JsonValue $r.GetValue($i) }
            [void]$webAddresses.Add($row)
        }
        $r.Close()
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
    webAddresses = $webAddresses   # Story 5653
}
$json = $doc | ConvertTo-Json -Depth 6 -Compress
[IO.File]::WriteAllText($OutFile, $json, (New-Object Text.UTF8Encoding($false)))
Write-Host ("Exported '{0}' (solution {1}): {2} rows, {3} files, {4} web addresses, {5:N0} KB -> {6}" -f $name, $SolutionId, $total, $files.Count, $webAddresses.Count, ((Get-Item $OutFile).Length / 1KB), $OutFile) -ForegroundColor Green
if ($missing) { Write-Warning ("These files' bytes were not found, so only their rows were exported: " + ($missing -join '; ')) }
