SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionFileFiling_SetImportant') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionFileFiling_SetImportant
GO
CREATE PROCEDURE dbo.stp_SolutionFileFiling_SetImportant
    @prmSolutionID     INT,
    @prmSolutionFileID INT,
    @prmIsImportant    BIT,
    @prmModifiedBy     VARCHAR(50) = NULL
AS

/*

Story 5594: flag or unflag one solution file Important without touching its category or date
(the flag on the card). The file must belong to @prmSolutionID. Returns RowsAffected (0 when
the file is not on that solution).

TEST
----

    BEGIN TRAN; EXEC dbo.stp_SolutionFileFiling_SetImportant @prmSolutionID = 143, @prmSolutionFileID = 1, @prmIsImportant = 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-05  Felix        New (Story 5594).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

IF NOT EXISTS (SELECT 1 FROM dbo.SolutionFile
               WHERE SolutionFileID = @prmSolutionFileID AND SolutionID = @prmSolutionID)
BEGIN
    SELECT 0 AS RowsAffected
    RETURN
END

BEGIN TRAN

    IF EXISTS (SELECT 1 FROM dbo.SolutionFileFiling WHERE SolutionFileID = @prmSolutionFileID)
        UPDATE dbo.SolutionFileFiling
        SET    IsImportant = ISNULL(@prmIsImportant, 0), ModifiedDate = GETDATE(), ModifiedBy = @prmModifiedBy
        WHERE  SolutionFileID = @prmSolutionFileID
    ELSE
        INSERT INTO dbo.SolutionFileFiling (SolutionFileID, IsImportant, ModifiedBy)
        VALUES (@prmSolutionFileID, ISNULL(@prmIsImportant, 0), @prmModifiedBy)

COMMIT

SELECT 1 AS RowsAffected

GO
