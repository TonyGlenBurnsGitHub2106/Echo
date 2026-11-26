SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_DailyWorkKind_Save') IS NOT NULL DROP PROCEDURE dbo.stp_DailyWorkKind_Save
GO
CREATE PROCEDURE dbo.stp_DailyWorkKind_Save
    @prmKindName NVARCHAR(100)
AS

/*

Story 6852 (Daily Work): find or create a kind of work by name (the "+ Add" row of the Kind
dropdown). An existing name returns the existing kind and wakes it. Returns DailyWorkKindID, KindName.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_DailyWorkKind_Save @prmKindName = N'Support call'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-11-25  Felix        New (Story 6852).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @name NVARCHAR(100) = LTRIM(RTRIM(ISNULL(@prmKindName, N'')))
IF @name = N''
BEGIN
    RAISERROR('A kind of work needs a name. Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @id INT
SELECT @id = DailyWorkKindID FROM dbo.DailyWorkKind WHERE KindName = @name

BEGIN TRAN
    IF @id IS NULL
    BEGIN
        INSERT INTO dbo.DailyWorkKind (KindName) VALUES (@name)
        SET @id = SCOPE_IDENTITY()
    END
    ELSE
        UPDATE dbo.DailyWorkKind SET IsActive = 1, ModifiedDate = GETDATE() WHERE DailyWorkKindID = @id AND IsActive = 0
COMMIT

SELECT DailyWorkKindID, KindName FROM dbo.DailyWorkKind WHERE DailyWorkKindID = @id

GO
