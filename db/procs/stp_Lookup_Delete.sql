SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Lookup_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_Lookup_Delete
GO
CREATE PROCEDURE dbo.stp_Lookup_Delete
    @prmLookupKey  VARCHAR(60),
    @prmItemKey    NVARCHAR(400),
    @prmMoveToKey  NVARCHAR(400) = NULL   -- optional: the item everything moves to (a merge)
AS

/*

Categories and Types (Story 3520 follow-up): delete one item of a registered lookup, taking
care of everything that uses it, in one transaction. What happens to each registered
reference:

                 no move-to                          with a move-to (merge)
  xref           the tag row is removed              moved to the other item (a thing that
                                                     already has both keeps one row)
  nullable       cleared (the thing keeps itself,    set to the other item
                 without this category / author)
  required       refused -- the page must ask where  set to the other item
                 these things should go

A text lookup (a book subject, a leave type) has one reference, its own column: the value
is cleared, or replaced by the move-to value.

Refused: a locked (reserved) name, a protected status code, a move-to that is the item
itself or does not exist. Answers Affected, the number of things changed.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_Lookup_Delete 'quote-category', N'1'; ROLLBACK
    BEGIN TRAN; EXEC dbo.stp_Lookup_Delete 'quote-topic', N'5', N'1'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-24  Felix        New (Story 3520 follow-up).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @table SYSNAME, @id SYSNAME, @name SYSNAME, @code SYSNAME, @protected VARCHAR(1000), @reserved NVARCHAR(500)
SELECT  @table = TableName, @id = IdColumn, @name = NameColumn, @code = CodeColumn,
        @protected = ProtectedCodes, @reserved = ReservedNames
FROM    dbo.LookupRegistry WHERE LookupKey = @prmLookupKey

IF @table IS NULL
BEGIN
    RAISERROR('That list is not registered.', 16, 1)
    RETURN
END

DECLARE @sql NVARCHAR(MAX)
DECLARE @affected INT = 0, @n INT
DECLARE @rt SYSNAME, @rc SYSNAME, @kind VARCHAR(10), @owner SYSNAME

-- ======================= text lookup ==========================================
IF @id IS NULL
BEGIN
    DECLARE @value NVARCHAR(400) = LTRIM(RTRIM(ISNULL(@prmItemKey, N'')))
    DECLARE @moveValue NVARCHAR(400) = NULLIF(LTRIM(RTRIM(ISNULL(@prmMoveToKey, N''))), N'')
    IF @value = N''
    BEGIN
        RAISERROR('Nothing to delete.', 16, 1)
        RETURN
    END
    IF @moveValue IS NOT NULL AND @moveValue = @value
    BEGIN
        RAISERROR('Pick a different one to move to.', 16, 1)
        RETURN
    END
    SELECT TOP 1 @kind = RefKind FROM dbo.LookupReference WHERE LookupKey = @prmLookupKey
    IF ISNULL(@kind, 'required') = 'required' AND @moveValue IS NULL
    BEGIN
        RAISERROR('Every one of these needs a value -- pick what they should be changed to.', 16, 1)
        RETURN
    END
    SET @sql = N'UPDATE dbo.' + QUOTENAME(@table) + N' SET ' + QUOTENAME(@name) + N' = @moveValue WHERE LTRIM(RTRIM(' + QUOTENAME(@name) + N')) = @value; SET @n = @@ROWCOUNT'
    EXEC sp_executesql @sql, N'@moveValue NVARCHAR(400), @value NVARCHAR(400), @n INT OUTPUT',
         @moveValue = @moveValue, @value = @value, @n = @n OUTPUT
    SELECT ISNULL(@n, 0) AS Affected
    RETURN
END

-- ======================= table lookup ==========================================
IF ISNULL(@prmItemKey, N'') = N'' OR @prmItemKey LIKE N'%[^0-9]%'
BEGIN
    RAISERROR('That item was not found.', 16, 1)
    RETURN
END
DECLARE @intID INT = CAST(@prmItemKey AS INT)
DECLARE @moveID INT = NULL
IF NULLIF(LTRIM(RTRIM(ISNULL(@prmMoveToKey, N''))), N'') IS NOT NULL
BEGIN
    IF @prmMoveToKey LIKE N'%[^0-9]%'
    BEGIN
        RAISERROR('The item to move to was not found.', 16, 1)
        RETURN
    END
    SET @moveID = CAST(@prmMoveToKey AS INT)
END

DECLARE @oldName NVARCHAR(400), @oldCode VARCHAR(100), @moveExists INT
SET @sql = N'SELECT @oldName = CAST(' + QUOTENAME(@name) + N' AS NVARCHAR(400))'
         + CASE WHEN @code IS NULL THEN N'' ELSE N', @oldCode = CAST(' + QUOTENAME(@code) + N' AS VARCHAR(100))' END
         + N' FROM dbo.' + QUOTENAME(@table) + N' WHERE ' + QUOTENAME(@id) + N' = @intID; '
         + N'SELECT @moveExists = COUNT(*) FROM dbo.' + QUOTENAME(@table) + N' WHERE ' + QUOTENAME(@id) + N' = @moveID'
EXEC sp_executesql @sql, N'@intID INT, @moveID INT, @oldName NVARCHAR(400) OUTPUT, @oldCode VARCHAR(100) OUTPUT, @moveExists INT OUTPUT',
     @intID = @intID, @moveID = @moveID, @oldName = @oldName OUTPUT, @oldCode = @oldCode OUTPUT, @moveExists = @moveExists OUTPUT

IF @oldName IS NULL
BEGIN
    RAISERROR('That item no longer exists.', 16, 1)
    RETURN
END
IF @reserved IS NOT NULL AND N',' + @reserved + N',' LIKE N'%,' + @oldName + N',%'
BEGIN
    DECLARE @msgLocked NVARCHAR(400) = N'"' + @oldName + N'" is built in -- Leonardo relies on it, so it cannot be deleted.'
    RAISERROR(@msgLocked, 16, 1)
    RETURN
END
IF @oldCode IS NOT NULL AND @protected IS NOT NULL AND ',' + @protected + ',' LIKE '%,' + @oldCode + ',%'
BEGIN
    DECLARE @msgProt NVARCHAR(400) = N'"' + @oldName + N'" is one of the statuses Leonardo works with, so it can be renamed but not deleted.'
    RAISERROR(@msgProt, 16, 1)
    RETURN
END
IF @moveID IS NOT NULL AND (@moveID = @intID OR ISNULL(@moveExists, 0) = 0)
BEGIN
    RAISERROR('Pick a different one to move to.', 16, 1)
    RETURN
END

-- A required reference in use needs somewhere to go.
IF @moveID IS NULL
BEGIN
    DECLARE @requiredInUse INT = 0
    DECLARE curReq CURSOR LOCAL FAST_FORWARD FOR
        SELECT RefTable, RefColumn FROM dbo.LookupReference WHERE LookupKey = @prmLookupKey AND RefKind = 'required'
    OPEN curReq
    FETCH NEXT FROM curReq INTO @rt, @rc
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @sql = N'SELECT @n = COUNT(*) FROM dbo.' + QUOTENAME(@rt) + N' WHERE ' + QUOTENAME(@rc) + N' = @intID'
        EXEC sp_executesql @sql, N'@intID INT, @n INT OUTPUT', @intID = @intID, @n = @n OUTPUT
        SET @requiredInUse = @requiredInUse + ISNULL(@n, 0)
        FETCH NEXT FROM curReq INTO @rt, @rc
    END
    CLOSE curReq
    DEALLOCATE curReq
    IF @requiredInUse > 0
    BEGIN
        RAISERROR('Some of the things using this must have one -- pick where they should move.', 16, 1)
        RETURN
    END
END

BEGIN TRAN

    DECLARE cur CURSOR LOCAL FAST_FORWARD FOR
        SELECT RefTable, RefColumn, RefKind, XrefOwnerColumn FROM dbo.LookupReference WHERE LookupKey = @prmLookupKey
    OPEN cur
    FETCH NEXT FROM cur INTO @rt, @rc, @kind, @owner
    WHILE @@FETCH_STATUS = 0
    BEGIN
        IF @kind = 'xref'
        BEGIN
            IF @moveID IS NULL
                SET @sql = N'DELETE FROM dbo.' + QUOTENAME(@rt) + N' WHERE ' + QUOTENAME(@rc) + N' = @intID; SET @n = @@ROWCOUNT'
            ELSE
                -- A thing tagged with BOTH keeps one row: drop the duplicate, move the rest.
                SET @sql = N'DELETE a FROM dbo.' + QUOTENAME(@rt) + N' a WHERE a.' + QUOTENAME(@rc) + N' = @intID AND EXISTS (SELECT 1 FROM dbo.'
                         + QUOTENAME(@rt) + N' b WHERE b.' + QUOTENAME(@rc) + N' = @moveID AND b.' + QUOTENAME(@owner) + N' = a.' + QUOTENAME(@owner) + N'); '
                         + N'SET @n = @@ROWCOUNT; '
                         + N'UPDATE dbo.' + QUOTENAME(@rt) + N' SET ' + QUOTENAME(@rc) + N' = @moveID WHERE ' + QUOTENAME(@rc) + N' = @intID; SET @n = @n + @@ROWCOUNT'
        END
        ELSE
            SET @sql = N'UPDATE dbo.' + QUOTENAME(@rt) + N' SET ' + QUOTENAME(@rc) + N' = @moveID WHERE ' + QUOTENAME(@rc) + N' = @intID; SET @n = @@ROWCOUNT'

        SET @n = 0
        EXEC sp_executesql @sql, N'@intID INT, @moveID INT, @n INT OUTPUT', @intID = @intID, @moveID = @moveID, @n = @n OUTPUT
        SET @affected = @affected + ISNULL(@n, 0)
        FETCH NEXT FROM cur INTO @rt, @rc, @kind, @owner
    END
    CLOSE cur
    DEALLOCATE cur

    SET @sql = N'DELETE FROM dbo.' + QUOTENAME(@table) + N' WHERE ' + QUOTENAME(@id) + N' = @intID'
    EXEC sp_executesql @sql, N'@intID INT', @intID = @intID

COMMIT

SELECT @affected AS Affected

GO
