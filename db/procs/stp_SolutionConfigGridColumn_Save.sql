SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridColumn_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridColumn_Save
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridColumn_Save
    @prmSolutionConfigGridColumnID INT,
    @prmSolutionID                 INT,
    @prmColumnTitle                NVARCHAR(100),
    @prmColorHex                   VARCHAR(7),
    @prmIsDefaultColumn            BIT,
    @prmSortOrder                  INT = NULL
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridColumn_Save 0, 1, N'QA - Tomcat 11', '#2563EB', 0, NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: add or change a column -- its name (unique per solution), Color
                         (#RRGGBB or NULL) and whether it is the one "applies to all" column whose value stands in for any blank
                         cell. Only one such column per solution: a second one is refused. Returns SolutionConfigGridColumnID.

*/

SET NOCOUNT ON

SET @prmColumnTitle = LTRIM(RTRIM(ISNULL(@prmColumnTitle, N'')))
IF @prmColumnTitle = N'' BEGIN RAISERROR('A column needs a name. Nothing was saved.', 16, 1) RETURN END
SET @prmColorHex = NULLIF(LTRIM(RTRIM(ISNULL(@prmColorHex, ''))), '')
IF @prmColorHex IS NOT NULL AND (LEN(@prmColorHex) <> 7 OR @prmColorHex NOT LIKE '#[0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]') BEGIN
    RAISERROR('A Color is written #RRGGBB, for example #2563EB. Nothing was saved.', 16, 1) RETURN
END
SET @prmColorHex = UPPER(@prmColorHex)
SET @prmIsDefaultColumn = ISNULL(@prmIsDefaultColumn, 0)
IF @prmSolutionConfigGridColumnID > 0 BEGIN
    SET @prmSolutionID = (SELECT SolutionID FROM dbo.SolutionConfigGridColumn WHERE SolutionConfigGridColumnID = @prmSolutionConfigGridColumnID)
    IF @prmSolutionID IS NULL BEGIN RAISERROR('That column no longer exists. Nothing was saved.', 16, 1) RETURN END
END
IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That solution no longer exists. Nothing was saved.', 16, 1) RETURN
END
IF EXISTS (SELECT 1 FROM dbo.SolutionConfigGridColumn WHERE SolutionID = @prmSolutionID AND ColumnTitle = @prmColumnTitle
           AND SolutionConfigGridColumnID <> ISNULL(@prmSolutionConfigGridColumnID, 0)) BEGIN
    DECLARE @dup NVARCHAR(300) = N'This solution already has a column named "' + @prmColumnTitle + N'". Nothing was saved.'
    RAISERROR(@dup, 16, 1) RETURN
END
IF @prmIsDefaultColumn = 1 BEGIN
    DECLARE @other NVARCHAR(100) = (SELECT TOP 1 ColumnTitle FROM dbo.SolutionConfigGridColumn
                                    WHERE SolutionID = @prmSolutionID AND IsDefaultColumn = 1
                                    AND SolutionConfigGridColumnID <> ISNULL(@prmSolutionConfigGridColumnID, 0))
    IF @other IS NOT NULL BEGIN
        DECLARE @two NVARCHAR(400) = N'"' + @other + N'" is already the column that applies to all. Only one column can be. Nothing was saved.'
        RAISERROR(@two, 16, 1) RETURN
    END
END

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF @prmSolutionConfigGridColumnID > 0
        UPDATE dbo.SolutionConfigGridColumn
        SET    ColumnTitle = @prmColumnTitle, ColorHex = @prmColorHex, IsDefaultColumn = @prmIsDefaultColumn,
               SortOrder = ISNULL(@prmSortOrder, SortOrder), ModifiedDate = GETDATE()
        WHERE  SolutionConfigGridColumnID = @prmSolutionConfigGridColumnID
    ELSE BEGIN
        INSERT INTO dbo.SolutionConfigGridColumn (SolutionID, ColumnTitle, ColorHex, IsDefaultColumn, SortOrder)
        VALUES (@prmSolutionID, @prmColumnTitle, @prmColorHex, @prmIsDefaultColumn,
                ISNULL(@prmSortOrder, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionConfigGridColumn WHERE SolutionID = @prmSolutionID), 0) + 1))
        SET @prmSolutionConfigGridColumnID = SCOPE_IDENTITY()
    END
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH
SELECT @prmSolutionConfigGridColumnID AS SolutionConfigGridColumnID

GO
