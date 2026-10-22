SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionFileFiling_FileMany') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionFileFiling_FileMany
GO
CREATE PROCEDURE dbo.stp_SolutionFileFiling_FileMany
    @prmSolutionID      INT,
    @prmSolutionFileIDs VARCHAR(MAX),          -- comma-separated SolutionFileIDs
    @prmFileCategoryID  INT         = NULL,    -- NULL or 0 = back to Staging
    @prmModifiedBy      VARCHAR(50) = NULL
AS

/*

Story 5594: file several solution files under one category at once (Select -> File to, and File
all on the Staging band), or send them back to Staging. Only files that belong to @prmSolutionID
are touched; anything else in the list is ignored. Validated before the first write, one
transaction. Twin of stp_EntityFile_FileMany. Returns Filed = how many files were filed.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_SolutionFileFiling_FileMany @prmSolutionID = 143, @prmSolutionFileIDs = '1,2', @prmFileCategoryID = 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-05  Felix        New (Story 5594).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @cat INT = NULLIF(ISNULL(@prmFileCategoryID, 0), 0)

IF @cat IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.FileCategory WHERE FileCategoryID = @cat)
BEGIN
    RAISERROR('That category does not exist. Nothing was saved.', 16, 1)
    RETURN
END

DECLARE @raw TABLE (FileID INT PRIMARY KEY)
INSERT INTO @raw (FileID)
SELECT DISTINCT CAST(LTRIM(RTRIM(items)) AS INT)
FROM   dbo.Split(@prmSolutionFileIDs, ',')
WHERE  LTRIM(RTRIM(items)) NOT LIKE '%[^0-9]%' AND LTRIM(RTRIM(items)) <> ''

DECLARE @ids TABLE (FileID INT PRIMARY KEY)
INSERT INTO @ids (FileID)
SELECT f.SolutionFileID
FROM   @raw r
       INNER JOIN dbo.SolutionFile f ON f.SolutionFileID = r.FileID
WHERE  f.SolutionID = @prmSolutionID

BEGIN TRAN

    UPDATE fl
    SET    FiledDate      = CASE WHEN @cat IS NULL THEN NULL
                                 WHEN ISNULL(fl.FileCategoryID, 0) <> @cat THEN GETDATE()
                                 ELSE fl.FiledDate END,
           FileCategoryID = @cat,
           ModifiedDate   = GETDATE(),
           ModifiedBy     = @prmModifiedBy
    FROM   dbo.SolutionFileFiling fl
           INNER JOIN @ids i ON i.FileID = fl.SolutionFileID

    INSERT INTO dbo.SolutionFileFiling (SolutionFileID, FileCategoryID, FiledDate, ModifiedBy)
    SELECT i.FileID, @cat, CASE WHEN @cat IS NULL THEN NULL ELSE GETDATE() END, @prmModifiedBy
    FROM   @ids i
    WHERE  NOT EXISTS (SELECT 1 FROM dbo.SolutionFileFiling fl WHERE fl.SolutionFileID = i.FileID)

COMMIT

SELECT COUNT(*) AS Filed FROM @ids

GO
