<#
.SYNOPSIS
    Loads a solution written by Export-Solution.ps1 into this Echo. Story 5653.

.DESCRIPTION
    Every row keeps its id, its dates and its author, so the solution arrives exactly as it was
    (log numbers, note versions, ticks and all). Its files are written into Echo's files folder.

    It refuses, and changes nothing, when:
      - this Echo already has a solution, item, note ... with one of the same ids;
      - a shared list (statuses, members, kinds, file categories ...) has the same id here but
        meaning something else, or the same name under a different id.
    Everything is written in one transaction: either all of it lands or none of it does.

    Run it as someone allowed to change Echo's tables (the person who installed Echo, or
    -SqlUser / -SqlPassword for such an account): keeping the ids needs more than Echo's own
    echo_app login may do. Echo may keep running.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\Import-Solution.ps1 -InFile C:\temp\claims.echo.json
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$InFile,
    [string]$SqlServer,
    [string]$Database,
    [string]$SqlUser,
    [string]$SqlPassword,
    [string]$FilesRoot
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'EchoCommon.ps1')
. (Join-Path $PSScriptRoot 'EchoSolutionTables.ps1')
if (-not $SqlServer -or -not $Database) {
    $info = Read-EchoInstall $root
    if (-not $SqlServer) { $SqlServer = $info.sqlServer }
    if (-not $Database)  { $Database  = $info.database }
}
if (-not $FilesRoot) { $FilesRoot = Join-Path $root 'EchoFiles' }

# ---- read the file (JavaScriptSerializer: no size limit, plain dictionaries back) ---------------
Add-Type -AssemblyName System.Web.Extensions
$ser = New-Object System.Web.Script.Serialization.JavaScriptSerializer
$ser.MaxJsonLength = [int]::MaxValue
$doc = $ser.DeserializeObject([IO.File]::ReadAllText($InFile))
if ($doc['format'] -ne 'echo-solution/1') { throw "$InFile is not a solution export (format $($doc['format']))." }
$sid = [int]$doc['solutionId']
Write-Host "Importing '$($doc['solutionName'])' (solution $sid, exported from $($doc['exportedFrom']) on $($doc['exportedAt']))"

