SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridColumnTag_Set') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridColumnTag_Set
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridColumnTag_Set
    @prmSolutionConfigGridColumnID INT,
    @prmAxisTitle                  NVARCHAR(100),
    @prmValueTitle                 NVARCHAR(100)
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridColumnTag_Set 1, N'Environment', N'QA'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: set a column's value on one axis, by NAME -- the axis and the value
                         are found or created on the column's own solution (they are per solution, so nothing invented here can
                         reach another solution). A blank value removes the column's tag on that axis. Returns RowsAffected.

*/

SET NOCOUNT ON

SET @prmAxisTitle  = LTRIM(RTRIM(ISNULL(@prmAxisTitle, N'')))
SET @prmValueTitle = LTRIM(RTRIM(ISNULL(@prmValueTitle, N'')))
IF @prmAxisTitle = N'' BEGIN RAISERROR('Name the axis, for example Environment. Nothing was saved.', 16, 1) RETURN END
DECLARE @solutionId INT = (SELECT SolutionID FROM dbo.SolutionConfigGridColumn WHERE SolutionConfigGridColumnID = @prmSolutionConfigGridColumnID)
IF @solutionId IS NULL BEGIN RAISERROR('That column no longer exists. Nothing was saved.', 16, 1) RETURN END

DECLARE @axisId INT = (SELECT SolutionConfigGridAxisID FROM dbo.SolutionConfigGridAxis WHERE SolutionID = @solutionId AND AxisTitle = @prmAxisTitle)
IF @axisId IS NULL AND @prmValueTitle = N'' BEGIN SELECT 0 AS RowsAffected RETURN END

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF @axisId IS NULL BEGIN
        INSERT INTO dbo.SolutionConfigGridAxis (SolutionID, AxisTitle, SortOrder)
        VALUES (@solutionId, @prmAxisTitle, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionConfigGridAxis WHERE SolutionID = @solutionId), 0) + 1)
        SET @axisId = SCOPE_IDENTITY()
    END
    IF @prmValueTitle = N''
        DELETE FROM dbo.SolutionConfigGridColumnTag
        WHERE  SolutionConfigGridColumnID = @prmSolutionConfigGridColumnID AND SolutionConfigGridAxisID = @axisId
    ELSE BEGIN
        DECLARE @valueId INT = (SELECT SolutionConfigGridAxisValueID FROM dbo.SolutionConfigGridAxisValue
                                WHERE SolutionConfigGridAxisID = @axisId AND ValueTitle = @prmValueTitle)
        IF @valueId IS NULL BEGIN
            INSERT INTO dbo.SolutionConfigGridAxisValue (SolutionConfigGridAxisID, ValueTitle, SortOrder)
            VALUES (@axisId, @prmValueTitle, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionConfigGridAxisValue WHERE SolutionConfigGridAxisID = @axisId), 0) + 1)
            SET @valueId = SCOPE_IDENTITY()
        END
        IF EXISTS (SELECT 1 FROM dbo.SolutionConfigGridColumnTag WITH (UPDLOCK, HOLDLOCK)
                   WHERE SolutionConfigGridColumnID = @prmSolutionConfigGridColumnID AND SolutionConfigGridAxisID = @axisId)
            UPDATE dbo.SolutionConfigGridColumnTag SET SolutionConfigGridAxisValueID = @valueId
            WHERE  SolutionConfigGridColumnID = @prmSolutionConfigGridColumnID AND SolutionConfigGridAxisID = @axisId
            AND    SolutionConfigGridAxisValueID <> @valueId
        ELSE
            INSERT INTO dbo.SolutionConfigGridColumnTag (SolutionConfigGridColumnID, SolutionConfigGridAxisID, SolutionConfigGridAxisValueID)
            VALUES (@prmSolutionConfigGridColumnID, @axisId, @valueId)
    END
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH
SELECT 1 AS RowsAffected

GO
