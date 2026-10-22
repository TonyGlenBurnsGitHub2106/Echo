SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionFileFiling_Set') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionFileFiling_Set
GO
CREATE PROCEDURE dbo.stp_SolutionFileFiling_Set
    @prmSolutionID     INT,
    @prmSolutionFileID INT,
    @prmFileCategoryID INT         = NULL,   -- NULL or 0 = Staging
    @prmDocumentDate   DATE        = NULL,
    @prmIsImportant    BIT         = 0,
    @prmModifiedBy     VARCHAR(50) = NULL
AS

/*

Story 5594: set everything the Solutions Files tab organizes about one file -- its category
(NULL / 0 sends it to Staging), its document date and Important. The file must belong to
@prmSolutionID, so a stale page can never re-file another solution's file. Validated before the
first write; one row per file (unique index), so a repeated call updates, never doubles.
Returns the filing row, or no row when the file is not on that solution.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_SolutionFileFiling_Set @prmSolutionID = 143, @prmSolutionFileID = 1, @prmFileCategoryID = 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-05  Felix        New (Story 5594).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @cat INT = NULLIF(ISNULL(@prmFileCategoryID, 0), 0)

IF NOT EXISTS (SELECT 1 FROM dbo.SolutionFile
               WHERE SolutionFileID = @prmSolutionFileID AND SolutionID = @prmSolutionID)
BEGIN
    SELECT TOP 0 SolutionFileID, FileCategoryID, DocumentDate, IsImportant, FiledDate
    FROM   dbo.SolutionFileFiling
    RETURN
END

IF @cat IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.FileCategory WHERE FileCategoryID = @cat)
BEGIN
    RAISERROR('That category does not exist. Nothing was saved.', 16, 1)
    RETURN
END

BEGIN TRAN

    IF EXISTS (SELECT 1 FROM dbo.SolutionFileFiling WHERE SolutionFileID = @prmSolutionFileID)
        UPDATE dbo.SolutionFileFiling
        SET    FiledDate      = CASE WHEN @cat IS NULL THEN NULL
                                     WHEN ISNULL(FileCategoryID, 0) <> @cat THEN GETDATE()
                                     ELSE FiledDate END,
               FileCategoryID = @cat,
               DocumentDate   = @prmDocumentDate,
               IsImportant    = ISNULL(@prmIsImportant, 0),
               ModifiedDate   = GETDATE(),
               ModifiedBy     = @prmModifiedBy
        WHERE  SolutionFileID = @prmSolutionFileID
    ELSE
        INSERT INTO dbo.SolutionFileFiling (SolutionFileID, FileCategoryID, DocumentDate, IsImportant, FiledDate, ModifiedBy)
        VALUES (@prmSolutionFileID, @cat, @prmDocumentDate, ISNULL(@prmIsImportant, 0),
                CASE WHEN @cat IS NULL THEN NULL ELSE GETDATE() END, @prmModifiedBy)

COMMIT

SELECT SolutionFileID, FileCategoryID, DocumentDate, IsImportant, FiledDate
FROM   dbo.SolutionFileFiling
WHERE  SolutionFileID = @prmSolutionFileID

GO
