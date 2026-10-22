SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridAxis_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridAxis_Save
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridAxis_Save
    @prmSolutionConfigGridAxisID INT,
    @prmSolutionID               INT,
    @prmAxisTitle                NVARCHAR(100),
    @prmSortOrder                INT = NULL
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridAxis_Save 0, 1, N'Environment', NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: add or rename an axis ("Environment", "Server") of a solution.
                         The name is unique per solution. Returns SolutionConfigGridAxisID.

*/

SET NOCOUNT ON

SET @prmAxisTitle = LTRIM(RTRIM(ISNULL(@prmAxisTitle, N'')))
IF @prmAxisTitle = N'' BEGIN RAISERROR('An axis needs a name. Nothing was saved.', 16, 1) RETURN END
IF @prmSolutionConfigGridAxisID > 0 BEGIN
    SET @prmSolutionID = (SELECT SolutionID FROM dbo.SolutionConfigGridAxis WHERE SolutionConfigGridAxisID = @prmSolutionConfigGridAxisID)
    IF @prmSolutionID IS NULL BEGIN RAISERROR('That axis no longer exists. Nothing was saved.', 16, 1) RETURN END
END
IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That solution no longer exists. Nothing was saved.', 16, 1) RETURN
END
IF EXISTS (SELECT 1 FROM dbo.SolutionConfigGridAxis WHERE SolutionID = @prmSolutionID AND AxisTitle = @prmAxisTitle
           AND SolutionConfigGridAxisID <> ISNULL(@prmSolutionConfigGridAxisID, 0)) BEGIN
    DECLARE @dup NVARCHAR(300) = N'This solution already has an axis named "' + @prmAxisTitle + N'". Nothing was saved.'
    RAISERROR(@dup, 16, 1) RETURN
END

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF @prmSolutionConfigGridAxisID > 0
        UPDATE dbo.SolutionConfigGridAxis
        SET    AxisTitle = @prmAxisTitle, SortOrder = ISNULL(@prmSortOrder, SortOrder), ModifiedDate = GETDATE()
        WHERE  SolutionConfigGridAxisID = @prmSolutionConfigGridAxisID
    ELSE BEGIN
        INSERT INTO dbo.SolutionConfigGridAxis (SolutionID, AxisTitle, SortOrder)
        VALUES (@prmSolutionID, @prmAxisTitle,
                ISNULL(@prmSortOrder, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionConfigGridAxis WHERE SolutionID = @prmSolutionID), 0) + 1))
        SET @prmSolutionConfigGridAxisID = SCOPE_IDENTITY()
    END
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH
SELECT @prmSolutionConfigGridAxisID AS SolutionConfigGridAxisID

GO
