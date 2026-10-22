SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionFile_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionFile_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionFile_ListForSolution
    @prmSolutionID            INT,
    @prmSolutionProjectItemID INT = NULL
AS

/*

TEST
----

    EXEC stp_SolutionFile_ListForSolution 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: the files attached to one solution (or to one of its items), newest first.

*/

SET NOCOUNT ON

SELECT  f.SolutionFileID, f.SolutionID, f.SolutionProjectItemID, i.ItemTitle, f.FileTitle, f.OriginalFileName,
        f.StorageCode, f.LeonardoFileID, f.StoredFileName, f.ContentType, f.FileSizeBytes, f.FileNotes,
        f.UploadedByMemberID, um.MemberCode AS UploadedByCode, um.MemberName AS UploadedByName,
        f.CreatedDate, f.ModifiedDate
FROM    dbo.SolutionFile f
LEFT JOIN dbo.SolutionProjectItem i ON i.SolutionProjectItemID = f.SolutionProjectItemID
LEFT JOIN dbo.SolutionMember um ON um.SolutionMemberID = f.UploadedByMemberID
WHERE   f.SolutionID = @prmSolutionID
AND     (ISNULL(@prmSolutionProjectItemID, 0) <= 0 OR f.SolutionProjectItemID = @prmSolutionProjectItemID)
ORDER BY f.CreatedDate DESC, f.SolutionFileID DESC

GO
