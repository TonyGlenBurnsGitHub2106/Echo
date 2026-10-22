SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionFile_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionFile_Save
GO
CREATE PROCEDURE dbo.stp_SolutionFile_Save
    @prmSolutionFileID        INT,
    @prmSolutionID            INT,
    @prmSolutionProjectItemID INT,
    @prmFileTitle             NVARCHAR(300),
    @prmOriginalFileName      NVARCHAR(300),
    @prmStorageCode           VARCHAR(20),
    @prmLeonardoFileID        INT,
    @prmStoredFileName        NVARCHAR(400),
    @prmContentType           NVARCHAR(200),
    @prmFileSizeBytes         BIGINT,
    @prmFileNotes             NVARCHAR(1000),
    @prmAuthorCode            VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionFile_Save 0, 1, NULL, 'Title', 'a.pdf', 'nook', NULL, '1/abc-a.pdf', 'application/pdf', 10, NULL, 'felix'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: record (id = 0) one attached file, or edit its title, notes and item. Where the
                         bytes live -- StorageCode 'repo' (a LeonardoFileID) or 'nook' (a StoredFileName inside the
                         solutions nook) -- is fixed when the file is recorded and never changed by an edit. An
                         item must belong to the same solution. Returns the id as SolutionFileID.
10/21/2026  Felix        Story 5653 (Echo): StorageCode 'local' -- a StoredFileName inside Echo's own files folder.

*/

SET NOCOUNT ON

SET @prmFileTitle = LTRIM(RTRIM(ISNULL(@prmFileTitle, '')))
IF ISNULL(@prmSolutionProjectItemID, 0) <= 0 SET @prmSolutionProjectItemID = NULL
IF @prmSolutionFileID > 0 SET @prmSolutionID = (SELECT SolutionID FROM dbo.SolutionFile WHERE SolutionFileID = @prmSolutionFileID)
IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That solution no longer exists.', 16, 1) RETURN
END
IF @prmSolutionProjectItemID IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM dbo.SolutionProjectItem i JOIN dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
        WHERE i.SolutionProjectItemID = @prmSolutionProjectItemID AND p.SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That item does not belong to this solution.', 16, 1) RETURN
END

IF @prmSolutionFileID > 0 BEGIN
    UPDATE dbo.SolutionFile
    SET    FileTitle = CASE WHEN @prmFileTitle = '' THEN FileTitle ELSE @prmFileTitle END,
           FileNotes = @prmFileNotes, SolutionProjectItemID = @prmSolutionProjectItemID, ModifiedDate = GETDATE()
    WHERE  SolutionFileID = @prmSolutionFileID
END ELSE BEGIN
    IF @prmStorageCode NOT IN ('repo', 'nook', 'local') BEGIN RAISERROR('A file is stored in the repo, in the nook, or (Echo) in its local files folder.', 16, 1) RETURN END
    IF @prmFileTitle = '' SET @prmFileTitle = LEFT(ISNULL(@prmOriginalFileName, 'file'), 300)
    DECLARE @authorId INT
    EXEC dbo.stp_SolutionMember_FindOrCreate @prmAuthorCode, @authorId OUTPUT
    INSERT INTO dbo.SolutionFile (SolutionID, SolutionProjectItemID, FileTitle, OriginalFileName, StorageCode, LeonardoFileID,
                                  StoredFileName, ContentType, FileSizeBytes, FileNotes, UploadedByMemberID)
    VALUES (@prmSolutionID, @prmSolutionProjectItemID, @prmFileTitle, ISNULL(@prmOriginalFileName, 'file'), @prmStorageCode,
            NULLIF(@prmLeonardoFileID, 0), @prmStoredFileName, @prmContentType, ISNULL(@prmFileSizeBytes, 0), @prmFileNotes, @authorId)
    SET @prmSolutionFileID = SCOPE_IDENTITY()
END

SELECT @prmSolutionFileID AS SolutionFileID

GO
