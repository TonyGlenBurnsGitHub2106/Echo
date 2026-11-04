SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionFile_NewVersion') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionFile_NewVersion
GO
CREATE PROCEDURE dbo.stp_SolutionFile_NewVersion
    @prmSolutionFileID    INT,
    @prmOriginalFileName  NVARCHAR(300),
    @prmStoredFileName    NVARCHAR(400),
    @prmContentType       NVARCHAR(200),
    @prmFileSizeBytes     BIGINT,
    @prmChangeNote        NVARCHAR(500),
    @prmMemberCode        VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionFile_NewVersion 211, N'charset-v2.xlsx', N'6/20261104-charset-v2.xlsx', N'application/vnd.ms-excel', 18233, N'QA numbers added', 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo), Release 5: a new version of a file. The file keeps its row, so its links, title, item and
                         filing stay. What it had (name, stored name, type, size, who uploaded it and when) goes
                         into a SolutionFileVersion row with the number it was, and the file takes the new bytes and
                         the next number. The bytes themselves are written by the service first, beside the old ones,
                         which are never removed. Returns CurrentVersionNumber.
11/04/2026  Felix        A version's UploadedDate is when its bytes arrived (the previous new version, else the
                         file's creation), not the last edit of the title. The new bytes are local storage.

*/

SET NOCOUNT ON

DECLARE @n INT, @solutionId INT
SELECT @n = CurrentVersionNumber, @solutionId = SolutionID FROM dbo.SolutionFile WHERE SolutionFileID = @prmSolutionFileID
IF @solutionId IS NULL BEGIN RAISERROR('That file no longer exists. Nothing was saved.', 16, 1) RETURN END
IF LTRIM(RTRIM(ISNULL(@prmStoredFileName, N''))) = N'' BEGIN RAISERROR('The new version has no stored file. Nothing was saved.', 16, 1) RETURN END
DECLARE @memberId INT = NULL
EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    SELECT @n = CurrentVersionNumber FROM dbo.SolutionFile WITH (UPDLOCK, HOLDLOCK) WHERE SolutionFileID = @prmSolutionFileID
    INSERT INTO dbo.SolutionFileVersion (SolutionFileID, VersionNumber, OriginalFileName, StoredFileName, ContentType, FileSizeBytes,
                                         ChangeNote, UploadedByMemberID, UploadedDate)
    SELECT SolutionFileID, @n, OriginalFileName, StoredFileName, ContentType, FileSizeBytes,
           NULLIF(LTRIM(RTRIM(ISNULL(@prmChangeNote, N''))), N''), UploadedByMemberID,
           ISNULL((SELECT MAX(v.CreatedDate) FROM dbo.SolutionFileVersion v WHERE v.SolutionFileID = @prmSolutionFileID), CreatedDate)
    FROM   dbo.SolutionFile WHERE SolutionFileID = @prmSolutionFileID
    UPDATE dbo.SolutionFile
    SET    OriginalFileName = @prmOriginalFileName, StoredFileName = @prmStoredFileName, ContentType = @prmContentType,
           FileSizeBytes = @prmFileSizeBytes, UploadedByMemberID = @memberId, CurrentVersionNumber = @n + 1, ModifiedDate = GETDATE(),
           StorageCode = 'local', LeonardoFileID = NULL
    WHERE  SolutionFileID = @prmSolutionFileID
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @n + 1 AS CurrentVersionNumber

GO
