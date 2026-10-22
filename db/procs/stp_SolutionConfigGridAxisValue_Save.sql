SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridAxisValue_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridAxisValue_Save
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridAxisValue_Save
    @prmSolutionConfigGridAxisValueID INT,
    @prmSolutionConfigGridAxisID      INT,
    @prmValueTitle                    NVARCHAR(100),
    @prmColorHex                      VARCHAR(7),
    @prmSortOrder                     INT = NULL
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridAxisValue_Save 0, 1, N'QA', '#2563EB', NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: add or change a value of an axis ("QA") -- its name and Color
                         (#RRGGBB, or NULL for none). Unique per axis. Returns SolutionConfigGridAxisValueID.

*/

SET NOCOUNT ON

SET @prmValueTitle = LTRIM(RTRIM(ISNULL(@prmValueTitle, N'')))
IF @prmValueTitle = N'' BEGIN RAISERROR('An axis value needs a name. Nothing was saved.', 16, 1) RETURN END
SET @prmColorHex = NULLIF(LTRIM(RTRIM(ISNULL(@prmColorHex, ''))), '')
IF @prmColorHex IS NOT NULL AND (LEN(@prmColorHex) <> 7 OR @prmColorHex NOT LIKE '#[0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]') BEGIN
    RAISERROR('A Color is written #RRGGBB, for example #2563EB. Nothing was saved.', 16, 1) RETURN
END
SET @prmColorHex = UPPER(@prmColorHex)
IF @prmSolutionConfigGridAxisValueID > 0 BEGIN
    SET @prmSolutionConfigGridAxisID = (SELECT SolutionConfigGridAxisID FROM dbo.SolutionConfigGridAxisValue WHERE SolutionConfigGridAxisValueID = @prmSolutionConfigGridAxisValueID)
    IF @prmSolutionConfigGridAxisID IS NULL BEGIN RAISERROR('That axis value no longer exists. Nothing was saved.', 16, 1) RETURN END
END
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigGridAxis WHERE SolutionConfigGridAxisID = @prmSolutionConfigGridAxisID) BEGIN
    RAISERROR('That axis no longer exists. Nothing was saved.', 16, 1) RETURN
END
IF EXISTS (SELECT 1 FROM dbo.SolutionConfigGridAxisValue WHERE SolutionConfigGridAxisID = @prmSolutionConfigGridAxisID AND ValueTitle = @prmValueTitle
           AND SolutionConfigGridAxisValueID <> ISNULL(@prmSolutionConfigGridAxisValueID, 0)) BEGIN
    DECLARE @dup NVARCHAR(300) = N'This axis already has a value named "' + @prmValueTitle + N'". Nothing was saved.'
    RAISERROR(@dup, 16, 1) RETURN
END

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF @prmSolutionConfigGridAxisValueID > 0
        UPDATE dbo.SolutionConfigGridAxisValue
        SET    ValueTitle = @prmValueTitle, ColorHex = @prmColorHex, SortOrder = ISNULL(@prmSortOrder, SortOrder), ModifiedDate = GETDATE()
        WHERE  SolutionConfigGridAxisValueID = @prmSolutionConfigGridAxisValueID
    ELSE BEGIN
        INSERT INTO dbo.SolutionConfigGridAxisValue (SolutionConfigGridAxisID, ValueTitle, ColorHex, SortOrder)
        VALUES (@prmSolutionConfigGridAxisID, @prmValueTitle, @prmColorHex,
                ISNULL(@prmSortOrder, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionConfigGridAxisValue WHERE SolutionConfigGridAxisID = @prmSolutionConfigGridAxisID), 0) + 1))
        SET @prmSolutionConfigGridAxisValueID = SCOPE_IDENTITY()
    END
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH
SELECT @prmSolutionConfigGridAxisValueID AS SolutionConfigGridAxisValueID

GO