$c = New-EchoConnection -SqlServer $SqlServer -Database $Database -SqlUser $SqlUser -SqlPassword $SqlPassword
$written = @()
$tran = $null
try {
    # ---- the shape of each table here -----------------------------------------------------------
    function Get-Shape([string]$table) {
        $cmd = $c.CreateCommand(); $cmd.Transaction = $tran
        $cmd.CommandText = "SELECT c.name, c.is_identity, t.name AS type FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id WHERE c.object_id = OBJECT_ID('dbo.$table') ORDER BY c.column_id"
        $r = $cmd.ExecuteReader(); $cols = @()
        while ($r.Read()) { $cols += [pscustomobject]@{ Name = $r.GetString(0); Identity = $r.GetBoolean(1); Type = $r.GetString(2) } }
        $r.Close()
        if (-not $cols) { throw "This Echo has no table $table -- run update-echo.cmd first." }
        return $cols
    }
    function Get-Ids([string]$table, [string]$pk, [object[]]$ids) {
        $have = @{}
        if (-not $ids) { return $have }
        for ($i = 0; $i -lt $ids.Count; $i += 500) {
            $part = ($ids[$i..([Math]::Min($i + 499, $ids.Count - 1))] | ForEach-Object { [int]$_ }) -join ','
            $cmd = $c.CreateCommand(); $cmd.Transaction = $tran
            $cmd.CommandText = "SELECT * FROM dbo.$table WHERE $pk IN ($part)"
            $r = $cmd.ExecuteReader()
            while ($r.Read()) { $row = @{}; for ($k = 0; $k -lt $r.FieldCount; $k++) { $row[$r.GetName($k)] = $r.GetValue($k) }; $have[[int]$row[$pk]] = $row }
            $r.Close()
        }
        return $have
    }

    # ---- 1. check everything first ---------------------------------------------------------------
    $plan = @()
    foreach ($t in $EchoSolutionTables) {
        $rows = @($doc['tables'][$t.Name])
        if (-not $rows -or $rows.Count -eq 0 -or $null -eq $rows[0]) { continue }
        $shape = Get-Shape $t.Name
        $pk = ($shape | Where-Object { $_.Identity } | Select-Object -First 1).Name
        if (-not $pk) { $pk = $shape[0].Name }
        $have = Get-Ids $t.Name $pk ($rows | ForEach-Object { $_[$pk] })
        if ($t.Lookup) {
            $new = @()
            foreach ($row in $rows) {
                $id = [int]$row[$pk]
                if ($have.ContainsKey($id)) {
                    if ([string]$have[$id][$t.Key] -ne [string]$row[$t.Key]) {
                        throw "$($t.Name) $id is '$($have[$id][$t.Key])' here but '$($row[$t.Key])' in the export. Nothing was imported."
                    }
                } else {
                    $cmd = $c.CreateCommand()
                    $cmd.CommandText = "SELECT $pk FROM dbo.$($t.Name) WHERE $($t.Key) = @k"
                    [void]$cmd.Parameters.AddWithValue('@k', [string]$row[$t.Key])
                    $other = $cmd.ExecuteScalar()
                    if ($other) { throw "$($t.Name) '$($row[$t.Key])' is id $other here but $id in the export. Nothing was imported." }
                    $new += , $row
                }
            }
            $rows = $new
        } elseif ($have.Count -gt 0) {
            throw "This Echo already has $($t.Name) $(@($have.Keys)[0]) (and perhaps more) -- the ids of this solution are taken. Nothing was imported."
        }
        if ($t.ParentsFirst) {
            $byId = @{}; $rows | ForEach-Object { $byId[[int]$_[$pk]] = $_ }
            $done = @{}; $sorted = New-Object System.Collections.ArrayList
            function Visit($row) {
                $id = [int]$row[$pk]
                if ($done.ContainsKey($id)) { return }
                $parent = $row[$t.ParentsFirst]
                if ($parent -and $byId.ContainsKey([int]$parent)) { Visit $byId[[int]$parent] }
                $done[$id] = 1; [void]$sorted.Add($row)
            }
            foreach ($row in $rows) { Visit $row }
            $rows = $sorted
        }
        if ($rows.Count -gt 0) { $plan += [pscustomobject]@{ Table = $t.Name; Rows = $rows; Shape = $shape; Pk = $pk } }
    }

    # ---- 2. write it all, in one transaction ------------------------------------------------------
    $tran = $c.BeginTransaction()
    $opts = [System.Data.SqlClient.SqlBulkCopyOptions]::KeepIdentity -bor [System.Data.SqlClient.SqlBulkCopyOptions]::CheckConstraints -bor [System.Data.SqlClient.SqlBulkCopyOptions]::FireTriggers -bor [System.Data.SqlClient.SqlBulkCopyOptions]::KeepNulls
    $count = 0
    foreach ($p in $plan) {
        $dt = New-Object System.Data.DataTable
        $cmd = $c.CreateCommand(); $cmd.Transaction = $tran; $cmd.CommandText = "SELECT * FROM dbo.$($p.Table) WHERE 1 = 0"
        $da = New-Object System.Data.SqlClient.SqlDataAdapter $cmd
        [void]$da.FillSchema($dt, [System.Data.SchemaType]::Source)
        foreach ($col in $dt.Columns) { $col.ReadOnly = $false; $col.AutoIncrement = $false; $col.AllowDBNull = $true }
        foreach ($row in $p.Rows) {
            $dr = $dt.NewRow()
            foreach ($col in $dt.Columns) {
                if (-not $row.ContainsKey($col.ColumnName)) { continue }
                $v = $row[$col.ColumnName]
                if ($null -eq $v) { $dr[$col.ColumnName] = [DBNull]::Value; continue }
                if ($col.DataType -eq [DateTime]) { $dr[$col.ColumnName] = [DateTime]::Parse([string]$v, [Globalization.CultureInfo]::InvariantCulture) }
                elseif ($col.DataType -eq [byte[]]) { $dr[$col.ColumnName] = [Convert]::FromBase64String([string]$v) }
                else { $dr[$col.ColumnName] = $v }
            }
            # What Echo does not have: the record a solution hangs on, Leonardo's file catalog, linked records.
            if ($p.Table -eq 'Solution') { $dr['EntityID'] = [DBNull]::Value }
            if ($p.Table -eq 'Conversation') { foreach ($n in 'LinkedEntityID', 'LinkedTaskID', 'LinkedChecklistID', 'LinkedStoryID') { if ($dt.Columns.Contains($n)) { $dr[$n] = [DBNull]::Value } } }
            if ($p.Table -eq 'SolutionFile') {
                $fid = [string]$row['SolutionFileID']
                $dr['LeonardoFileID'] = [DBNull]::Value
                $dr['StorageCode'] = 'local'
                $b64 = $doc['files'][$fid]
                if ($b64) {
                    $safe = ([string]$row['OriginalFileName']) -replace '[^A-Za-z0-9._ ()-]', '_'
                    $safe = $safe.TrimStart('.'); if (-not $safe) { $safe = 'file' }
                    $rel = "$sid/" + [Guid]::NewGuid().ToString().Substring(0, 8) + '-' + $safe
                    $full = Join-Path $FilesRoot $rel
                    New-Item -ItemType Directory -Force (Split-Path -Parent $full) | Out-Null
                    [IO.File]::WriteAllBytes($full, [Convert]::FromBase64String($b64))
                    $written += $full
                    $dr['StoredFileName'] = $rel
                } else {
                    $dr['StoredFileName'] = [DBNull]::Value
                }
            }
            $dt.Rows.Add($dr)
        }
        $bulk = New-Object System.Data.SqlClient.SqlBulkCopy($c, $opts, $tran)
        $bulk.DestinationTableName = "dbo.$($p.Table)"
        $bulk.BulkCopyTimeout = 600
        foreach ($col in $dt.Columns) { [void]$bulk.ColumnMappings.Add($col.ColumnName, $col.ColumnName) }
        $bulk.WriteToServer($dt)
        if ($p.Shape | Where-Object { $_.Identity }) {
            $cmd = $c.CreateCommand(); $cmd.Transaction = $tran
            $cmd.CommandText = "DBCC CHECKIDENT ('dbo.$($p.Table)', RESEED) WITH NO_INFOMSGS"
            [void]$cmd.ExecuteNonQuery()
        }
        $count += $dt.Rows.Count
        Write-Host ("  {0,-34} {1,6} rows" -f $p.Table, $dt.Rows.Count)
    }
    $tran.Commit()
    $tran = $null
    Write-Host "Imported '$($doc['solutionName'])': $count rows, $($written.Count) files. Open it from Solutions." -ForegroundColor Green
} catch {
    if ($tran) { try { $tran.Rollback() } catch { } }
    # The rows did not land, so the files written for them are moved aside, never erased.
    if ($written) {
        $trash = Join-Path $FilesRoot 'trash'
        New-Item -ItemType Directory -Force $trash | Out-Null
        foreach ($w in $written) { if (Test-Path -LiteralPath $w) { Move-Item -LiteralPath $w -Destination (Join-Path $trash ([IO.Path]::GetFileName($w))) } }
    }
    throw
} finally {
    $c.Close()
}
