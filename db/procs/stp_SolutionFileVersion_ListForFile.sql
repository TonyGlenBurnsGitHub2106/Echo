SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionFileVersion_ListForFile') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionFileVersion_ListForFile
GO
CREATE PROCEDURE dbo.stp_SolutionFileVersion_ListForFile
    @prmSolutionFileID INT
AS

/*

TEST
----

    EXEC stp_SolutionFileVersion_ListForFile 211

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo), Release 5: the earlier versions of a file, newest first (the current one is the file itself).

*/

SET NOCOUNT ON

SELECT  v.SolutionFileVersionID, v.SolutionFileID, v.VersionNumber, v.OriginalFileName, v.StoredFileName, v.ContentType,
        v.FileSizeBytes, v.ChangeNote, v.UploadedDate, m.MemberCode AS UploadedByCode, m.MemberName AS UploadedByName
FROM    dbo.SolutionFileVersion v
LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = v.UploadedByMemberID
WHERE   v.SolutionFileID = @prmSolutionFileID
ORDER BY v.VersionNumber DESC

GO
